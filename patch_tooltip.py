import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

replacement_tooltip = """                        } else {
                            var showPotBreakdown by remember { mutableStateOf(false) }
                            val potInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable(
                                    interactionSource = potInteractionSource,
                                    indication = null,
                                    onClick = { showPotBreakdown = !showPotBreakdown }
                                )
                            ) {
                                Text(
                                    text = "MAIN POT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = Emerald400
                                )
                                AnimatedContent(
                                    targetState = state.mainPot + state.players.sumOf { it.currentBet },
                                    transitionSpec = {
                                        slideInVertically { height -> height } + fadeIn() togetherWith slideOutVertically { height -> -height } + fadeOut()
                                    },
                                    label = "PotAnimation"
                                ) { targetPot ->
                                    Text(
                                        text = targetPot.fmt(),
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-1).sp,
                                        color = GoldYellow,
                                        style = LocalTextStyle.current.copy(
                                            shadow = Shadow(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                offset = Offset(0f, 4f),
                                                blurRadius = 8f
                                            )
                                        )
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showPotBreakdown) {
                                val breakdown = state.players.filter { it.totalInvested > 0 }.joinToString(" | ") { "${it.name}: ${it.totalInvested.fmt()}" }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = breakdown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.background(GoldYellow, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }"""

pattern_tooltip = re.compile(r"                        \} else \{\n                            var showPotBreakdown by remember \{ mutableStateOf\(false\) \}\n                            Column\(\n                                horizontalAlignment = Alignment\.CenterHorizontally,\n                                modifier = Modifier\.clickable \{ showPotBreakdown = !showPotBreakdown \}\n                            \) \{.*?                            AnimatedVisibility\(visible = showPotBreakdown\) \{\n                                val breakdown = state\.players\.filter \{ it\.totalInvested > 0 \}\.joinToString\(\" \| \"\) \{ \"\$\{it\.name\}: \$\{it\.totalInvested\.fmt\(\)\}\" \}\n                                Spacer\(Modifier\.height\(4\.dp\)\)\n                                Text\(\n                                    text = breakdown,\n                                    fontSize = 10\.sp,\n                                    color = Color\.White\.copy\(0\.8f\),\n                                    modifier = Modifier\.background\(Color\.Black\.copy\(0\.4f\), RoundedCornerShape\(4\.dp\)\)\.padding\(horizontal = 8\.dp, vertical = 4\.dp\)\n                                \)\n                            \}\n                        \}", re.DOTALL)

if pattern_tooltip.search(content):
    content = pattern_tooltip.sub(replacement_tooltip, content)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
    print("Tooltip replaced successfully")
else:
    print("Tooltip match not found")
