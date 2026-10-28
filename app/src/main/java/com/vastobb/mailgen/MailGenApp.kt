package com.vastobb.mailgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailGenApp(vm: MailViewModel) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                title = {
                    Column {
                        Text(
                            "mail-gen",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "4 parallel temp mailboxes · auto-rotate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshAll() }) {
                        Icon(Icons.Default.Refresh, "Refresh all", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { vm.deleteAll() }) {
                        Icon(Icons.Default.Delete, "Delete all", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.slots, key = { it.index }) { slot ->
                    SlotCard(
                        slot = slot,
                        onGenerate = { vm.generate(slot.index) },
                        onDelete = { vm.delete(slot.index) },
                        onRefresh = { vm.refresh(slot.index) },
                        onCopy = { addr ->
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("email", addr))
                            vm.copy(addr)
                        },
                        onOpenMessage = { id -> vm.openMessage(slot.index, id) },
                    )
                }
            }
        }
    }

    state.openMessage?.let { msg ->
        MessageSheet(msg = msg, onDismiss = { vm.closeMessage() })
    }
}

@Composable
private fun SlotCard(
    slot: Slot,
    onGenerate: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenMessage: (String) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SLOT ${slot.index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            StatusDot(slot.status)
        }

        Spacer(Modifier.height(10.dp))

        when {
            slot.busy || slot.status == SlotStatus.WORKING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "working…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            slot.account != null -> {
                Text(
                    slot.account.address,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${slot.messages.size} message${if (slot.messages.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Text(
                    "no mailbox",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (slot.error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                slot.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onGenerate,
                enabled = !slot.busy,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (slot.account == null) "generate" else "regenerate",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (slot.account != null) {
                IconButton(onClick = { onCopy(slot.account.address) }, enabled = !slot.busy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        "Copy address",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !slot.busy) {
                    Icon(
                        Icons.Default.Refresh,
                        "Refresh inbox",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, enabled = !slot.busy) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete mailbox",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (slot.messages.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline)
            slot.messages.forEach { m ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMessage(m.id) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        m.subject,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        m.from,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: SlotStatus) {
    val color = when (status) {
        SlotStatus.READY -> Tokens.Ok
        SlotStatus.WORKING -> Tokens.Warn
        SlotStatus.ERROR -> Tokens.Danger
        SlotStatus.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .size(7.dp)
            .background(color, RoundedCornerShape(50)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSheet(msg: MessageDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(msg.subject, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                msg.from,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                msg.text.ifBlank { "(no plain text body)" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
