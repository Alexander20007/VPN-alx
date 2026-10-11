package com.alx.vpn.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.alx.vpn.data.Settings
import com.alx.vpn.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Violet = Color(0xFF7C4DFF)
private val Cyan = Color(0xFF00E5FF)
private val Bg = Color(0xFF0B1020)
private val CardBg = Color(0xFF141B33)
private val Green = Color(0xFF00C853)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFE53935)
private val Muted = Color.White.copy(alpha = 0.6f)

private val badgeColors = listOf(
    Color(0xFF7C4DFF), Color(0xFF00B8D4), Color(0xFFFF6D00), Color(0xFF00C853), Color(0xFFD500F9),
    Color(0xFF2979FF), Color(0xFFFF4081), Color(0xFF1DE9B6), Color(0xFFFFAB00), Color(0xFF8D6E63), Color(0xFF90A4AE),
)

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

private enum class NavTab(val label: String, val icon: String) {
    Home("Início", "🏠"), Servers("Servidores", "🌐"), Config("Ajustes", "⚙️"), Logs("Logs", "📜")
}

@Composable
fun AlxApp(
    store: ProfileStore,
    settingsStore: SettingsStore,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val profiles = remember { mutableStateListOf<Profile>().apply { addAll(store.load()) } }
    var selected by remember { mutableStateOf(store.selectedId ?: profiles.firstOrNull()?.id) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(NavTab.Home) }
    var pickingApps by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    val status by VpnState.status.collectAsState()
    val message by VpnState.message.collectAsState()
    val connectedAt by VpnState.connectedAt.collectAsState()

    fun persist() {
        store.save(profiles.toList())
        store.selectedId = selected
    }

    fun updateSettings(s: Settings) {
        settings = s
        settingsStore.save(s)
    }

    BackHandler(enabled = editing != null || pickingApps || tab != NavTab.Home) {
        when {
            editing != null -> editing = null
            pickingApps -> pickingApps = false
            else -> tab = NavTab.Home
        }
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
        pickingApps -> AppPickerScreen(
            selected = settings.apps,
            onChange = { updateSettings(settings.copy(apps = it)) },
            onBack = { pickingApps = false },
        )
        else -> Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = CardBg) {
                    NavTab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Text(t.icon, fontSize = 18.sp) },
                            label = { Text(t.label, fontSize = 11.sp) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    NavTab.Home -> HomeTab(
                        profile = profiles.firstOrNull { it.id == selected },
                        status = status,
                        message = message,
                        connectedAt = connectedAt,
                        onToggle = {
                            when (status) {
                                VpnState.Status.Idle, VpnState.Status.Error -> {
                                    val id = selected
                                    if (id == null) {
                                        Toast.makeText(ctx, "Adicione e selecione um servidor", Toast.LENGTH_SHORT).show()
                                        tab = NavTab.Servers
                                    } else onConnect(id)
                                }
                                else -> onDisconnect()
                            }
                        },
                        onGoServers = { tab = NavTab.Servers },
                    )
                    NavTab.Servers -> ServersTab(
                        profiles = profiles,
                        selectedId = selected,
                        onSelect = { selected = it; persist() },
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
                        onDuplicate = { p ->
                            profiles.add(p.copy(id = java.util.UUID.randomUUID().toString(), name = p.name + " (cópia)"))
                            persist()
                        },
                        onDelete = { p ->
                            profiles.removeAll { it.id == p.id }
                            if (selected == p.id) selected = profiles.firstOrNull()?.id
                            persist()
                        },
                    )
                    NavTab.Config -> SettingsTab(settings, ::updateSettings) { pickingApps = true }
                    NavTab.Logs -> LogsTab()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- componentes

@Composable
private fun ProtoBadge(protocol: String) {
    val c = badgeColors[Protocols.all.indexOf(protocol).coerceAtLeast(0) % badgeColors.size]
    Surface(shape = RoundedCornerShape(6.dp), color = c.copy(alpha = 0.22f)) {
        Text(
            protocol.uppercase(), color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            content()
        }
    }
}

@Composable
fun Dropdown(
    label: String,
    value: String,
    options: List<String>,
    display: (String) -> String = { it.ifBlank { "(nenhum)" } },
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${display(value)}", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- abas

@Composable
private fun HomeTab(
    profile: Profile?,
    status: VpnState.Status,
    message: String,
    connectedAt: Long,
    onToggle: () -> Unit,
    onGoServers: () -> Unit,
) {
    val color = when (status) {
        VpnState.Status.Connected -> Green
        VpnState.Status.Connecting -> Amber
        VpnState.Status.Error -> Red
        VpnState.Status.Idle -> Violet
    }
    val label = when (status) {
        VpnState.Status.Connected -> "DESCONECTAR"
        VpnState.Status.Connecting -> "CANCELAR"
        else -> "CONECTAR"
    }
    val statusText = when (status) {
        VpnState.Status.Connected -> "Conectado"
        VpnState.Status.Connecting -> "Conectando…"
        VpnState.Status.Error -> "Falha na conexão"
        VpnState.Status.Idle -> "Desconectado"
    }
    val active = status == VpnState.Status.Connecting || status == VpnState.Status.Connected
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (status == VpnState.Status.Connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val secs = if (status == VpnState.Status.Connected && connectedAt > 0) ((now - connectedAt) / 1000).coerceAtLeast(0) else 0L
    val clock = "%02d:%02d:%02d".format(secs / 3600, secs % 3600 / 60, secs % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1250), Bg)))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ALX",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Violet, Cyan)),
                    fontSize = 32.sp, fontWeight = FontWeight.Black,
                ),
            )
            Text(" VPN", fontSize = 32.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(215.dp)
                    .graphicsLayer {
                        val s = if (active) scale else 1f
                        scaleX = s; scaleY = s
                        alpha = if (active) 0.22f else 0.10f
                    }
                    .clip(CircleShape)
                    .background(color),
            )
            Box(
                Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.55f))))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
            Text(
                statusText, color = color, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (status == VpnState.Status.Connected) {
            Text(clock, fontSize = 34.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light)
        }
        if (status == VpnState.Status.Error && message.isNotBlank()) {
            Text(message, color = Red, fontSize = 12.sp, maxLines = 4)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onGoServers),
        ) {
            if (profile == null) {
                Text("Nenhum servidor selecionado. Toque para adicionar.", color = Muted, modifier = Modifier.padding(16.dp))
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        ProtoBadge(profile.protocol)
                    }
                    Text("${profile.v("server")}:${profile.v("port")}", color = Muted, fontSize = 13.sp)
                    val net = profile.v("network")
                    val sec = profile.v("security")
                    val extra = listOfNotNull(
                        net.takeIf { it.isNotEmpty() }?.uppercase(),
                        sec.takeIf { it.isNotEmpty() && it != "none" }?.uppercase(),
                        profile.v("sni").takeIf { it.isNotEmpty() }?.let { "SNI $it" },
                        profile.v("host").takeIf { it.isNotEmpty() }?.let { "Host $it" },
                    )
                    if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = Cyan, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ServersTab(
    profiles: List<Profile>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
    onEdit: (Profile) -> Unit,
    onDuplicate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
) {
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Excluir servidor?") },
            text = { Text(p.name) },
            confirmButton = { TextButton(onClick = { onDelete(p); toDelete = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Servidores", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd) { Text("+ Novo") }
            OutlinedButton(onClick = onPaste) { Text("Colar link") }
        }
        if (profiles.isEmpty()) {
            Text(
                "Nenhum servidor ainda. Toque em “+ Novo” ou copie um link (vless://, vmess://, trojan://, ss://, hy2://, tuic://) e toque em “Colar link”.",
                color = Muted, fontSize = 13.sp,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { p ->
                val sel = p.id == selectedId
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (sel) Color(0xFF1E1A4D) else CardBg),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(p.id) },
                ) {
                    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel, onClick = { onSelect(p.id) })
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${p.v("server")}:${p.v("port")}", fontSize = 12.sp, color = Muted, maxLines = 1)
                            }
                            ProtoBadge(p.protocol)
                            Spacer(Modifier.width(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onEdit(p) }) { Text("Editar") }
                            TextButton(onClick = { onDuplicate(p) }) { Text("Duplicar") }
                            TextButton(onClick = { toDelete = p }) { Text("Excluir", color = Red) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(s: Settings, onChange: (Settings) -> Unit, onPickApps: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("As alterações valem na próxima conexão.", color = Muted, fontSize = 12.sp)

        SectionCard("DNS") {
            Dropdown("Servidor DNS", s.dnsPreset, Settings.dnsPresets) { onChange(s.copy(dnsPreset = it)) }
            if (s.dnsPreset == "Personalizado") {
                OutlinedTextField(
                    value = s.dnsCustom, onValueChange = { onChange(s.copy(dnsCustom = it)) },
                    label = { Text("Endereço (https://…, tls://…, 1.1.1.1)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = s.bootstrapDns, onValueChange = { onChange(s.copy(bootstrapDns = it.trim())) },
                label = { Text("DNS de bootstrap (UDP, resolve o servidor)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("Rede") {
            SwitchRow("IPv6", "Encaminhar tráfego IPv6 pelo túnel", s.ipv6) { onChange(s.copy(ipv6 = it)) }
            SwitchRow("Ignorar rede local", "IPs privados (192.168.x.x…) vão direto", s.bypassLan) { onChange(s.copy(bypassLan = it)) }
            Dropdown("MTU", s.mtu.toString(), Settings.mtus.map { it.toString() }) {
                onChange(s.copy(mtu = it.toIntOrNull() ?: 1500))
            }
        }

        SectionCard("Apps (split tunneling)") {
            Dropdown("Modo", s.appMode, Settings.appModes, display = { Settings.appModeLabel(it) }) {
                onChange(s.copy(appMode = it))
            }
            if (s.appMode != "all") {
                OutlinedButton(onClick = onPickApps, modifier = Modifier.fillMaxWidth()) {
                    Text("Escolher apps (${s.apps.size} selecionados)")
                }
            }
        }

        SectionCard("Avançado") {
            Dropdown("Nível de log", s.logLevel, Settings.logLevels) { onChange(s.copy(logLevel = it)) }
        }

        Text("ALX VPN 1.1.0 · núcleo sing-box", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LogsTab() {
    val logs by VpnState.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Logs", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(logs.joinToString("\n"))) }) { Text("Copiar") }
            TextButton(onClick = { VpnState.clear() }) { Text("Limpar") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(logs) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

// ---------------------------------------------------------------- seletor de apps

@Composable
private fun AppPickerScreen(selected: Set<String>, onChange: (Set<String>) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }
                .filter { it.second != ctx.packageName }
                .sortedBy { it.first.lowercase() }
        }
    }
    val shown = apps.filter { query.isBlank() || it.first.contains(query, true) || it.second.contains(query, true) }
    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Voltar") }
            Spacer(Modifier.weight(1f))
            Text("${selected.size} selecionados", color = Muted, fontSize = 12.sp)
            TextButton(onClick = { onChange(emptySet()) }) { Text("Limpar") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("Buscar app") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (apps.isEmpty()) Text("Carregando apps…", color = Muted, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.second }) { (label, pkg) ->
                val on = pkg in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onChange(if (on) selected - pkg else selected + pkg) },
                ) {
                    Checkbox(checked = on, onCheckedChange = { onChange(if (on) selected - pkg else selected + pkg) })
                    Column {
                        Text(label, maxLines = 1)
                        Text(pkg, fontSize = 11.sp, color = Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- editor

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Geral") {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nome") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Dropdown("Protocolo", protocol, Protocols.all, display = { Protocols.label(it) }) {
                    protocol = it
                    fields.clear()
                    fields.putAll(Protocols.defaults(it))
                }
            }
            SectionCard("Conexão") {
                Protocols.fields(protocol, fields).forEach { spec ->
                    key(protocol, spec.key) {
                        val value = fields[spec.key].orEmpty()
                        val opts = spec.options
                        if (opts != null) {
                            Dropdown(spec.label.substringBefore('\n'), value, opts) { fields[spec.key] = it }
                        } else {
                            val hint = spec.label.substringAfter('\n', "")
                            OutlinedTextField(
                                value = value,
                                onValueChange = { fields[spec.key] = it },
                                label = { Text(spec.label.substringBefore('\n')) },
                                placeholder = if (hint.isNotEmpty()) ({ Text(hint) }) else null,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = !spec.multiline,
                                minLines = if (spec.multiline) 3 else 1,
                                keyboardOptions = if (spec.number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                            )
                        }
                    }
                }
            }
            Text(
                "Dica (modo injetor): o servidor pode ser um IP/host e o SNI/Host pode ser outro domínio.",
                fontSize = 12.sp, color = Muted,
            )
        }
    }
}
