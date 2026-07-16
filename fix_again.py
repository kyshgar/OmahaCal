with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()
import re
target = r"\} \} AnimatedVisibility\(visible = showPotBreakdown\) \{ val breakdown = state.players.filter \{ it.totalInvested > 0 \}.joinToString\(\" \| \"\) \{ \"\$\{it.name\}: \$\{it.totalInvested.fmt\(\)\}\" \}; Spacer\(Modifier.height\(4.dp\)\); Text\(text = breakdown, fontSize = 10.sp, color = Color.White.copy\(0.8f\), modifier = Modifier.background\(Color.Black.copy\(0.4f\), RoundedCornerShape\(4.dp\)\).padding\(horizontal = 8.dp, vertical = 4.dp\)\) \} if \(state.isSettlementPhase\) \{"
replacement = """}

                        AnimatedVisibility(visible = showPotBreakdown) {
                            val breakdown = state.players.filter { it.totalInvested > 0 }.joinToString(" | ") { "${it.name}: ${it.totalInvested.fmt()}" }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = breakdown,
                                fontSize = 10.sp,
                                color = Color.White.copy(0.8f),
                                modifier = Modifier.background(Color.Black.copy(0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        }
                        }
                        
                        if (state.isSettlementPhase) {"""

code = re.sub(target, replacement, code)
with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)
