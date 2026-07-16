import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

replacement = """    val targetBgColor = when {
        isStrikeOut -> Color(0xFF1A0505)
        isFolded -> Color(0xFF0A0A0A)
        isAllIn -> FoldRed.copy(alpha = 0.15f)
        else -> GlassWhite
    }
    val targetBorderColor = when {
        isActive -> GoldYellow.copy(alpha = glowAlpha)
        isAllIn -> FoldRed.copy(alpha = 0.8f)
        isStrikeOut -> Color.Transparent
        isFolded -> Color.Transparent
        isRaiser -> Color(0xFFF97316)
        else -> GlassBorder
    }
    val bgColor by animateColorAsState(targetBgColor, label="bg")
    val borderColor by animateColorAsState(targetBorderColor, label="border")
    val borderWidth = if (isActive || isRaiser || isAllIn) 2.dp else 1.dp"""

pattern = re.compile(r"    val bgColor = when \{.*?val borderWidth = if \(isActive \|\| isRaiser \|\| isAllIn\) 2\.dp else 1\.dp", re.DOTALL)
match = pattern.search(content)
if match:
    new_content = content[:match.start()] + replacement + content[match.end():]
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(new_content)
    print("Replaced successfully")
else:
    print("Match not found")
