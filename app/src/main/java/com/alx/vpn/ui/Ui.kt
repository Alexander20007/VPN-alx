package com.alx.vpn.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alx.vpn.core.VpnState
import com.alx.vpn.data.LinkParser
import com.alx.vpn.data.Profile
import com.alx.vpn.data.ProfileStore
import com.alx.vpn.data.Protocols

private val Violet = Color(0xFF7C4DFF)
private val Cyan = Color(0xFF00E5FF)
private val Bg = Color(0xFF0B1020)
private val CardBg = Color(0xFF141B33)

@Composable
fun AlxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, secondary = Cyan, background = Bg, surface = CardBg,
            onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White,
        ),
        content = content,
    )
}

@Composable
fun AlxApp(store: ProfileStore, onConnect: (String) -> Unit, onDisconnect: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val profiles = remember { mutableStateListOf<Profile>().apply { addAll(store.load()) } }
    var selected by remember { mutableStateOf(store.selectedId ?: profiles.firstOrNull()?.id) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val status by VpnState.status.collectAsState()
    val message by VpnState.message.collectAsState()

    fun persist() {
        store.save(profiles.toList())
        store.selectedId = selected
    }

    BackHandler(enabled = editing != null || showLogs) {
        if (editing != null) editing = null else showLogs = false
    }

    val e = editing
    when {
        e != null -> EditorScreen(
            initial = e,
            isNew = editingIsNew,
            onCancel = { editing = null },
            onSave = { p ->
                val i = profiles.indexOfFirst { it.id == p.id }
                if (i >= 0) profiles[i] = p else profiles.add(p)
                if (selected == null) selected = p.id
                persist()
                editing = null
            },
        )
        showLogs -> LogsScreen(onBack = { showLogs = false })
        else -> HomeScreen(
            profiles = profiles,
            selectedId = selected,
            status = status,
            message = message,
            onSelect = { selected = it; persist() },
            onToggle = {
                when (status) {
                    VpnState.Status.Idle, VpnState.Status.Error -> {
                        val id = selected
                        if (id == null) Toast.makeText(ctx, "Adicione e selecione um servidor", Toast.LENGTH_SHORT).show()
                        else onConnect(id)
                    }
                    else -> onDisconnect()
                }
            },
            onAdd = {
                editing = Profile(name = "", protocol = "vless", f = Protocols.defaults("vless"))
                editingIsNew = true
            },
            onPaste = {
                val text = clipboard.getText()?.text.orEmpty()
                val found = text.lines().mapNotNull { LinkParser.parse(it) }
                if (found.isEmpty()) {
                    Toast.makeText(ctx, "Nenhum link válido na área de transferência", Toast.LENGTH_SHORT).show()
                } else {
                    profiles.addAll(found)
                    if (selected == null) selected = found.first().id
                    persist()
                    Toast.makeText(ctx, "${found.size} servidor(es) importado(s)", Toast.LENGTH_SHORT).show()
                }
            },
            onEdit = { editing = it; editingIsNew = false },
            onDelete = { p ->
                profiles.removeAll { it.id == p.id }
                if (selected == p.id) selected = profiles.firstOrNull()?.id
                persist()
            },
            onLogs = { showLogs = true },
        )
    }
}

@Composable
fun HomeScreen(
    profiles: List<Profile>,
    selectedId: String?,
    status: VpnState.Status,
    message: String,
    onSelect: (String) -> Unit,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onLogs: () -> Unit,
) {
    val color = when (status) {
        VpnState.Status.Connected -> Color(0xFF00C853)
        VpnState.Status.Connecting -> Color(0xFFFFB300)
        VpnState.Status.Error -> Color(0xFFE53935)
        VpnState.Status.Idle -> Violet
    }
    val label = when (status) {
        VpnState.Status.Connected -> "DESCONECTAR"
        VpnState.Status.Connecting -> "CONECTANDO…"
        else -> "CONECTAR"
    }
    val statusText = when (status) {
        VpnState.Status.Connected -> "Conectado"
        VpnState.Status.Connecting -> "Conectando…"
        VpnState.Status.Error -> "Erro: $message"
        VpnState.Status.Idle -> "Desconectado"
    }
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.systemBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "ALX VPN",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Violet, Cyan)),
                        fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    ),
                )
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.55f))))
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(statusText, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAdd) { Text("+ Novo") }
                    OutlinedButton(onClick = onPaste) { Text("Colar link") }
                    TextButton(onClick = onLogs) { Text("Logs") }
                }
            }
            if (profiles.isEmpty()) {
                item {
                    Text(
                        "Nenhum servidor ainda. Toque em “+ Novo” ou copie um link (vless://, vmess://, trojan://, ss://, hy2://, tuic://) e toque em “Colar link”.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    )
                }
            }
            items(profiles, key = { it.id }) { p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        RadioButton(selected = p.id == selectedId, onClick = { onSelect(p.id) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                "${p.protocol.uppercase()} · ${p.v("server")}:${p.v("port")}",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1,
                            )
                        }
                        TextButton(onClick = { onEdit(p) }) { Text("Editar") }
                        TextButton(onClick = { onDelete(p) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
fun Dropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${value.ifBlank { "(nenhum)" }}", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o.ifBlank { "(nenhum)" }) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initial: Profile, isNew: Boolean, onSave: (Profile) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var protocol by remember(initial.id) { mutableStateOf(initial.protocol) }
    val fields = remember(initial.id) { mutableStateMapOf<String, String>().apply { putAll(initial.f) } }

    fun trySave() {
        val server = fields["server"].orEmpty().trim()
        val port = fields["port"].orEmpty().trim().toIntOrNull()
        if (server.isEmpty() || port == null) {
            Toast.makeText(ctx, "Informe servidor e porta válidos", Toast.LENGTH_SHORT).show()
        } else {
            onSave(
                Profile(
                    id = initial.id,
                    name = name.ifBlank { server },
                    protocol = protocol,
                    f = fields.toMap().mapValues { it.value.trim() },
                ),
            )
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Novo servidor" else "Editar servidor") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancelar") } },
                actions = { TextButton(onClick = { trySave() }) { Text("Salvar") } },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Nome") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Dropdown("Protocolo", protocol, Protocols.all) {
                protocol = it
                fields.clear()
                fields.putAll(Protocols.defaults(it))
            }
            Protocols.fields(protocol, fields).forEach { spec ->
                key(protocol, spec.key) {
                    val value = fields[spec.key].orEmpty()
                    val opts = spec.options
                    if (opts != null) {
                        Dropdown(spec.label, value, opts) { fields[spec.key] = it }
                    } else {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { fields[spec.key] = it },
                            label = { Text(spec.label) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = !spec.multiline,
                            minLines = if (spec.multiline) 3 else 1,
                            keyboardOptions = if (spec.number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                        )
                    }
                }
            }
            Text(
                "Dica: o servidor pode ser um IP/host e o SNI/Host pode ser outro domínio (modo injetor).",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val logs by VpnState.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Voltar") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(logs.joinToString("\n"))) }) { Text("Copiar") }
            TextButton(onClick = { VpnState.logs.value = emptyList() }) { Text("Limpar") }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            items(logs) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}
