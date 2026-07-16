import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

replacement = """                    Column(
                        modifier = Modifier
                            .clickable(onClick = onRenameClick)
                            .padding(
                                start = if (isCollapsed) 16.dp else 0.dp,
                                end = 4.dp,
                                top = if (isCollapsed) 8.dp else 0.dp,
                                bottom = if (isCollapsed) 8.dp else 0.dp
                            )
                    ) {
                        if (isCollapsed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = player.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(0.5f),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Text(
                                    text = if (isStrikeOut) "STRIKE OUT" else "FOLDED",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isStrikeOut) FoldRed.copy(0.8f) else Color.Gray,
                                    letterSpacing = 2.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                    if (isStrikeOut) {
                                        Button(
                                            onClick = onRebuyClick,
                                            shape = RoundedCornerShape(4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500.copy(0.2f), contentColor = Emerald500),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("REBUY", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                        }
                                    } else {
                                        Text(
                                            text = player.stack.fmt(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Emerald400.copy(0.6f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val nameColor by animateColorAsState(Color.White, label = "nameColor")
                                Text(
                                    text = player.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = nameColor,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))
                                if (isDealer) {
                                    Text(" D ", modifier = Modifier.background(Color.White, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (isSb) {
                                    Text(" SB ", modifier = Modifier.background(CallBlue, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (isBb) {
                                    Text(" BB ", modifier = Modifier.background(GoldYellow, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val stackColor by animateColorAsState(Emerald400, label = "stackColor")
                                Text(
                                    text = "$${player.stack.fmt()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = stackColor,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.width(6.dp))
                                if (isActive) {
                                    Text("▶ ACTING", fontSize = 10.sp, color = GoldYellow.copy(alpha = glowAlpha), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                } else {
                                    Text("Total Inv: ${player.totalInvested.fmt()}", fontSize = 11.sp, color = Emerald300, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }"""

pattern = re.compile(r"                    Column\(\n                        modifier = Modifier\n                            \.clickable\(onClick = onRenameClick\)\n                            \.padding\(end = 4\.dp\)\n                    \) \{.*?                            \} else if \(isStrikeOut\) \{\n                                Button\(\n                                    onClick = onRebuyClick,\n                                    shape = RoundedCornerShape\(4\.dp\),\n                                    colors = ButtonDefaults\.buttonColors\(containerColor = Emerald500\.copy\(0\.2f\), contentColor = Emerald500\),\n                                    contentPadding = PaddingValues\(horizontal = 8\.dp, vertical = 0\.dp\),\n                                    modifier = Modifier\.height\(24\.dp\)\.padding\(start = 8\.dp\)\n                                \) \{\n                                    Text\(\"REBUY\", fontSize = 10\.sp, fontWeight = FontWeight\.Black\)\n                                \}\n                            \}\n                        \}\n                    \}", re.DOTALL)

if pattern.search(content):
    content = pattern.sub(replacement, content)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Match not found")
