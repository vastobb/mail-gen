package com.vastobb.mailgen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

const val SLOT_COUNT = 4

enum class SlotStatus { EMPTY, WORKING, READY, ERROR }

data class Slot(
    val index: Int,
    val status: SlotStatus = SlotStatus.EMPTY,
    val account: Account? = null,
    val messages: List<MessageSummary> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
)

data class UiState(
    val slots: List<Slot> = (0 until SLOT_COUNT).map { Slot(it) },
    val openMessage: MessageDetail? = null,
    val toast: String? = null,
)

/**
 * Owns the four independent slots. Generating into a slot always destroys that
 * slot's previous mailbox first, so the provider's one-mailbox-per-account
 * ceiling never blocks a regeneration.
 */
class MailViewModel(context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("mailgen", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        restore()
    }

    // ---------- persistence ----------
    private fun restore() {
        val raw = prefs.getString("slots", null) ?: return
        try {
            val arr = JSONArray(raw)
            val slots = (0 until SLOT_COUNT).map { i -> Slot(i) }.toMutableList()
            for (i in 0 until minOf(arr.length(), SLOT_COUNT)) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.isNull("account")) continue
                val a = o.getJSONObject("account")
                slots[i] = slots[i].copy(
                    status = SlotStatus.READY,
                    account = Account(
                        id = a.getString("id"),
                        address = a.getString("address"),
                        password = a.getString("password"),
                        token = a.getString("token"),
                    ),
                )
            }
            _state.update { it.copy(slots = slots) }
            slots.forEach { if (it.account != null) refresh(it.index) }
        } catch (_: Exception) {
            // corrupt state is not worth crashing over; start clean
        }
    }

    private fun persist() {
        val arr = JSONArray()
        _state.value.slots.forEach { s ->
            val o = JSONObject()
            s.account?.let {
                o.put("account", JSONObject().apply {
                    put("id", it.id)
                    put("address", it.address)
                    put("password", it.password)
                    put("token", it.token)
                })
            } ?: o.put("account", JSONObject.NULL)
            arr.put(o)
        }
        prefs.edit().putString("slots", arr.toString()).apply()
    }

    // ---------- slot ops ----------
    private fun patch(index: Int, block: (Slot) -> Slot) {
        _state.update { st ->
            st.copy(slots = st.slots.map { if (it.index == index) block(it) else it })
        }
    }

    fun generate(index: Int) {
        val current = _state.value.slots[index]
        if (current.busy) return
        patch(index) { it.copy(busy = true, status = SlotStatus.WORKING, error = null) }
        viewModelScope.launch {
            try {
                // Destroy the previous mailbox in this slot before minting a new one.
                current.account?.let { old ->
                    runCatching { MailApi.deleteAccount(old) }
                }
                val acc = MailApi.createAccount()
                patch(index) {
                    it.copy(
                        busy = false,
                        status = SlotStatus.READY,
                        account = acc,
                        messages = emptyList(),
                        error = null,
                    )
                }
                persist()
                refresh(index)
            } catch (e: Exception) {
                patch(index) {
                    it.copy(
                        busy = false,
                        status = SlotStatus.ERROR,
                        account = null,
                        messages = emptyList(),
                        error = e.message ?: "generation failed",
                    )
                }
                persist()
            }
        }
    }

    fun delete(index: Int) {
        val s = _state.value.slots[index]
        if (s.busy || s.account == null) return
        patch(index) { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { MailApi.deleteAccount(s.account) }
            patch(index) {
                Slot(index, status = SlotStatus.EMPTY, busy = false)
            }
            persist()
            toast("Slot ${index + 1} cleared")
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _state.value.slots.forEach { s ->
                s.account?.let { runCatching { MailApi.deleteAccount(it) } }
            }
            _state.update { st ->
                st.copy(slots = (0 until SLOT_COUNT).map { Slot(it) })
            }
            persist()
            toast("All 4 mailboxes deleted")
        }
    }

    fun refresh(index: Int) {
        val acc = _state.value.slots[index].account ?: return
        viewModelScope.launch {
            runCatching { MailApi.listMessages(acc.token) }
                .onSuccess { msgs -> patch(index) { it.copy(messages = msgs) } }
                .onFailure { e ->
                    // A dead token means the mailbox was reclaimed server-side.
                    if (e.message?.contains("401") == true) {
                        patch(index) { Slot(index, SlotStatus.EMPTY) }
                        persist()
                    }
                }
        }
    }

    fun refreshAll() {
        _state.value.slots.forEach { if (it.account != null) refresh(it.index) }
    }

    fun openMessage(index: Int, msgId: String) {
        val acc = _state.value.slots[index].account ?: return
        viewModelScope.launch {
            runCatching { MailApi.readMessage(acc.token, msgId) }
                .onSuccess { d -> _state.update { it.copy(openMessage = d) } }
        }
    }

    fun closeMessage() = _state.update { it.copy(openMessage = null) }

    fun copy(address: String) {
        toast("Copied $address")
    }

    private fun toast(msg: String) {
        _state.update { it.copy(toast = msg) }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }
}
