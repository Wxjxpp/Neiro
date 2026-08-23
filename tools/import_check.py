#!/usr/bin/env python3
"""扫描 Kotlin 文件：常用符号被使用但没有对应 import（排除 .xxx 全限定调用）。"""
import re, sys, os

SYMBOLS = [
    # foundation.layout
    "Box","Column","Row","Spacer","Arrangement","PaddingValues","WindowInsets",
    "aspectRatio","fillMaxSize","fillMaxWidth","fillMaxHeight","statusBars","navigationBars",
    "systemBars","safeDrawing","asPaddingValues","calculateTopPadding","calculateBottomPadding",
    "FlowRow","ExperimentalLayoutApi","imePadding","navigationBarsPadding","statusBarsPadding",
    # foundation
    "clickable","combinedClickable","ExperimentalFoundationApi","detectVerticalDragGestures",
    "detectHorizontalDragGestures","detectTapGestures","nestedScroll",
    # lazy
    "LazyColumn","LazyRow","LazyVerticalGrid","items","itemsIndexed","rememberLazyListState",
    "rememberLazyGridState",
    # runtime
    "Composable","remember","rememberSaveable","mutableStateOf","mutableStateListOf","getValue","setValue",
    "LaunchedEffect","DisposableEffect","rememberCoroutineScope","collectAsState","derivedStateOf",
    "snapshotFlow","key",
    # material.icons
    "Icons",
    # material3
    "MaterialTheme","Text","Icon","IconButton","Surface","Card","Scaffold","TopAppBar",
    "CenterAlignedTopAppBar","LargeTopAppBar","MediumTopAppBar","HorizontalDivider","VerticalDivider",
    "AlertDialog","ModalBottomSheet","DropdownMenu","DropdownMenuItem","Slider","Switch","Checkbox",
    "FilterChip","AssistChip","ElevatedAssistChip","FloatingActionButton","ExtendedFloatingActionButton",
    "CircularProgressIndicator","LinearProgressIndicator","SnackbarHost","SnackbarHostState",
    "ModalNavigationDrawer","rememberDrawerState","DrawerValue","NavigationDrawerItem",
    "Button","OutlinedButton","TextButton","FilledTonalButton","ElevatedButton","OutlinedTextField",
    "TextField","FILLED_TONAL_ICON_BUTTON","FilledIconButton","OutlinedIconToggleButton",
    "SegmentedButton","SingleChoiceSegmentedButtonRow","rememberSwipeToDismissBoxState",
    "animateFloatingActionButton",
    # animation
    "AnimatedVisibility","AnimatedContent","Crossfade","animateFloatAsState","animateColorAsState",
    "animateDpAsState","tween","spring","fadeIn","fadeOut","slideInVertically","slideOutVertically",
    "expandVertically","shrinkVertically","togetherWith","EnterTransition","ExitTransition",
    # ui
    "Alignment","Modifier","clip","background","alpha","graphicsLayer","blur","drawBehind",
    "pointerInput","TransformOrigin","ContentScale","TextOverflow","FontWeight","textAlign",
    "LocalContext","LocalDensity","LocalConfiguration","LocalLifecycleOwner",
    "LocalSoftwareKeyboardController","stringResource","BackHandler","offset",
]

def main(paths):
    problems = []
    for path in paths:
        try:
            src = open(path, encoding="utf-8").read()
        except Exception:
            continue
        imports = set()
        for m in re.finditer(r"^import\s+(?:[\w.]+\.)(\w+)", src, re.M):
            imports.add(m.group(1))
        # wildcard imports
        wildcards = re.findall(r"^import\s+([\w.]+)\.\*", src, re.M)
        body = src.split("\n")
        for sym in SYMBOLS:
            if sym in imports:
                continue
            # 使用点：前面不是 '.'（非全限定），不是注释行
            pat = re.compile(r"(?<![.\w])" + re.escape(sym) + r"\b")
            used = False
            for line in body:
                s = line.strip()
                if s.startswith("//") or s.startswith("*") or s.startswith("/*"):
                    continue
                if pat.search(line):
                    # 检查是否所有出现都在全限定链中（前一个字符是 .）
                    real = [m for m in pat.finditer(line) if line[m.start()-1] != "."]
                    if real:
                        used = True
                        break
            if not used:
                continue
            # 通配符兜底（如 androidx.compose.foundation.layout.*）
            covered = False
            for w in wildcards:
                if w.endswith(".layout") or w.endswith(".material3") or w.endswith(".runtime") or w == "androidx.compose.ui":
                    covered = True
                    break
            if covered:
                continue
            problems.append(f"{path}: {sym}")
    print("\n".join(problems) if problems else "ALL CLEAN")

if __name__ == "__main__":
    main(sys.argv[1:])
