import os
import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

# GameState & SetupConfig
code = code.replace("val bbAmount: Int = 20,\n    val quickAddChips:", "val bbAmount: Int = 20,\n    val defaultBuyIn: Int = 1000,\n    val quickAddChips:")
code = code.replace("val lastAggressorId: Int = -1,\n    val actedPlayerIdsThisStreet", "val lastAggressorId: Int = -1,\n    val lastAggressorIdOverall: Int = -1,\n    val actedPlayerIdsThisStreet")
code = code.replace("val bbAmount: Int,\n    val quickAddChips:", "val bbAmount: Int,\n    val defaultBuyIn: Int,\n    val quickAddChips:")

# Prefs
code = code.replace(".putInt(\"bbAmount\", state.bbAmount)", ".putInt(\"bbAmount\", state.bbAmount)\n            .putInt(\"defaultBuyIn\", state.defaultBuyIn)")
code = code.replace("val bbAmount = prefs.getInt(\"bbAmount\", 20)", "val bbAmount = prefs.getInt(\"bbAmount\", 20)\n        val defaultBuyIn = prefs.getInt(\"defaultBuyIn\", 1000)")
code = code.replace("SetupConfig(count, isHiLo, players, sbAmount, bbAmount, finalChips, biggestPot)", "SetupConfig(count, isHiLo, players, sbAmount, bbAmount, defaultBuyIn, finalChips, biggestPot)")

# Player
code = code.replace("val hasPfrThisHand: Boolean = false,\n    val isInHand:", "val hasPfrThisHand: Boolean = false,\n    val kills: Int = 0,\n    val isInHand:")

# GameViewModel
code = code.replace("stack = 1000)", "stack = config.defaultBuyIn, totalBuyIn = config.defaultBuyIn)")
code = code.replace("bbAmount = config.bbAmount,\n                quickAddChips = config.quickAddChips", "bbAmount = config.bbAmount,\n                defaultBuyIn = config.defaultBuyIn,\n                quickAddChips = config.quickAddChips")
code = code.replace("it.copy(stack = 1000, totalBuyIn = 1000, totalRebuyAmount = 0)", "it.copy(stack = _state.value.defaultBuyIn, totalBuyIn = _state.value.defaultBuyIn, totalRebuyAmount = 0)")
code = code.replace("fun updateSettings(sb: Int, bb: Int, quickAddChips: List<Int>, context: Context) {\n        _state.value = _state.value.copy(sbAmount = sb, bbAmount = bb, quickAddChips = quickAddChips)", "fun updateSettings(sb: Int, bb: Int, defaultBuyIn: Int, quickAddChips: List<Int>, context: Context) {\n        _state.value = _state.value.copy(sbAmount = sb, bbAmount = bb, defaultBuyIn = defaultBuyIn, quickAddChips = quickAddChips)")
code = code.replace("dealerIdx = dIdx,\n            lastAggressorId = -1,", "dealerIdx = dIdx,\n            lastAggressorId = -1,\n            lastAggressorIdOverall = -1,")
code = code.replace("lastAggressorId = if (isFullRaise) playerId else _state.value.lastAggressorId,\n            logs = logs,", "lastAggressorId = if (isFullRaise) playerId else _state.value.lastAggressorId,\n            lastAggressorIdOverall = if (isRaise) playerId else _state.value.lastAggressorIdOverall,\n            logs = logs,")

code = code.replace("""        val newPlayers = s.players.map { p ->
            val winAmount = payouts[p.id] ?: 0
            p.copy(stack = p.stack + winAmount)
        }""", """        val bankruptPlayers = s.players.filter { p -> 
            p.stack == 0 && (payouts[p.id] ?: 0) == 0 && p.totalInvested > 0 
        }
        val killers = mutableMapOf<Int, Int>()
        bankruptPlayers.forEach { bp ->
            val eligiblePots = pots.filter { it.eligiblePlayerIds.contains(bp.id) }
            val potToAwardKill = eligiblePots.maxByOrNull { it.amount }
            if (potToAwardKill != null) {
                val settlement = settlements.find { it.potId == potToAwardKill.id }
                val highWinners = potToAwardKill.eligiblePlayerIds.intersect(settlement?.highWinners ?: emptySet())
                val lowWinners = potToAwardKill.eligiblePlayerIds.intersect(settlement?.lowWinners ?: emptySet())
                val allWinners = highWinners + lowWinners
                allWinners.forEach { wId ->
                    if (wId != bp.id) {
                        killers[wId] = (killers[wId] ?: 0) + 1
                    }
                }
            }
        }
        val newPlayers = s.players.map { p ->
            val winAmount = payouts[p.id] ?: 0
            val addedKills = killers[p.id] ?: 0
            p.copy(stack = p.stack + winAmount, kills = p.kills + addedKills)
        }""")

# Lobby Settings Dialog Call
target_lobby_settings_call = """        LobbySettingsDialog(
            currentSb = state.sbAmount,
            currentBb = state.bbAmount,
            currentChips = state.quickAddChips,
            onDismiss = { showLobbySettings = false },
            onSave = { sb, bb, chips ->
                viewModel.updateSettings(sb, bb, chips, context)
                showLobbySettings = false
            }
        )"""
replacement_lobby_settings_call = """        LobbySettingsDialog(
            currentSb = state.sbAmount,
            currentBb = state.bbAmount,
            currentBuyIn = state.defaultBuyIn,
            currentChips = state.quickAddChips,
            onDismiss = { showLobbySettings = false },
            onSave = { sb, bb, buyin, chips ->
                viewModel.updateSettings(sb, bb, buyin, chips, context)
                showLobbySettings = false
            }
        )"""
code = code.replace(target_lobby_settings_call, replacement_lobby_settings_call)

# Lobby Settings Dialog Definition
target_lobby_settings_def = """@Composable
fun LobbySettingsDialog(
    currentSb: Int,
    currentBb: Int,
    currentChips: List<Int>,
    onDismiss: () -> Unit,
    onSave: (Int, Int, List<Int>) -> Unit
) {
    var sbText by remember { mutableStateOf(currentSb.toString()) }
    var bbText by remember { mutableStateOf(currentBb.toString()) }
    var chipsText by remember { mutableStateOf(currentChips.joinToString(",")) }"""

replacement_lobby_settings_def = """@Composable
fun LobbySettingsDialog(
    currentSb: Int,
    currentBb: Int,
    currentBuyIn: Int,
    currentChips: List<Int>,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, List<Int>) -> Unit
) {
    var sbText by remember { mutableStateOf(currentSb.toString()) }
    var bbText by remember { mutableStateOf(currentBb.toString()) }
    var buyInText by remember { mutableStateOf(currentBuyIn.toString()) }
    var chipsText by remember { mutableStateOf(currentChips.joinToString(",")) }"""
code = code.replace(target_lobby_settings_def, replacement_lobby_settings_def)

target_lobby_settings_fields = """                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = chipsText,"""
replacement_lobby_settings_fields = """                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = buyInText,
                    onValueChange = { buyInText = it },
                    label = { Text("Default Buy-in", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = Emerald400
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = chipsText,"""
code = code.replace(target_lobby_settings_fields, replacement_lobby_settings_fields)

target_lobby_settings_save = """                    val sb = sbText.toIntOrNull() ?: 10
                    val bb = bbText.toIntOrNull() ?: 20
                    val parsedChips = chipsText.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .take(8)
                    val finalChips = if (parsedChips.isEmpty()) listOf(10,20,100,500) else parsedChips
                    onSave(sb, bb, finalChips)"""
replacement_lobby_settings_save = """                    val sb = sbText.toIntOrNull() ?: 10
                    val bb = bbText.toIntOrNull() ?: 20
                    val buyin = buyInText.toIntOrNull() ?: 1000
                    val parsedChips = chipsText.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .take(8)
                    val finalChips = if (parsedChips.isEmpty()) listOf(10,20,100,500) else parsedChips
                    onSave(sb, bb, buyin, finalChips)"""
code = code.replace(target_lobby_settings_save, replacement_lobby_settings_save)

# Main Animation UI Overlay
target_app_start = """    var showRebuyDialogFor by remember { mutableStateOf<Int?>(null) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSettlementPhase) {"""

replacement_app_start = """    var showRebuyDialogFor by remember { mutableStateOf<Int?>(null) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    
    var showStreetOverlay by remember { mutableStateOf<Street?>(null) }
    LaunchedEffect(state.street, state.isGameStarted) {
        if (state.isGameStarted && state.street != Street.PRE_FLOP) {
            showStreetOverlay = state.street
            delay(600)
            showStreetOverlay = null
        }
    }

    LaunchedEffect(state.isSettlementPhase) {"""
code = code.replace(target_app_start, replacement_app_start)

target_app_box_end = """                EndGameButton(
                    isActivated = state.isEndGameActivated,
                    onActivated = { viewModel.activateEndGame() }
                )
            }
        }
    }

    // --- Dialogs ---"""

replacement_app_box_end = """                EndGameButton(
                    isActivated = state.isEndGameActivated,
                    onActivated = { viewModel.activateEndGame() }
                )
            }
            
            AnimatedVisibility(
                visible = showStreetOverlay != null,
                enter = scaleIn(tween(200), initialScale = 0.5f) + fadeIn(tween(200)),
                exit = scaleOut(tween(400), targetScale = 1.5f) + fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = showStreetOverlay?.name?.replace("_", " ") ?: "",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.9f),
                    letterSpacing = 4.sp,
                    style = LocalTextStyle.current.copy(shadow = Shadow(color = Emerald400, blurRadius = 24f))
                )
            }
        }
    }

    // --- Dialogs ---"""
code = code.replace(target_app_box_end, replacement_app_box_end)

# PlayerCardCompact Parameters
target_playercard_call = """                        PlayerCardCompact(
                            modifier = Modifier.weight(1f),
                            player = player,
                            isActive = (player.id == state.activePlayerId && !player.isFolded && !state.isSettlementPhase),
                            canRaise = canRaiseThisTurn,"""

replacement_playercard_call = """                        val firstToShowId = if (state.isSettlementPhase) {
                            val active = state.players.filter { !it.isFolded && it.isInHand }
                            val lastAgg = active.find { it.id == state.lastAggressorIdOverall }
                            if (lastAgg != null) {
                                lastAgg.id
                            } else {
                                var first = -1
                                var idx = (state.dealerIdx + 1) % state.players.size
                                for (i in state.players.indices) {
                                    val p = state.players[idx]
                                    if (!p.isFolded && p.isInHand) {
                                        first = p.id
                                        break
                                    }
                                    idx = (idx + 1) % state.players.size
                                }
                                first
                            }
                        } else -1

                        PlayerCardCompact(
                            modifier = Modifier.weight(1f),
                            player = player,
                            isActive = (player.id == state.activePlayerId && !player.isFolded && !state.isSettlementPhase),
                            canRaise = canRaiseThisTurn,
                            bbAmount = state.bbAmount,
                            isFirstToShow = (player.id == firstToShowId),"""
code = code.replace(target_playercard_call, replacement_playercard_call)

target_playercard_def = """fun PlayerCardCompact(
    modifier: Modifier = Modifier,
    player: Player,
    isActive: Boolean,
    canRaise: Boolean,
    highestBet: Int,
    targetC: Int,
    targetMinBet: Int,
    targetMaxBet: Int,
    isDealer: Boolean,
    isSb: Boolean,
    isBb: Boolean,
    isRaiser: Boolean,
    onRenameClick: () -> Unit,"""

replacement_playercard_def = """fun PlayerCardCompact(
    modifier: Modifier = Modifier,
    player: Player,
    isActive: Boolean,
    canRaise: Boolean,
    highestBet: Int,
    targetC: Int,
    targetMinBet: Int,
    targetMaxBet: Int,
    bbAmount: Int,
    isFirstToShow: Boolean,
    isDealer: Boolean,
    isSb: Boolean,
    isBb: Boolean,
    isRaiser: Boolean,
    onRenameClick: () -> Unit,"""
code = code.replace(target_playercard_def, replacement_playercard_def)

target_playercard_tags = """                                if (isRaiser) {
                                    Text(" RAISE ", modifier = Modifier.background(FoldRed, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$${player.stack.fmt()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Emerald400,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.width(6.dp))"""

replacement_playercard_tags = """                                if (isRaiser) {
                                    Text(" RAISE ", modifier = Modifier.background(FoldRed, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (isFirstToShow) {
                                    Text(" [SHOW FIRST] ", modifier = Modifier.background(CallBlue, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$${player.stack.fmt()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Emerald400,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.width(4.dp))
                                val bbCount = if (bbAmount > 0) player.stack / bbAmount else 0
                                Text("($bbCount BB)", fontSize = 10.sp, color = Color.Gray)
                                Spacer(Modifier.width(6.dp))"""
code = code.replace(target_playercard_tags, replacement_playercard_tags)

# PnLScreen Fixes
target_pnl_net = """                                        text = "净: $sign${result.netProfit.fmt()}",
                                        fontSize = 14.sp,"""
replacement_pnl_net = """                                        text = "$sign${result.netProfit.fmt()}",
                                        fontSize = 14.sp,"""
code = code.replace(target_pnl_net, replacement_pnl_net)

target_pnl_end = """                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PotGold, contentColor = CasinoBgDark)
                ) {
                    Text("NEXT HAND", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}"""
replacement_pnl_end = """                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PotGold, contentColor = CasinoBgDark)
                ) {
                    Text("NEXT HAND", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
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
}"""
code = code.replace(target_pnl_end, replacement_pnl_end)

# FinalSettlementScreen Kills Display
target_final_hands = """                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(player.name, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${player.handsPlayed} Hands", fontSize = 12.sp, color = Color.White.copy(0.5f))
                                }"""
replacement_final_hands = """                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(player.name, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${player.handsPlayed} Hands | ${player.kills} Kills", fontSize = 12.sp, color = Color.White.copy(0.5f))
                                }"""
code = code.replace(target_final_hands, replacement_final_hands)

# SettlePotDialog Winner UI Update
target_settle_high = """                                Text("High Winner(s):", fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f), fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    eligibleList.forEach { p ->
                                        val isSelected = settlement.highWinners.contains(p.id)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val isAdding = !isSelected
                                                val newHighs = if (isAdding) settlement.highWinners + p.id else settlement.highWinners - p.id
                                                var updatedSettlements = potSettlements + (pot.id to settlement.copy(highWinners = newHighs))

                                                if (isAdding) {
                                                    for (otherPot in pots) {
                                                        if (otherPot.id > pot.id && otherPot.eligiblePlayerIds.contains(p.id)) {
                                                            val otherSettle = updatedSettlements[otherPot.id]!!
                                                            updatedSettlements = updatedSettlements + (otherPot.id to otherSettle.copy(highWinners = otherSettle.highWinners + p.id))
                                                        }
                                                    }
                                                }
                                                potSettlements = updatedSettlements
                                            },
                                            label = { Text(p.name, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = GlassWhite,
                                                selectedContainerColor = Emerald500.copy(0.2f),
                                                selectedLabelColor = Emerald400
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                borderColor = Color.Transparent,
                                                selectedBorderColor = Emerald500,
                                                enabled = true, selected = isSelected
                                            )
                                        )
                                    }
                                }"""

replacement_settle_high = """                                Text("High Winner(s):", fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f), fontSize = 12.sp)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    eligibleList.forEach { p ->
                                        val isSelected = settlement.highWinners.contains(p.id)
                                        val containerColor = if (isSelected) Emerald500.copy(0.2f) else GlassWhite
                                        val borderColor = if (isSelected) Emerald500 else Color.Transparent
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp)
                                                .background(containerColor, RoundedCornerShape(12.dp))
                                                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val isAdding = !isSelected
                                                    val newHighs = if (isAdding) settlement.highWinners + p.id else settlement.highWinners - p.id
                                                    var updatedSettlements = potSettlements + (pot.id to settlement.copy(highWinners = newHighs))

                                                    if (isAdding) {
                                                        for (otherPot in pots) {
                                                            if (otherPot.id > pot.id && otherPot.eligiblePlayerIds.contains(p.id)) {
                                                                val otherSettle = updatedSettlements[otherPot.id]!!
                                                                updatedSettlements = updatedSettlements + (otherPot.id to otherSettle.copy(highWinners = otherSettle.highWinners + p.id))
                                                            }
                                                        }
                                                    }
                                                    potSettlements = updatedSettlements
                                                }
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(p.name, fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (isSelected) Emerald400 else Color.White)
                                                Text("(Stack: ${p.stack.fmt()})", fontSize = 14.sp, color = Color.White.copy(0.6f))
                                            }
                                        }
                                    }
                                }"""
code = code.replace(target_settle_high, replacement_settle_high)

target_settle_low = """                                if (state.isOmahaHiLo) {
                                    Text("Low Winner(s):", fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f), fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        eligibleList.forEach { p ->
                                            val isSelected = settlement.lowWinners.contains(p.id)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val isAdding = !isSelected
                                                    val newLows = if (isAdding) settlement.lowWinners + p.id else settlement.lowWinners - p.id
                                                    var updatedSettlements = potSettlements + (pot.id to settlement.copy(lowWinners = newLows))

                                                    if (isAdding) {
                                                        for (otherPot in pots) {
                                                            if (otherPot.id > pot.id && otherPot.eligiblePlayerIds.contains(p.id)) {
                                                                val otherSettle = updatedSettlements[otherPot.id]!!
                                                                updatedSettlements = updatedSettlements + (otherPot.id to otherSettle.copy(lowWinners = otherSettle.lowWinners + p.id))
                                                            }
                                                        }
                                                    }
                                                    potSettlements = updatedSettlements
                                                },
                                                label = { Text(p.name, fontWeight = FontWeight.Bold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = GlassWhite,
                                                    selectedContainerColor = CallBlue.copy(0.2f),
                                                    selectedLabelColor = CallBlue
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    borderColor = Color.Transparent,
                                                    selectedBorderColor = CallBlue,
                                                    enabled = true, selected = isSelected
                                                )
                                            )
                                        }
                                    }
                                }"""

replacement_settle_low = """                                if (state.isOmahaHiLo) {
                                    Text("Low Winner(s):", fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f), fontSize = 12.sp)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        eligibleList.forEach { p ->
                                            val isSelected = settlement.lowWinners.contains(p.id)
                                            val containerColor = if (isSelected) CallBlue.copy(0.2f) else GlassWhite
                                            val borderColor = if (isSelected) CallBlue else Color.Transparent
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(60.dp)
                                                    .background(containerColor, RoundedCornerShape(12.dp))
                                                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val isAdding = !isSelected
                                                        val newLows = if (isAdding) settlement.lowWinners + p.id else settlement.lowWinners - p.id
                                                        var updatedSettlements = potSettlements + (pot.id to settlement.copy(lowWinners = newLows))

                                                        if (isAdding) {
                                                            for (otherPot in pots) {
                                                                if (otherPot.id > pot.id && otherPot.eligiblePlayerIds.contains(p.id)) {
                                                                    val otherSettle = updatedSettlements[otherPot.id]!!
                                                                    updatedSettlements = updatedSettlements + (otherPot.id to otherSettle.copy(lowWinners = otherSettle.lowWinners + p.id))
                                                                }
                                                            }
                                                        }
                                                        potSettlements = updatedSettlements
                                                    }
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(p.name, fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (isSelected) CallBlue else Color.White)
                                                    Text("(Stack: ${p.stack.fmt()})", fontSize = 14.sp, color = Color.White.copy(0.6f))
                                                }
                                            }
                                        }
                                    }
                                }"""
code = code.replace(target_settle_low, replacement_settle_low)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)

