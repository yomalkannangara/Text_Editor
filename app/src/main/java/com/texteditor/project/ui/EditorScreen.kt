package com.texteditor.project.ui

import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.texteditor.project.data.CompileResponse
import com.texteditor.project.data.HighlightConfig
import com.texteditor.project.network.CompilerClient
import com.texteditor.project.util.highlightWithConfig
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------- Theme ----------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEAEAF0),
    outlineVariant = Color(0xFFE5E7EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B93FF),
    onPrimary = Color.Black,
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF2B2B2B),
    outlineVariant = Color(0xFF3A3A3A),
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // ---------- Dark Mode toggle ----------
    var isDark by rememberSaveable { mutableStateOf(true) }

    MaterialTheme(colorScheme = if (isDark) DarkColors else LightColors) {
        val cs = MaterialTheme.colorScheme

        // ---- Theme-aware default Kotlin syntax ----
        fun defaultKotlinConfigForTheme(dark: Boolean) = HighlightConfig(
            language = "kotlin",
            keywords = listOf(
                "fun","class","data","object","interface","enum","sealed","open","override",
                "private","public","protected","internal","val","var","when","if","else","for",
                "while","do","return","in","is","as","break","continue","try","catch","finally",
                "throw","import","package","null","true","false","this","super","where","typealias","by","companion"
            ),
            comment = "//",
            stringDelimiters = listOf("\"", "'"),
            colors = mapOf(
                "base" to if (dark) "#E5E7EB" else "#111827",
                "keyword" to if (dark) "#8B93FF" else "#1F4B99",
                "comment" to if (dark) "#6A9955" else "#2F7D32",
                "string"  to if (dark) "#D69D85" else "#B55339",
                "number"  to if (dark) "#B5CEA8" else "#2F6F3E"
            )
        )

        // Current syntax config (can be replaced by imported JSON)
        var config by remember(isDark) { mutableStateOf(defaultKotlinConfigForTheme(isDark)) }
        var customSyntaxLoaded by remember { mutableStateOf(false) }

        // Load assets/kotlin.json if present (only once at start)
        LaunchedEffect(Unit) {
            runCatching {
                ctx.assets.open("kotlin.json").bufferedReader().use { r ->
                    Gson().fromJson(r.readText(), HighlightConfig::class.java)
                }
            }.onSuccess { cfg ->
                if (cfg != null) {
                    config = cfg
                    customSyntaxLoaded = true
                }
            }
        }

        // If theme toggles and user didn't load custom JSON, refresh default colors
        LaunchedEffect(isDark) {
            if (!customSyntaxLoaded) config = defaultKotlinConfigForTheme(isDark)
        }

        // ---- Import syntax JSON ----
        val importSyntax = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    input.reader(Charsets.UTF_8).use { reader ->
                        Gson().fromJson(reader, HighlightConfig::class.java)
                    }
                } ?: error("Unable to open the selected file")
            }.onSuccess { cfg ->
                config = cfg
                customSyntaxLoaded = true
                Toast.makeText(ctx, "Syntax loaded", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(ctx, "Failed to load JSON: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // ---------- Editor state (TextFieldValue for selection) ----------
        var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
        var fileName by rememberSaveable { mutableStateOf("Untitled.txt") }
        var editorFocused by remember { mutableStateOf(false) }

        // Undo/Redo — compact
        val undoStack = remember { mutableStateListOf<TextFieldValue>() }
        val redoStack = remember { mutableStateListOf<TextFieldValue>() }
        val maxHistory = 50

        fun pushUndo(snapshot: TextFieldValue) {
            if (undoStack.isEmpty() || undoStack.last().text != snapshot.text || undoStack.last().selection != snapshot.selection) {
                undoStack.add(snapshot)
                if (undoStack.size > maxHistory) undoStack.removeAt(0)
            }
        }
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun doUndo() {
            if (undoStack.isNotEmpty()) {
                redoStack.add(text)
                text = undoStack.removeLast()
            } else {
                Toast.makeText(ctx, "Nothing to undo", Toast.LENGTH_SHORT).show()
            }
        }
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        fun doRedo() {
            if (redoStack.isNotEmpty()) {
                pushUndo(text)
                text = redoStack.removeLast()
            } else {
                Toast.makeText(ctx, "Nothing to redo", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- Find & Replace (inline bar) ----
        var showFindReplaceBar by remember { mutableStateOf(false) }
        var findQuery by remember { mutableStateOf("") }
        var replaceText by remember { mutableStateOf("") }
        var caseSensitive by remember { mutableStateOf(false) }
        var wholeWord by remember { mutableStateOf(false) }

        val matchRanges = remember(text.text, findQuery, caseSensitive, wholeWord) {
            val src = text.text
            if (findQuery.isBlank()) emptyList()
            else {
                val escaped = Regex.escape(findQuery)
                val pattern = if (wholeWord) "\\b$escaped\\b" else escaped
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(pattern, options).findAll(src).map { it.range }.toList()
            }
        }

        // ---- File Open / Save ----
        val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader().use {
                    val loaded = it?.readText().orEmpty()
                    pushUndo(text)
                    text = TextFieldValue(loaded)
                    redoStack.clear()
                    fileName = "Opened.txt"
                    Toast.makeText(ctx, "Opened", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.text.toByteArray())
                }
                fileName = "Saved.txt"
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- Compile ----
        var compileResp by remember { mutableStateOf<CompileResponse?>(null) }
        var compiling by remember { mutableStateOf(false) }
        val compiler = remember { CompilerClient() }

        // ---- Drawer ----
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        // Hide keyboard + caret when drawer opens
        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.currentValue }.collectLatest { v ->
                if (v == DrawerValue.Open) {
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }
            }
        }

        // Shared scroll states
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()

        // Editor text style (monospace)
        val editorTextStyle = remember {
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(270.dp),
                    drawerContainerColor = cs.surfaceVariant
                ) {
                    // Header row with close + dark mode toggle
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close sidebar")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DarkMode, contentDescription = null, tint = cs.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Text("Dark Mode", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = isDark, onCheckedChange = { isDark = it })
                        }
                    }

                    Divider()

                    NavigationDrawerItem(
                        label = { Text("New") },
                        selected = false,
                        onClick = {
                            pushUndo(text)
                            text = TextFieldValue("")
                            redoStack.clear()
                            fileName = "Untitled.txt"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.FiberNew, contentDescription = null) }
                    )
                    NavigationDrawerItem(
                        label = { Text(if (compiling) "Compiling…" else "Compile") },
                        selected = false,
                        onClick = {
                            if (!compiling) {
                                compiling = true
                                compileResp = null
                                scope.launch {
                                    try {
                                        compileResp = compiler.compile(text.text)
                                    } catch (e: Exception) {
                                        compileResp = CompileResponse(false, "client", "", e.message ?: "error", -1)
                                    } finally {
                                        compiling = false
                                        drawerState.close()
                                    }
                                }
                            } else {
                                Toast.makeText(ctx, "Already compiling…", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) }
                    )
                    NavigationDrawerItem(
                        label = { Text("Open") },
                        selected = false,
                        onClick = {
                            openFile.launch(arrayOf("text/plain"))
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) }
                    )
                    NavigationDrawerItem(
                        label = { Text("Save As") },
                        selected = false,
                        onClick = {
                            saveFile.launch(fileName)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Save, contentDescription = null) }
                    )

                    Divider(Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        label = { Text("Import syntax JSON") },
                        selected = false,
                        onClick = {
                            importSyntax.launch(arrayOf("application/json"))
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.UploadFile, contentDescription = null) }
                    )
                    NavigationDrawerItem(
                        label = { Text("Reset to Kotlin default") },
                        selected = false,
                        onClick = {
                            customSyntaxLoaded = false
                            config = defaultKotlinConfigForTheme(isDark)
                            Toast.makeText(ctx, "Reset to Kotlin syntax", Toast.LENGTH_SHORT).show()
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Restore, contentDescription = null) }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = cs.surface,
                            titleContentColor = cs.onSurface
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    keyboard?.hide()
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                            ) { Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu") }
                        },
                        actions = {
                            IconButton(onClick = { showFindReplaceBar = !showFindReplaceBar }) {
                                Icon(Icons.Filled.Search, contentDescription = "Find & Replace")
                            }
                            IconButton(onClick = { doUndo() }) {
                                Icon(Icons.Filled.Undo, contentDescription = "Undo")
                            }
                            IconButton(onClick = { doRedo() }) {
                                Icon(Icons.Filled.Redo, contentDescription = "Redo")
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 1.dp, color = cs.surface) {
                        val chars = text.text.length
                        val words = if (text.text.isBlank()) 0 else text.text.trim().split(Regex("\\s+")).size
                        val hasSelection = text.selection.start != text.selection.end
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 6.dp)
                        ) {
                            // Cut / Copy / Paste row — spaced evenly
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // CUT (selection only)
                                AssistChip(
                                    onClick = {
                                        if (!hasSelection) return@AssistChip
                                        val sel = text.selection
                                        val slice = text.text.substring(sel.start, sel.end)
                                        if (slice.isNotEmpty()) {
                                            clipboard.setText(AnnotatedString(slice))
                                            pushUndo(text)
                                            val newTxt = text.text.removeRange(sel.start, sel.end)
                                            text = TextFieldValue(newTxt, selection = TextRange(sel.start))
                                            redoStack.clear()
                                        }
                                    },
                                    label = { Text("Cut") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
                                    enabled = hasSelection
                                )
                                // COPY (selection only)
                                AssistChip(
                                    onClick = {
                                        if (!hasSelection) return@AssistChip
                                        val sel = text.selection
                                        val slice = text.text.substring(sel.start, sel.end)
                                        clipboard.setText(AnnotatedString(slice))
                                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Copy") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    enabled = hasSelection
                                )
                                // PASTE (replaces selection or inserts at caret)
                                AssistChip(
                                    onClick = {
                                        val clip = clipboard.getText()?.text ?: return@AssistChip
                                        if (clip.isNotEmpty()) {
                                            pushUndo(text)
                                            val sel = text.selection
                                            val start = sel.start
                                            val end = sel.end
                                            val rebuilt = buildString {
                                                append(text.text.substring(0, start))
                                                append(clip)
                                                append(text.text.substring(end))
                                            }
                                            val newCaret = start + clip.length
                                            text = TextFieldValue(rebuilt, selection = TextRange(newCaret))
                                            redoStack.clear()
                                        }
                                    },
                                    label = { Text("Paste") },
                                    leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 28.dp)
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text("Chars: $chars  •  Words: $words", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .background(cs.surface)
                ) {
                    // --- Find & Replace bar ---
                    if (showFindReplaceBar) {
                        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, color = cs.surface) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = findQuery,
                                        onValueChange = { findQuery = it },
                                        label = { Text("Find") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = replaceText,
                                        onValueChange = { replaceText = it },
                                        label = { Text("Replace with") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row {
                                        FilterChip(
                                            selected = caseSensitive,
                                            onClick = { caseSensitive = !caseSensitive },
                                            label = { Text("Case sensitive") },
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        FilterChip(
                                            selected = wholeWord,
                                            onClick = { wholeWord = !wholeWord },
                                            label = { Text("Whole word") },
                                        )
                                    }
                                    // Make this a filled/tonal button (NOT outlined) for visibility
                                    FilledTonalButton(
                                        enabled = findQuery.isNotBlank(),
                                        onClick = {
                                            if (findQuery.isNotEmpty()) {
                                                val escaped = Regex.escape(findQuery)
                                                val pattern = if (wholeWord) "\\b$escaped\\b" else escaped
                                                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                                                val regex = Regex(pattern, options)
                                                pushUndo(text)
                                                val replaced = regex.replace(text.text, replaceText)
                                                text = TextFieldValue(replaced)
                                                redoStack.clear()
                                                Toast.makeText(ctx, "Replaced all", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .height(36.dp)           // 👈 reduce button height
                                            .widthIn(min = 60.dp)
                                    ) {
                                        Text("Replace All", fontSize = 13.sp) }
                                }
                            }
                        }
                    }

                    // --- Editor area (virtual scroll, measured widths) ---
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(cs.surface)
                            .horizontalScroll(hScroll)
                            .verticalScroll(vScroll)
                    ) {
                        val density = LocalDensity.current
                        val textMeasurer = rememberTextMeasurer()

                        // Gutter width from largest line number + padding
                        val lineCount = remember(text.text) { text.text.count { it == '\n' } + 1 }
                        val largestNumberLabel = remember(lineCount) { lineCount.coerceAtLeast(1).toString() }
                        val gutterLabelWidthPx = remember(lineCount) {
                            textMeasurer
                                .measure(AnnotatedString(largestNumberLabel), style = editorTextStyle)
                                .size.width
                        }
                        val gutterPadding = 16.dp
                        val gutterWidth = with(density) { gutterLabelWidthPx.toDp() } + gutterPadding
                        val numbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

                        // Content width based on longest line
                        val longestLine = remember(text.text) {
                            text.text.split('\n').maxByOrNull { it.length }?.take(20000) ?: ""
                        }
                        val contentWidthPx = remember(longestLine) {
                            textMeasurer
                                .measure(AnnotatedString(if (longestLine.isEmpty()) " " else longestLine), style = editorTextStyle)
                                .size.width
                        }
                        val contentPadding = 16.dp
                        val contentWidth = with(density) { contentWidthPx.toDp() } + contentPadding

                        val separatorWidth = 1.dp
                        val minEditorArea = (maxWidth - gutterWidth - separatorWidth).coerceAtLeast(0.dp)
                        val editorDrawWidth = contentWidth.coerceAtLeast(minEditorArea)

                        // ----- Text layout result for hit-testing & caret rect -----
                        val layoutResultState = remember { mutableStateOf<TextLayoutResult?>(null) }

                        Row(Modifier.width(gutterWidth + separatorWidth + editorDrawWidth)) {
                            // Gutter
                            Text(
                                text = numbers,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                color = cs.onSurface.copy(alpha = 0.55f),
                                softWrap = false,
                                modifier = Modifier
                                    .width(gutterWidth)
                                    .background(cs.surfaceVariant)
                                    .padding(horizontal = gutterPadding / 2, vertical = 8.dp)
                            )

                            // Separator
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .width(separatorWidth)
                                    .background(cs.outlineVariant)
                            )

                            // Editor
                            BasicTextField(
                                value = text,
                                onValueChange = { new ->
                                    pushUndo(text)
                                    text = new
                                    redoStack.clear()
                                },
                                textStyle = editorTextStyle.copy(color = Color.Transparent),
                                modifier = Modifier
                                    .width(editorDrawWidth)
                                    .fillMaxHeight()
                                    .padding(horizontal = contentPadding / 2, vertical = 8.dp)
                                    .onFocusChanged { editorFocused = it.isFocused }
                                    // Double-tap to select word
                                    .pointerInput(text.text) {
                                        detectTapGestures(
                                            onDoubleTap = { pos ->
                                                val layout = layoutResultState.value ?: return@detectTapGestures
                                                val offset = layout.getOffsetForPosition(pos)
                                                val content = text.text
                                                if (content.isEmpty()) return@detectTapGestures

                                                fun isWordChar(ch: Char) = ch.isLetterOrDigit() || ch == '_'

                                                var start = offset.coerceIn(0, content.length)
                                                var end = start

                                                if (start in content.indices &&
                                                    !isWordChar(content[start]) &&
                                                    start > 0 && isWordChar(content[start - 1])
                                                ) start--

                                                while (start > 0 && isWordChar(content[start - 1])) start--
                                                while (end < content.length && isWordChar(content[end])) end++

                                                if (start < end) {
                                                    text = TextFieldValue(
                                                        text = content,
                                                        selection = TextRange(start, end)
                                                    )
                                                }
                                            }
                                        )
                                    },
                                singleLine = false,
                                cursorBrush = if (drawerState.isClosed && editorFocused)
                                    SolidColor(Color(0xFFFFD700)) else SolidColor(Color.Transparent)
                            ) { innerTextField ->
                                val syntaxAnnotated = remember(text.text, config) { highlightWithConfig(text.text, config) }
                                val matchesOverlay = remember(text.text, matchRanges, findQuery) {
                                    buildAnnotatedString {
                                        append(text.text)
                                        matchRanges.forEach { r ->
                                            addStyle(
                                                SpanStyle(background = Color(0x66FFD54F)),
                                                r.first, r.last + 1
                                            )
                                        }
                                    }
                                }

                                Box(Modifier.fillMaxSize()) {
                                    // Syntax layer
                                    Text(
                                        text = syntaxAnnotated,
                                        style = editorTextStyle.copy(color = cs.onSurface),
                                        softWrap = false,
                                        onTextLayout = { layoutResultState.value = it } // capture layout
                                    )
                                    // Highlight overlay
                                    if (findQuery.isNotBlank() && matchRanges.isNotEmpty()) {
                                        Text(
                                            text = matchesOverlay,
                                            style = editorTextStyle.copy(color = Color.Transparent),
                                            softWrap = false
                                        )
                                    }
                                    // Actual editable field
                                    innerTextField()

                                    if (text.text.isEmpty()) {
                                        Text(
                                            "Type here…",
                                            color = cs.onSurface.copy(alpha = 0.4f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 16.sp,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // ----- Auto horizontal scroll to keep caret visible near right edge -----
                        val edgeGap = 24.dp
                        val edgeGapPx = with(density) { edgeGap.toPx() }
                        val viewportPx = with(density) { editorDrawWidth.toPx() }

                        LaunchedEffect(text.selection, layoutResultState.value, editorDrawWidth) {
                            val layout = layoutResultState.value ?: return@LaunchedEffect
                            val caretIndex = text.selection.end.coerceIn(0, text.text.length)
                            val caretRect = layout.getCursorRect(caretIndex)
                            val caretX = caretRect.right

                            val current = hScroll.value.toFloat()
                            val max = hScroll.maxValue.toFloat()
                            val rightVisibleX = current + viewportPx
                            val leftVisibleX = current

                            val target: Float? = when {
                                // if caret goes beyond right edge, bring it back with small margin
                                caretX + edgeGapPx > rightVisibleX -> caretX + edgeGapPx - viewportPx
                                // if caret goes left beyond left edge (e.g., user clicked/selected), nudge right
                                caretX - edgeGapPx < leftVisibleX -> caretX - edgeGapPx
                                else -> null
                            }

                            target?.let {
                                val clamped = it.coerceIn(0f, max)
                                hScroll.animateScrollTo(clamped.roundToInt())
                            }
                        }
                    }
                }
            }

            // Compile result dialog
            compileResp?.let { r ->
                // -------- helpers (local) --------
                fun stripAnsi(s: String) = s.replace(Regex("\\u001B\\[[;?0-9]*[A-Za-z]"), "")
                fun normalize(s: String) = stripAnsi(s).replace("\r\n", "\n").trim()
                fun dedupLines(s: String): String {
                    if (s.isBlank()) return ""
                    val out = ArrayList<String>()
                    val seen = LinkedHashSet<String>()
                    var blank = false
                    for (raw in s.lines()) {
                        val line = raw.trimEnd()
                        if (line.isEmpty()) {
                            if (!blank) { out.add(""); blank = true }
                            continue
                        }
                        blank = false
                        if (seen.add(line)) out.add(raw)
                    }
                    return out.joinToString("\n")
                }

                val normOut = remember(r.stdout) { normalize(r.stdout) }
                val normErr = remember(r.stderr) { normalize(r.stderr) }

                // Show ONLY ONE stream by default:
                //  - if there was an error/exitCode!=0 or stderr not empty → show stderr
                //  - else show stdout
                val isError = r.exitCode != 0 || normErr.isNotBlank()
                val primaryText = remember(r) {
                    dedupLines(if (isError) normErr else normOut)
                }

                AlertDialog(
                    onDismissRequest = { compileResp = null },
                    title = { Text(if (r.phase == "run") "Program Output" else "Compiler Output") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(label = { Text("phase: ${r.phase}") }, onClick = {})
                            AssistChip(label = { Text("exit: ${r.exitCode}") }, onClick = {})

                            if (primaryText.isNotBlank()) {
                                val label = if (isError) "ERROR" else "OUTPUT"
                                Text(
                                    "$label:\n$primaryText",
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isError) Color(0xFFFF6B6B) else Color.Unspecified
                                )
                            } else {
                                Text("No output.", fontFamily = FontFamily.Monospace)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { compileResp = null }) { Text("OK") } }
                )
            }


        }
    }
}
