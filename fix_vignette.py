import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

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

# Find the first occurrence (PlODealerApp)
first_index = content.find(box_new)
if first_index != -1:
    second_index = content.find(box_new, first_index + len(box_new))
    if second_index != -1:
        # Revert second occurrence
        content = content[:second_index] + content[second_index:].replace(box_new, box_old, 1)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
