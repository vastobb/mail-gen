package com.vastobb.mailgen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * mail.tm client. Ported 1:1 from the verified Python engine (mailgen_core.py):
 * same endpoints, same shapes, same quirks (the API answers either a plain
 * array or a hydra:Collection depending on negotiation, so both are handled).
 */
object MailApi {
    private const val BASE = "https://api.mail.tm"
    private const val UA = "mail-gen/1.0"

    private val json = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class ApiException(message: String) : Exception(message)

    private fun call(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
    ): Pair<Int, String> {
        val rb = body?.toString()?.toRequestBody(json)
        val req = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .method(method, rb)
            .build()
        client.newCall(req).execute().use { r ->
            return r.code to (r.body?.string() ?: "")
        }
    }

    private fun items(raw: String): JSONArray {
        val t = raw.trim()
        if (t.startsWith("[")) return JSONArray(t)
        val o = JSONObject(t)
        return o.optJSONArray("hydra:member") ?: o.optJSONArray("member") ?: JSONArray()
    }

    suspend fun activeDomain(): String = withContext(Dispatchers.IO) {
        val (code, raw) = call("GET", "/domains?page=1")
        if (code != 200) throw ApiException("domains failed: $code")
        val arr = items(raw)
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            if (d.optBoolean("isActive") && !d.optBoolean("isPrivate")) {
                return@withContext d.getString("domain")
            }
        }
        throw ApiException("no active public domain")
    }

    private fun randomLocal(n: Int = 10): String {
        val cs = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..n).map { cs.random() }.joinToString("")
    }

    private fun randomPassword(): String {
        val cs = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..16).map { cs.random() }.joinToString("") + "!aA1"
    }

    /** Creates a mailbox and returns a fully usable account with a live token. */
    suspend fun createAccount(domain: String? = null): Account = withContext(Dispatchers.IO) {
        val dom = domain ?: activeDomain()
        val address = "${randomLocal()}@$dom"
        val password = randomPassword()

        val (code, raw) = call(
            "POST", "/accounts",
            body = JSONObject().put("address", address).put("password", password),
        )
        if (code != 200 && code != 201) throw ApiException("create failed: $code $raw")
        val created = JSONObject(raw)

        val (tcode, traw) = call(
            "POST", "/token",
            body = JSONObject().put("address", address).put("password", password),
        )
        if (tcode != 200) throw ApiException("token failed: $tcode")
        val token = JSONObject(traw).getString("token")

        Account(
            id = created.optString("id"),
            address = address,
            password = password,
            token = token,
        )
    }

    suspend fun listMessages(token: String): List<MessageSummary> = withContext(Dispatchers.IO) {
        val (code, raw) = call("GET", "/messages?page=1", token)
        if (code != 200) throw ApiException("messages failed: $code")
        val arr = items(raw)
        (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MessageSummary(
                id = m.optString("id"),
                subject = m.optString("subject").ifBlank { "(no subject)" },
                from = m.optJSONObject("from")?.optString("address").orEmpty(),
                intro = m.optString("intro").orEmpty(),
                seen = m.optBoolean("seen"),
                createdAt = m.optString("createdAt").orEmpty(),
            )
        }
    }

    suspend fun readMessage(token: String, id: String): MessageDetail = withContext(Dispatchers.IO) {
        val (code, raw) = call("GET", "/messages/$id", token)
        if (code != 200) throw ApiException("read failed: $code")
        val m = JSONObject(raw)
        MessageDetail(
            id = m.optString("id"),
            subject = m.optString("subject").ifBlank { "(no subject)" },
            from = m.optJSONObject("from")?.optString("address").orEmpty(),
            text = m.optString("text").orEmpty(),
            html = m.optString("html").orEmpty(),
            createdAt = m.optString("createdAt").orEmpty(),
        )
    }

    /** Returns true when the mailbox is gone or the token is no longer valid. */
    suspend fun deleteAccount(acc: Account): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = call("DELETE", "/accounts/${acc.id}", acc.token)
        code == 200 || code == 204 || code == 401
    }

    suspend fun alive(acc: Account): Boolean = withContext(Dispatchers.IO) {
        val (code, _) = call("GET", "/me", acc.token)
        code == 200
    }
}

data class Account(
    val id: String,
    val address: String,
    val password: String,
    val token: String,
)

data class MessageSummary(
    val id: String,
    val subject: String,
    val from: String,
    val intro: String,
    val seen: Boolean,
    val createdAt: String,
)

data class MessageDetail(
    val id: String,
    val subject: String,
    val from: String,
    val text: String,
    val html: String,
    val createdAt: String,
)
