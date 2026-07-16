import os
import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

# Block 1
code = code.replace(
    "import kotlinx.coroutines.flow.asStateFlow\n\n// --- Preferences & Persistence ---",
    "import kotlinx.coroutines.flow.asStateFlow\nimport java.text.NumberFormat\nimport java.util.Locale\n\nfun Int.fmt(): String = NumberFormat.getNumberInstance(Locale.US).format(this)\n\n// --- Preferences & Persistence ---"
)

# Block 2
code = code.replace(
    "val totalBuyIn: Int = 1000,\n    val currentBet: Int = 0,",
    "val totalBuyIn: Int = 1000,\n    val totalRebuyAmount: Int = 0,\n    val currentBet: Int = 0,"
)

# Block 3
code = code.replace(
    "val playersStr = allPlayersToSave.joinToString(\";\") { \"${it.id},${it.name},${it.stack},${it.totalBuyIn},${it.handsPlayed},${it.vpipCount},${it.pfrCount}\" }",
    "val playersStr = allPlayersToSave.joinToString(\";\") { \"${it.id},${it.name},${it.stack},${it.totalBuyIn},${it.handsPlayed},${it.vpipCount},${it.pfrCount},${it.totalRebuyAmount}\" }"
)

# Block 4
code = code.replace(
    "val pfr = if (parts.size >= 7) parts[6].toInt() else 0\n                    pList.add(Player(id = parts[0].toInt(), name = parts[1], stack = parts[2].toInt(), totalBuyIn = tbi, handsPlayed = hp, vpipCount = vpip, pfrCount = pfr))",
    "val pfr = if (parts.size >= 7) parts[6].toInt() else 0\n                    val rebuy = if (parts.size >= 8) parts[7].toInt() else 0\n                    pList.add(Player(id = parts[0].toInt(), name = parts[1], stack = parts[2].toInt(), totalBuyIn = tbi, totalRebuyAmount = rebuy, handsPlayed = hp, vpipCount = vpip, pfrCount = pfr))"
)

# Block 5
code = code.replace(
    "val newPool = _state.value.playerPool.map { it.copy(stack = 1000, totalBuyIn = 1000) }\n        val newPlayers = _state.value.players.map { it.copy(stack = 1000, totalBuyIn = 1000) }",
    "val newPool = _state.value.playerPool.map { it.copy(stack = 1000, totalBuyIn = 1000, totalRebuyAmount = 0) }\n        val newPlayers = _state.value.players.map { it.copy(stack = 1000, totalBuyIn = 1000, totalRebuyAmount = 0) }"
)

# Block 6
code = code.replace(
    "val newPool = _state.value.playerPool.map { if (it.id == id) it.copy(stack = newStack, totalBuyIn = newStack) else it }\n        val newPlayers = _state.value.players.map { if (it.id == id) it.copy(stack = newStack, totalBuyIn = newStack) else it }",
    "val newPool = _state.value.playerPool.map { \n            if (it.id == id) {\n                val diff = newStack - it.stack\n                val newRebuy = if (diff > 0) it.totalRebuyAmount + diff else it.totalRebuyAmount\n                it.copy(stack = newStack, totalBuyIn = it.totalBuyIn + diff, totalRebuyAmount = newRebuy)\n            } else it \n        }\n        val newPlayers = _state.value.players.map { \n            if (it.id == id) {\n                val diff = newStack - it.stack\n                val newRebuy = if (diff > 0) it.totalRebuyAmount + diff else it.totalRebuyAmount\n                it.copy(stack = newStack, totalBuyIn = it.totalBuyIn + diff, totalRebuyAmount = newRebuy)\n            } else it \n        }"
)

# Block 7
code = code.replace(
    "if (it.id == id) it.copy(stack = it.stack + amount, totalBuyIn = it.totalBuyIn + amount) else it",
    "if (it.id == id) it.copy(stack = it.stack + amount, totalBuyIn = it.totalBuyIn + amount, totalRebuyAmount = it.totalRebuyAmount + amount) else it"
)

code = code.replace(
    "LogEntry(\"${players.find { it.id == id }?.name} rebuys $amount\"",
    "LogEntry(\"${players.find { it.id == id }?.name} rebuys ${amount.fmt()}\""
)

# Block 8
code = code.replace(
    "fun movePlayer(index: Int, direction: Int, context: Context) {",
    "fun chopBlinds(context: Context) {\n        val players = _state.value.players.map { p ->\n            p.copy(\n                stack = p.stack + p.totalInvested,\n                totalInvested = 0,\n                currentBet = 0,\n                handsPlayed = (p.handsPlayed - 1).coerceAtLeast(0)\n            )\n        }\n        _state.value = _state.value.copy(players = players, mainPot = 0)\n        \n        val nextDealer = (_state.value.dealerIdx + 1) % _state.value.playerCount\n        startHand(context, dealerIdx = nextDealer, logs = _state.value.logs + LogEntry(\"🤝 Blinds Chopped.\", LogType.INFO))\n    }\n\n    fun movePlayer(index: Int, direction: Int, context: Context) {"
)

# Block 9
code = code.replace(
    "PnLScreen(\n                results = state.settlementResults!!,\n                onNextHand = {\n                    triggerVibration(context, 1)\n                    viewModel.startNextHand(context)\n                }\n            )",
    "PnLScreen(\n                results = state.settlementResults!!,\n                players = state.players,\n                quickChips = state.quickAddChips,\n                onNextHand = {\n                    triggerVibration(context, 1)\n                    viewModel.startNextHand(context)\n                },\n                onRebuy = { playerId, amount ->\n                    triggerVibration(context, 1)\n                    viewModel.rebuyPlayer(playerId, amount, context)\n                }\n            )"
)

# Block 10
code = code.replace(
    "fun PnLScreen(results: List<PnLResult>, onNextHand: () -> Unit) {\n    Box(modifier = Modifier.fillMaxSize()) {",
    """fun PnLScreen(
    results: List<PnLResult>, 
    players: List<Player>,
    quickChips: List<Int>,
    onNextHand: () -> Unit,
    onRebuy: (Int, Int) -> Unit
) {
    var showRebuyDialogFor by remember { mutableStateOf<Int?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {"""
)

# Block 11 - PnLScreen item modification for formatting and rebuy button
code = code.replace(
    "text = \"${result.payout}\",\n                                        fontSize = 32.sp",
    "text = result.payout.fmt(),\n                                        fontSize = 32.sp"
)
code = code.replace(
    "text = \"净: $sign${result.netProfit}\",\n                                        fontSize = 14.sp",
    "text = \"净: $sign${result.netProfit.fmt()}\",\n                                        fontSize = 14.sp"
)
target_rebuy_button = """                                        fontFamily = FontFamily.Monospace,
                                        color = amountColor
                                    )
                                }
                            }
                        }
                    }"""
replacement_rebuy_button = """                                        fontFamily = FontFamily.Monospace,
                                        color = amountColor
                                    )
                                    val player = players.find { it.id == result.playerId }
                                    if ((player?.stack ?: 0) == 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Button(
                                            onClick = { showRebuyDialogFor = result.playerId },
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = CasinoBgDark),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("[REBUY]", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }"""
code = code.replace(target_rebuy_button, replacement_rebuy_button)

# Block 12 - PnLScreen Dialog addition
target_pnl_dialog = """                }
            }
        }
    }
}

@Composable
fun FinalSettlementScreen"""
replacement_pnl_dialog = """                }
            }
        }
    }

    if (showRebuyDialogFor != null) {
        val player = players.find { it.id == showRebuyDialogFor }
        CustomKeyboardDialog(
            title = "Rebuy - ${player?.name}",
            initialValue = "",
            isNumeric = true,
            minNumeric = 1,
            quickChips = quickChips,
            onDismiss = { showRebuyDialogFor = null },
            onConfirm = { newVal ->
                val amount = newVal.toIntOrNull() ?: 0
                if (amount > 0) onRebuy(showRebuyDialogFor!!, amount)
                showRebuyDialogFor = null
            }
        )
    }
}

@Composable
fun FinalSettlementScreen"""
code = code.replace(target_pnl_dialog, replacement_pnl_dialog)

# Block 13 - Main Pot formatting
code = code.replace("text = \"$targetPot\",\n                                fontSize = 42.sp", "text = targetPot.fmt(),\n                                fontSize = 42.sp")

# Block 14 - CanChopBlinds + Tooltip
target_pot_header = """                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.street.name.replace("_", " "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = streetColor,
                            modifier = Modifier
                                .background(streetColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "MAIN POT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = Emerald400
                        )
                        AnimatedContent("""

replacement_pot_header = """                    val activeNonFolded = state.players.filter { !it.isFolded && it.isInHand }
                    val canChopBlinds = state.street == Street.PRE_FLOP && activeNonFolded.size == 2 && state.players.sumOf { it.totalInvested } == state.sbAmount + state.bbAmount
                    
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.street.name.replace("_", " "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = streetColor,
                            modifier = Modifier
                                .background(streetColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        
                        if (canChopBlinds) {
                            Button(
                                onClick = { 
                                    triggerVibration(context, 1)
                                    viewModel.chopBlinds(context) 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FoldRed, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("🤝 CHOP BLINDS", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                            }
                        } else {
                            var showPotBreakdown by remember { mutableStateOf(false) }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { showPotBreakdown = !showPotBreakdown }
                            ) {
                                Text(
                                    text = "MAIN POT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = Emerald400
                                )
                                AnimatedContent("""

code = code.replace(target_pot_header, replacement_pot_header)

target_pot_footer = """                            ) { targetPot ->
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
                        
                        if (state.isSettlementPhase) {"""

replacement_pot_footer = """                            ) { targetPot ->
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

code = code.replace(target_pot_footer, replacement_pot_footer)


# Block 15 - Rebuy amount in PlayerCardCompact
target_name = """                                Text(
                                    text = player.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))"""
replacement_name = """                                Text(
                                    text = player.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                if (player.totalRebuyAmount > 0) {
                                    Text(
                                        text = " [RB: +${player.totalRebuyAmount.fmt()}]",
                                        color = FoldRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(6.dp))"""
code = code.replace(target_name, replacement_name)

# Replace all other formatting (minBetTotal, actualMaxBetTotal, minExtra, actualMaxExtra, etc.)
code = code.replace("append(\"$minBetTotal\")", "append(minBetTotal.fmt())")
code = code.replace("append(\"(+$minExtra)\")", "append(\"(+${minExtra.fmt()})\")")
code = code.replace("append(\"$actualMaxBetTotal\")", "append(actualMaxBetTotal.fmt())")
code = code.replace("append(\"(+$actualMaxExtra)\")", "append(\"(+${actualMaxExtra.fmt()})\")")
code = code.replace("Text(player.stack.fmt(),", "Text(\"${player.stack}\",") # Revert to string formatting if it was already? No, the user wants .fmt() for stack!
# Let's just find everywhere where `text = "$...` and `text = "...${...}"` exists for money.

# Format Stack in Setup
code = code.replace("Text(\"${player.stack}\", color = PotGold", "Text(player.stack.fmt(), color = PotGold")

# Format Stack in PlayerCardCompact
code = code.replace("text = \"$$${player.stack}\"", "text = \"$${player.stack.fmt()}\"")

# Format Total Invested
code = code.replace("Total Inv: ${player.totalInvested}", "Total Inv: ${player.totalInvested.fmt()}")

# Format Bets
code = code.replace("text = \"$bet\",", "text = bet.fmt(),")
code = code.replace("\"--- ${nextStreet.name} ---\"", "\"--- ${nextStreet.name} (Pot: ${newMainPot.fmt()}) ---\"")
code = code.replace("Calls $highestBetBefore\"", "Calls ${highestBetBefore.fmt()}\"")
code = code.replace("Raises to $actualBetAmount\"", "Raises to ${actualBetAmount.fmt()}\"")
code = code.replace("Bets $actualBetAmount\"", "Bets ${actualBetAmount.fmt()}\"")
code = code.replace("BIGGEST POT: 💰 $biggestPot\"", "BIGGEST POT: 💰 ${biggestPot.fmt()}\"")
code = code.replace("text = \"$sign$profit\"", "text = \"$sign${profit.fmt()}\"")
code = code.replace("Text(\"$${pot.amount}\",", "Text(\"$${pot.amount.fmt()}\",")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)

