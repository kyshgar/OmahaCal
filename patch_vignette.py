import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Import
if "import androidx.compose.ui.draw.drawWithContent" not in content:
    content = content.replace("import androidx.compose.ui.draw.alpha", "import androidx.compose.ui.draw.alpha\nimport androidx.compose.ui.draw.drawWithContent")
    print("Import added")
else:
    print("Import already exists")

# LaunchedEffect
launched_effect_old = """    var showSettleDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.street) {
        if (state.isGameStarted && state.street != Street.PRE_FLOP) {
            triggerVibration(context, 0)
            delay(100)
            triggerVibration(context, 0)
        }
    }"""

launched_effect_new = """    var showSettleDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    val vignetteAlpha = remember { Animatable(0f) }
    var vignetteColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(state.street) {
        if (state.isGameStarted && state.street != Street.PRE_FLOP) {
            vignetteColor = when (state.street) {
                Street.FLOP -> Color(0xFF00C853)
                Street.TURN -> Color(0xFFFFD54F)
                Street.RIVER -> Color(0xFFD50000)
                else -> Color.Transparent
            }
            launch {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(50)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            launch {
                vignetteAlpha.snapTo(0f)
                vignetteAlpha.animateTo(1f, tween(300, easing = FastOutLinearInEasing))
                vignetteAlpha.animateTo(0f, tween(600, easing = LinearOutSlowInEasing))
            }
        }
    }"""

if launched_effect_old in content:
    content = content.replace(launched_effect_old, launched_effect_new)
    print("LaunchedEffect replaced")
else:
    print("LaunchedEffect not found")

# Modifier.drawWithContent
box_old = """    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(CasinoBgDark, CasinoBgLight)))
                .padding(padding)
        ) {"""

box_new = """    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(CasinoBgDark, CasinoBgLight)))
                .padding(padding)
                .drawWithContent {
                    drawContent()
                    if (vignetteAlpha.value > 0f) {
                        val brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, vignetteColor),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width.coerceAtLeast(size.height) / 1.5f
                        )
                        drawRect(
                            brush = brush,
                            size = size,
                            alpha = vignetteAlpha.value
                        )
                    }
                }
        ) {"""

if box_old in content:
    content = content.replace(box_old, box_new)
    print("Box replaced")
else:
    print("Box not found")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
