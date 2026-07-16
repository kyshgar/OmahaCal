package com.example.omahacalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.content.Context
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.view.WindowManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.util.Locale

fun Int.fmt(): String = NumberFormat.getNumberInstance(Locale.US).format(this)

// --- Preferences & Persistence ---
enum class LogType { INFO, FOLD, CALL, RAISE, POT }
data class LogEntry(val text: String, val type: LogType)

fun triggerVibration(context: Context, intensity: Int) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        when (intensity) {
            0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(20)
                }
            }
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
            2 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, 255))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
            }
        }
    } catch (e: Exception) {}
}

data class SetupConfig(
    val count: Int,
    val isHiLo: Boolean,
    val players: List<Player>?,
    val sbAmount: Int,
    val bbAmount: Int,
    val defaultBuyIn: Int,
    val quickAddChips: List<Int>,
    val biggestPot: Int
)

object Prefs {
    fun saveSetup(context: Context, state: GameState) {
        val prefs = context.getSharedPreferences("OmahaPrefs", Context.MODE_PRIVATE)
        val allPlayersToSave = state.playerPool.map { p -> state.players.find { it.id == p.id } ?: p }
        val playersStr = allPlayersToSave.joinToString(";") { "${it.id},${it.name},${it.stack},${it.totalBuyIn},${it.handsPlayed},${it.vpipCount},${it.pfrCount},${it.agqCount},${it.totalRebuyAmount},${it.sawFlopCount},${it.wentToShowdownCount},${it.wonAtShowdownCount}" }
        prefs.edit()
            .putString("players", playersStr)
            .putInt("playerCount", state.playerCount)
            .putBoolean("isOmahaHiLo", state.isOmahaHiLo)
            .putInt("sbAmount", state.sbAmount)
            .putInt("bbAmount", state.bbAmount)
            .putInt("defaultBuyIn", state.defaultBuyIn)
            .putString("quickAddChips", state.quickAddChips.joinToString(","))
            .putInt("biggestPot", state.biggestPot)
            .apply()
    }

    fun loadSetup(context: Context, onLoaded: (SetupConfig) -> Unit) {
        val prefs = context.getSharedPreferences("OmahaPrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("playerCount", 4)
        val isHiLo = prefs.getBoolean("isOmahaHiLo", false)
        val sbAmount = prefs.getInt("sbAmount", 10)
        val bbAmount = prefs.getInt("bbAmount", 20)
        val defaultBuyIn = prefs.getInt("defaultBuyIn", 1000)
        val chipsStr = prefs.getString("quickAddChips", "10,20,100,500") ?: "10,20,100,500"
        val chips = chipsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.take(8)
        val finalChips = if (chips.isEmpty()) listOf(10, 20, 100, 500) else chips
        val biggestPot = prefs.getInt("biggestPot", 0)

        val playersStr = prefs.getString("players", null)

        var players: List<Player>? = null
        if (playersStr != null) {
            val pList = mutableListOf<Player>()
            try {
                playersStr.split(";").forEach {
                    val parts = it.split(",")
                    val tbi = if (parts.size >= 4) parts[3].toInt() else 1000
                    val hp = if (parts.size >= 5) parts[4].toInt() else 0
                    val vpip = if (parts.size >= 6) parts[5].toInt() else 0
                    val pfr = if (parts.size >= 7) parts[6].toInt() else 0
                    val agq = if (parts.size >= 8) parts[7].toInt() else 0
                    val rebuy = if (parts.size >= 9) parts[8].toInt() else 0
                    val sf = if (parts.size >= 10) parts[9].toInt() else 0
                    val wtsd = if (parts.size >= 11) parts[10].toInt() else 0
                    val wmsd = if (parts.size >= 12) parts[11].toInt() else 0
                    pList.add(Player(id = parts[0].toInt(), name = parts[1], stack = parts[2].toInt(), totalBuyIn = tbi, totalRebuyAmount = rebuy, handsPlayed = hp, vpipCount = vpip, pfrCount = pfr, agqCount = agq, sawFlopCount = sf, wentToShowdownCount = wtsd, wonAtShowdownCount = wmsd))
                }
                players = pList
            } catch (e: Exception) {}
        }
        onLoaded(SetupConfig(count, isHiLo, players, sbAmount, bbAmount, defaultBuyIn, finalChips, biggestPot))
    }
}

enum class Street { PRE_FLOP, FLOP, TURN, RIVER }

data class Player(
    val id: Int,
    val name: String,
    val stack: Int = 1000,
    val totalBuyIn: Int = 1000,
    val totalRebuyAmount: Int = 0,
    val currentBet: Int = 0,
    val totalInvested: Int = 0,
    val isFolded: Boolean = false,
    val handsPlayed: Int = 0,
    val vpipCount: Int = 0,
    val pfrCount: Int = 0,
    val agqCount: Int = 0,
    val sawFlopCount: Int = 0,
    val wentToShowdownCount: Int = 0,
    val wonAtShowdownCount: Int = 0,
    val hasVpipThisHand: Boolean = false,
    val hasPfrThisHand: Boolean = false,
    val hasAgqThisHand: Boolean = false,
    val hasSawFlopThisHand: Boolean = false,
    val kills: Int = 0,
    val isInHand: Boolean = true,
    val lastActedOnFullRaiseAmount: Int = -1
)

data class PnLResult(
    val playerId: Int,
    val name: String,
    val netProfit: Int,
    val payout: Int
)

data class Pot(
    val id: Int,
    val name: String,
    val amount: Int,
    val eligiblePlayerIds: Set<Int>
)

data class PotSettlement(
    val potId: Int,
    val highWinners: Set<Int>,
    val lowWinners: Set<Int>
)

data class GameState(
    val isGameStarted: Boolean = false,
    val playerCount: Int = 4,
    val isOmahaHiLo: Boolean = false,
    val sbAmount: Int = 10,
    val bbAmount: Int = 20,
    val defaultBuyIn: Int = 1000,
    val quickAddChips: List<Int> = listOf(10, 20, 100, 500),
    val playerPool: List<Player> = emptyList(),
    val players: List<Player> = emptyList(),
    val mainPot: Int = 0,
    val highestBet: Int = 0,
    val lastRaiseDelta: Int = 20,
    val activePlayerId: Int = 1,
    val settlementResults: List<PnLResult>? = null,
    val street: Street = Street.PRE_FLOP,
    val dealerIdx: Int = 0,
    val lastAggressorId: Int = -1,
    val lastAggressorIdOverall: Int = -1,
    val actedPlayerIdsThisStreet: Set<Int> = emptySet(),
    val logs: List<LogEntry> = emptyList(),
    val isSettlementPhase: Boolean = false,
    val isEndGameActivated: Boolean = false,
    val biggestPot: Int = 0,
    val lastFullRaiseAmount: Int = 0
)

// --- ViewModel ---
class GameViewModel : ViewModel() {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val history = mutableListOf<GameState>()

    private fun saveHistory() {
        history.add(_state.value)
    }

    fun undo() {
        if (history.isNotEmpty()) {
            _state.value = history.removeAt(history.lastIndex)
        }
    }

    fun initSetup(context: Context) {
        Prefs.loadSetup(context) { config ->
            val count = config.count
            val loadedPlayers = config.players ?: emptyList()

            val pool = MutableList(9) { index ->
                val id = index + 1
                val existing = loadedPlayers.find { it.id == id }
                existing ?: Player(id = id, name = when (index) { 0 -> "SB"; 1 -> "BB"; else -> "P$id" }, stack = config.defaultBuyIn, totalBuyIn = config.defaultBuyIn)
            }

            _state.value = _state.value.copy(
                playerCount = count,
                isOmahaHiLo = config.isHiLo,
                sbAmount = config.sbAmount,
                bbAmount = config.bbAmount,
                defaultBuyIn = config.defaultBuyIn,
                quickAddChips = config.quickAddChips,
                playerPool = pool,
                players = pool.take(count),
                biggestPot = config.biggestPot
            )
        }
    }

    fun clearAllChips(context: Context) {
        val newPool = _state.value.playerPool.map { it.copy(stack = _state.value.defaultBuyIn, totalBuyIn = _state.value.defaultBuyIn, totalRebuyAmount = 0) }
        val newPlayers = _state.value.players.map { it.copy(stack = _state.value.defaultBuyIn, totalBuyIn = _state.value.defaultBuyIn, totalRebuyAmount = 0) }
        _state.value = _state.value.copy(playerPool = newPool, players = newPlayers)
        Prefs.saveSetup(context, _state.value)
    }

    fun updateSettings(sb: Int, bb: Int, defaultBuyIn: Int, quickAddChips: List<Int>, context: Context) {
        _state.value = _state.value.copy(sbAmount = sb, bbAmount = bb, defaultBuyIn = defaultBuyIn, quickAddChips = quickAddChips)
        Prefs.saveSetup(context, _state.value)
    }

    fun updateSetupCount(count: Int, context: Context) {
        if (count == _state.value.playerCount) return
        val newPlayers = _state.value.playerPool.take(count)
        _state.value = _state.value.copy(playerCount = count, players = newPlayers)
        Prefs.saveSetup(context, _state.value)
    }

    fun updateSetupHiLo(isHiLo: Boolean, context: Context) {
        _state.value = _state.value.copy(isOmahaHiLo = isHiLo)
        Prefs.saveSetup(context, _state.value)
    }

    fun renameSetupPlayer(id: Int, newName: String, context: Context) {
        if (newName.isBlank()) return
        val newPool = _state.value.playerPool.map { if (it.id == id) it.copy(name = newName) else it }
        val newPlayers = _state.value.players.map { if (it.id == id) it.copy(name = newName) else it }
        _state.value = _state.value.copy(playerPool = newPool, players = newPlayers)
        Prefs.saveSetup(context, _state.value)
    }

    fun updateSetupPlayerStack(id: Int, newStack: Int, context: Context) {
        val newPool = _state.value.playerPool.map {
            if (it.id == id) {
                val diff = newStack - it.stack
                val newRebuy = if (diff > 0) it.totalRebuyAmount + diff else it.totalRebuyAmount
                it.copy(stack = newStack, totalBuyIn = it.totalBuyIn + diff, totalRebuyAmount = newRebuy)
            } else it
        }
        val newPlayers = _state.value.players.map {
            if (it.id == id) {
                val diff = newStack - it.stack
                val newRebuy = if (diff > 0) it.totalRebuyAmount + diff else it.totalRebuyAmount
                it.copy(stack = newStack, totalBuyIn = it.totalBuyIn + diff, totalRebuyAmount = newRebuy)
            } else it
        }
        _state.value = _state.value.copy(playerPool = newPool, players = newPlayers)
        Prefs.saveSetup(context, _state.value)
    }

    fun rebuyPlayer(id: Int, amount: Int, context: Context) {
        saveHistory()
        val players = _state.value.players.map {
            if (it.id == id) it.copy(stack = it.stack + amount, totalBuyIn = it.totalBuyIn + amount, totalRebuyAmount = it.totalRebuyAmount + amount) else it
        }
        _state.value = _state.value.copy(players = players, logs = _state.value.logs + LogEntry("${players.find { it.id == id }?.name} rebuys ${amount.fmt()}", LogType.INFO))
        Prefs.saveSetup(context, _state.value)
    }

    fun chopBlinds(context: Context) {
        val players = _state.value.players.map { p ->
            p.copy(
                stack = p.stack + p.totalInvested,
                totalInvested = 0,
                currentBet = 0,
                handsPlayed = (p.handsPlayed - 1).coerceAtLeast(0)
            )
        }
        _state.value = _state.value.copy(players = players, mainPot = 0)

        val nextDealer = (_state.value.dealerIdx + 1) % _state.value.playerCount
        startHand(context, dealerIdx = nextDealer, logs = _state.value.logs + LogEntry("🤝 Blinds Chopped.", LogType.INFO))
    }

    fun movePlayer(index: Int, direction: Int, context: Context) {
        val list = _state.value.players.toMutableList()
        val targetIndex = index + direction
        if (targetIndex in list.indices) {
            val temp = list[index]
            list[index] = list[targetIndex]
            list[targetIndex] = temp

            val updatedList = list.mapIndexed { idx, player ->
                player.copy(id = idx + 1)
            }

            val newPool = updatedList.toMutableList()
            for (i in updatedList.size until _state.value.playerPool.size) {
                newPool.add(_state.value.playerPool[i].copy(id = i + 1))
            }

            _state.value = _state.value.copy(players = updatedList, playerPool = newPool)
            Prefs.saveSetup(context, _state.value)
        }
    }

    fun startGame(context: Context) {
        startHand(context, dealerIdx = 0, logs = listOf(LogEntry("Game Started", LogType.INFO)))
    }

    fun startNextHand(context: Context) {
        val nextDealer = (_state.value.dealerIdx + 1) % _state.value.playerCount
        startHand(context, dealerIdx = nextDealer, logs = _state.value.logs + LogEntry("--- NEW HAND ---", LogType.INFO))
    }

    private fun startHand(context: Context, dealerIdx: Int, logs: List<LogEntry>) {
        val currentPlayers = _state.value.players

        val activeIds = currentPlayers.filter { it.stack > 0 }.map { it.id }
        if (activeIds.size < 2) return

        val isHeadsUp = activeIds.size == 2

        var dIdx = dealerIdx % currentPlayers.size
        while (currentPlayers[dIdx].stack <= 0) dIdx = (dIdx + 1) % currentPlayers.size

        var sbIdx: Int
        var bbIdx: Int
        if (isHeadsUp) {
            sbIdx = dIdx
            bbIdx = (dIdx + 1) % currentPlayers.size
            while (currentPlayers[bbIdx].stack <= 0) bbIdx = (bbIdx + 1) % currentPlayers.size
        } else {
            sbIdx = (dIdx + 1) % currentPlayers.size
            while (currentPlayers[sbIdx].stack <= 0) sbIdx = (sbIdx + 1) % currentPlayers.size
            bbIdx = (sbIdx + 1) % currentPlayers.size
            while (currentPlayers[bbIdx].stack <= 0) bbIdx = (bbIdx + 1) % currentPlayers.size
        }

        val sbAmount = _state.value.sbAmount
        val bbAmount = _state.value.bbAmount

        val players = currentPlayers.mapIndexed { index, p ->
            val isInHand = p.stack > 0
            val hp = if (isInHand) p.handsPlayed + 1 else p.handsPlayed
            when (index) {
                sbIdx -> p.copy(stack = (p.stack - sbAmount).coerceAtLeast(0), currentBet = sbAmount.coerceAtMost(p.stack), totalInvested = sbAmount.coerceAtMost(p.stack), isFolded = false, handsPlayed = hp, hasVpipThisHand = false, hasPfrThisHand = false, hasAgqThisHand = false, hasSawFlopThisHand = false, isInHand = isInHand, lastActedOnFullRaiseAmount = -1)
                bbIdx -> p.copy(stack = (p.stack - bbAmount).coerceAtLeast(0), currentBet = bbAmount.coerceAtMost(p.stack), totalInvested = bbAmount.coerceAtMost(p.stack), isFolded = false, handsPlayed = hp, hasVpipThisHand = false, hasPfrThisHand = false, hasAgqThisHand = false, hasSawFlopThisHand = false, isInHand = isInHand, lastActedOnFullRaiseAmount = -1)
                else -> p.copy(currentBet = 0, totalInvested = 0, isFolded = false, handsPlayed = hp, hasVpipThisHand = false, hasPfrThisHand = false, hasAgqThisHand = false, hasSawFlopThisHand = false, isInHand = isInHand, lastActedOnFullRaiseAmount = -1)
            }
        }
        var firstActive = if (isHeadsUp) sbIdx else (bbIdx + 1) % players.size
        while (!players[firstActive].isInHand) {
            firstActive = (firstActive + 1) % players.size
        }

        _state.value = _state.value.copy(
            isGameStarted = true,
            players = players,
            mainPot = 0,
            highestBet = bbAmount,
            lastRaiseDelta = bbAmount,
            lastFullRaiseAmount = bbAmount,
            activePlayerId = players[firstActive].id,
            settlementResults = null,
            street = Street.PRE_FLOP,
            dealerIdx = dIdx,
            lastAggressorId = -1,
            lastAggressorIdOverall = -1,
            actedPlayerIdsThisStreet = emptySet(),
            logs = logs,
            isSettlementPhase = false
        )
        Prefs.saveSetup(context, _state.value)
        history.clear()

        val activeP = _state.value.players.find { it.id == _state.value.activePlayerId }
        if (activeP != null && activeP.stack == 0) {
            advanceActivePlayer()
        }
    }

    private fun checkAutoAdvance() {
        val s = _state.value
        val activePlayers = s.players.filter { !it.isFolded && it.isInHand }

        if (activePlayers.size <= 1) {
            _state.value = s.copy(
                isSettlementPhase = true,
                logs = s.logs + LogEntry("All others folded. Proceed to settle.", LogType.INFO)
            )
            return
        }

        val allMatchedOrAllIn = activePlayers.all { it.currentBet == s.highestBet || it.stack == 0 }
        val allActed = activePlayers.all { s.actedPlayerIdsThisStreet.contains(it.id) || it.stack == 0 }

        if (allMatchedOrAllIn && allActed) {
            advanceStreet()
        } else {
            advanceActivePlayer()
        }
    }

    private fun advanceActivePlayer() {
        val players = _state.value.players
        val currentIdx = players.indexOfFirst { it.id == _state.value.activePlayerId }
        if (currentIdx == -1) return

        var nextIdx = (currentIdx + 1) % players.size
        var loopCount = 0
        while ((players[nextIdx].isFolded || players[nextIdx].stack == 0 || !players[nextIdx].isInHand) && loopCount < players.size) {
            nextIdx = (nextIdx + 1) % players.size
            loopCount++
        }

        if (loopCount == players.size) {
            advanceStreet()
            return
        }

        _state.value = _state.value.copy(activePlayerId = players[nextIdx].id)
    }

    private fun advanceStreet() {
        val s = _state.value
        val sumOfBets = s.players.sumOf { it.currentBet }
        val players = s.players.map { it.copy(currentBet = 0, lastActedOnFullRaiseAmount = -1) }
        val newMainPot = s.mainPot + sumOfBets

        if (s.street == Street.RIVER || s.players.count { !it.isFolded && it.isInHand && it.stack > 0 } <= 1) {
            _state.value = s.copy(
                players = players,
                mainPot = newMainPot,
                highestBet = 0,
                lastRaiseDelta = s.bbAmount,
                lastFullRaiseAmount = 0,
                isSettlementPhase = true,
                logs = s.logs + LogEntry("Moving to Settlement...", LogType.INFO)
            )
        } else {
            val nextStreet = Street.values()[s.street.ordinal + 1]
            val isFlop = nextStreet == Street.FLOP
            
            val finalPlayers = players.map { p ->
                if (isFlop && !p.isFolded && p.isInHand) {
                    p.copy(sawFlopCount = p.sawFlopCount + 1, hasSawFlopThisHand = true)
                } else p
            }

            var nextActiveIdx = (s.dealerIdx + 1) % finalPlayers.size
            var loopCount = 0
            while ((finalPlayers[nextActiveIdx].isFolded || finalPlayers[nextActiveIdx].stack == 0 || !finalPlayers[nextActiveIdx].isInHand) && loopCount < finalPlayers.size) {
                nextActiveIdx = (nextActiveIdx + 1) % finalPlayers.size
                loopCount++
            }

            _state.value = s.copy(
                players = finalPlayers,
                mainPot = newMainPot,
                highestBet = 0,
                lastRaiseDelta = s.bbAmount,
                lastFullRaiseAmount = 0,
                street = nextStreet,
                actedPlayerIdsThisStreet = emptySet(),
                lastAggressorId = -1,
                activePlayerId = finalPlayers[nextActiveIdx].id,
                logs = s.logs + LogEntry("--- ${nextStreet.name} (Pot: ${newMainPot.fmt()}) ---", LogType.INFO)
            )
        }
    }

    fun foldPlayer(playerId: Int) {
        saveHistory()
        val currentFullRaise = _state.value.lastFullRaiseAmount
        val players = _state.value.players.map {
            if (it.id == playerId) it.copy(isFolded = true, lastActedOnFullRaiseAmount = currentFullRaise) else it
        }
        val p = players.find { it.id == playerId }
        val logs = _state.value.logs + LogEntry("${p?.name} Folds", LogType.FOLD)

        _state.value = _state.value.copy(players = players, logs = logs, actedPlayerIdsThisStreet = _state.value.actedPlayerIdsThisStreet + playerId)
        checkAutoAdvance()
    }

    fun callPlayer(playerId: Int) {
        saveHistory()
        val highestBet = _state.value.highestBet
        val currentFullRaise = _state.value.lastFullRaiseAmount
        val players = _state.value.players.map {
            if (it.id == playerId) {
                val diff = highestBet - it.currentBet
                val actualDiff = diff.coerceAtMost(it.stack)
                val isVpip = actualDiff > 0
                val newVpip = if (isVpip && !it.hasVpipThisHand) it.vpipCount + 1 else it.vpipCount
                val newHasVpip = it.hasVpipThisHand || isVpip
                it.copy(
                    currentBet = it.currentBet + actualDiff,
                    totalInvested = it.totalInvested + actualDiff,
                    stack = it.stack - actualDiff,
                    vpipCount = newVpip,
                    hasVpipThisHand = newHasVpip,
                    lastActedOnFullRaiseAmount = currentFullRaise
                )
            } else it
        }
        val p = players.find { it.id == playerId }

        val highestBetBefore = _state.value.highestBet
        val currentBetBefore = _state.value.players.find { it.id == playerId }?.currentBet ?: 0
        val diff = highestBetBefore - currentBetBefore

        val actionStr = if (diff == 0) "Checks" else if (p?.currentBet == highestBetBefore) "Calls ${highestBetBefore.fmt()}" else "All-In"
        val logs = _state.value.logs + LogEntry("${p?.name} $actionStr", LogType.CALL)

        _state.value = _state.value.copy(players = players, logs = logs, actedPlayerIdsThisStreet = _state.value.actedPlayerIdsThisStreet + playerId)
        checkAutoAdvance()
    }

    fun potPlayer(playerId: Int) {
        val highestBet = _state.value.highestBet
        val mainPot = _state.value.mainPot
        val sumOfBets = _state.value.players.sumOf { it.currentBet }
        val player = _state.value.players.find { it.id == playerId } ?: return

        val newBet = sumOfBets + mainPot + 2 * highestBet - player.currentBet
        customBetPlayer(playerId, newBet, isPot = true)
    }

    fun minRaisePlayer(playerId: Int) {
        val s = _state.value
        val player = s.players.find { it.id == playerId } ?: return
        val callAmount = (s.highestBet - player.currentBet).coerceAtLeast(0)
        val currentTotalPot = s.mainPot + s.players.sumOf { it.currentBet }
        val maxRaise = currentTotalPot + callAmount
        val actualMaxBetTotal = (s.highestBet + maxRaise).coerceAtMost(player.currentBet + player.stack)
        val minBetTotal = (s.highestBet + s.lastRaiseDelta).coerceAtMost(actualMaxBetTotal)
        customBetPlayer(playerId, minBetTotal, isPot = false)
    }

    fun customBetPlayer(playerId: Int, betAmount: Int, isPot: Boolean = false) {
        saveHistory()
        val highestBet = _state.value.highestBet

        val actualBetAmountVal = _state.value.players.find { it.id == playerId }?.currentBet?.let { currentBet ->
            val diff = betAmount - currentBet
            currentBet + diff.coerceAtMost(_state.value.players.find { it.id == playerId }?.stack ?: 0)
        } ?: betAmount

        val isRaise = actualBetAmountVal > highestBet
        val raiseDelta = actualBetAmountVal - highestBet
        val isFullRaise = isRaise && raiseDelta >= _state.value.lastRaiseDelta
        val newFullRaiseAmount = if (isFullRaise) actualBetAmountVal else _state.value.lastFullRaiseAmount

        val players = _state.value.players.map {
            if (it.id == playerId) {
                val diff = betAmount - it.currentBet
                val actualDiff = diff.coerceAtMost(it.stack)

                val isVpip = actualDiff > 0
                val newVpip = if (isVpip && !it.hasVpipThisHand) it.vpipCount + 1 else it.vpipCount
                val newHasVpip = it.hasVpipThisHand || isVpip

                val actualBetAmount = it.currentBet + actualDiff
                val isPreFlop = _state.value.street == Street.PRE_FLOP
                val isAggressive = actualBetAmount > highestBet || (actualBetAmount > 0 && highestBet == 0)
                val isPreFlopRaise = isPreFlop && isAggressive
                
                val newPfr = if (isPreFlopRaise && !it.hasPfrThisHand) it.pfrCount + 1 else it.pfrCount
                val newHasPfr = it.hasPfrThisHand || isPreFlopRaise
                
                val newAgq = if (isAggressive && !it.hasAgqThisHand) it.agqCount + 1 else it.agqCount
                val newHasAgq = it.hasAgqThisHand || isAggressive

                it.copy(
                    currentBet = actualBetAmount,
                    totalInvested = it.totalInvested + actualDiff,
                    stack = it.stack - actualDiff,
                    vpipCount = newVpip,
                    hasVpipThisHand = newHasVpip,
                    pfrCount = newPfr,
                    hasPfrThisHand = newHasPfr,
                    agqCount = newAgq,
                    hasAgqThisHand = newHasAgq,
                    lastActedOnFullRaiseAmount = newFullRaiseAmount
                )
            } else it
        }
        val p = players.find { it.id == playerId }
        val actualBetAmount = p?.currentBet ?: betAmount

        val newHighest = actualBetAmount.coerceAtLeast(highestBet)
        val newRaiseDelta = if (isFullRaise) raiseDelta else _state.value.lastRaiseDelta

        val logType = if (isPot) LogType.POT else if (isRaise) LogType.RAISE else LogType.CALL
        val logMsg = if (isRaise) "${p?.name} Raises to ${actualBetAmount.fmt()}" else "${p?.name} Bets ${actualBetAmount.fmt()}"
        val logs = _state.value.logs + LogEntry(logMsg, logType)

        _state.value = _state.value.copy(
            players = players,
            highestBet = newHighest,
            lastRaiseDelta = newRaiseDelta,
            lastFullRaiseAmount = newFullRaiseAmount,
            lastAggressorId = if (isFullRaise) playerId else _state.value.lastAggressorId,
            lastAggressorIdOverall = if (isRaise) playerId else _state.value.lastAggressorIdOverall,
            logs = logs,
            actedPlayerIdsThisStreet = _state.value.actedPlayerIdsThisStreet + playerId
        )
        checkAutoAdvance()
    }

    fun calculatePots(): List<Pot> {
        val s = _state.value
        val inHandPlayers = s.players.filter { it.isInHand }
        val allInvestedLevels = inHandPlayers.map { it.totalInvested }.filter { it > 0 }.distinct().sorted()
        val pots = mutableListOf<Pot>()
        var previousLevel = 0
        var potId = 0

        for (level in allInvestedLevels) {
            val amountPerPlayer = level - previousLevel
            var currentPotAmount = 0
            val eligiblePlayers = mutableSetOf<Int>()

            for (p in inHandPlayers) {
                if (p.totalInvested > previousLevel) {
                    val contribution = (p.totalInvested - previousLevel).coerceAtMost(amountPerPlayer)
                    currentPotAmount += contribution
                    if (!p.isFolded && p.totalInvested >= level) {
                        eligiblePlayers.add(p.id)
                    }
                }
            }

            if (currentPotAmount > 0) {
                if (eligiblePlayers.isEmpty() && pots.isNotEmpty()) {
                    val last = pots.removeAt(pots.lastIndex)
                    pots.add(last.copy(amount = last.amount + currentPotAmount))
                } else {
                    val name = if (potId == 0) "Main Pot" else "Side Pot $potId"
                    if (pots.isNotEmpty() && pots.last().eligiblePlayerIds == eligiblePlayers) {
                        val last = pots.removeAt(pots.lastIndex)
                        pots.add(last.copy(amount = last.amount + currentPotAmount))
                    } else {
                        pots.add(Pot(potId++, name, currentPotAmount, eligiblePlayers))
                    }
                }
            }
            previousLevel = level
        }

        val finalActive = inHandPlayers.filter { !it.isFolded }.map { it.id }.toSet()
        return pots.map { pot ->
            if (pot.eligiblePlayerIds.isEmpty()) {
                pot.copy(eligiblePlayerIds = if (finalActive.isNotEmpty()) finalActive else inHandPlayers.map { it.id }.toSet())
            } else {
                pot
            }
        }
    }

    fun settlePots(settlements: List<PotSettlement>) {
        saveHistory()
        val s = _state.value
        val pots = calculatePots()
        val totalPotAmount = pots.sumOf { it.amount }
        val newBiggestPot = maxOf(s.biggestPot, totalPotAmount)
        val payouts = mutableMapOf<Int, Int>()

        val sortedByPosition = { ids: Set<Int> ->
            ids.toList().sortedBy { id ->
                val idx = s.players.indexOfFirst { it.id == id }
                val distance = (idx - s.dealerIdx + s.players.size) % s.players.size
                if (distance == 0) s.players.size else distance
            }
        }

        for (pot in pots) {
            val settlement = settlements.find { it.potId == pot.id } ?: continue
            val highWinners = pot.eligiblePlayerIds.intersect(settlement.highWinners)
            val lowWinners = pot.eligiblePlayerIds.intersect(settlement.lowWinners)

            val sortedHigh = sortedByPosition(highWinners)
            val sortedLow = sortedByPosition(lowWinners)
            val sortedEligible = sortedByPosition(pot.eligiblePlayerIds)

            if (s.isOmahaHiLo && lowWinners.isNotEmpty() && highWinners.isNotEmpty()) {
                val highHalf = pot.amount / 2
                val lowHalf = pot.amount - highHalf

                val highShare = highHalf / highWinners.size
                val highRemainder = highHalf % highWinners.size
                sortedHigh.forEachIndexed { idx, winnerId ->
                    payouts[winnerId] = (payouts[winnerId] ?: 0) + highShare + if (idx < highRemainder) 1 else 0
                }

                val lowShare = lowHalf / lowWinners.size
                val lowRemainder = lowHalf % lowWinners.size
                sortedLow.forEachIndexed { idx, winnerId ->
                    payouts[winnerId] = (payouts[winnerId] ?: 0) + lowShare + if (idx < lowRemainder) 1 else 0
                }
            } else {
                val actualWinners = if (highWinners.isNotEmpty()) sortedHigh else sortedEligible
                if (actualWinners.isNotEmpty()) {
                    val share = pot.amount / actualWinners.size
                    val remainder = pot.amount % actualWinners.size
                    actualWinners.forEachIndexed { idx, winnerId ->
                        payouts[winnerId] = (payouts[winnerId] ?: 0) + share + if (idx < remainder) 1 else 0
                    }
                }
            }
        }

        val results = s.players.map { p ->
            val winAmount = payouts[p.id] ?: 0
            PnLResult(
                playerId = p.id,
                name = p.name,
                netProfit = winAmount - p.totalInvested,
                payout = winAmount
            )
        }

        val bankruptPlayers = s.players.filter { p ->
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
            val wentToSD = p.hasSawFlopThisHand && !p.isFolded && p.isInHand
            val wonSD = wentToSD && winAmount > 0
            p.copy(
                stack = p.stack + winAmount, 
                kills = p.kills + addedKills,
                wentToShowdownCount = p.wentToShowdownCount + if (wentToSD) 1 else 0,
                wonAtShowdownCount = p.wonAtShowdownCount + if (wonSD) 1 else 0
            )
        }

        _state.value = s.copy(players = newPlayers, settlementResults = results, mainPot = 0, isSettlementPhase = false, biggestPot = newBiggestPot, logs = s.logs + LogEntry("Pot Settled.", LogType.INFO))
    }

    fun activateEndGame() {
        _state.value = _state.value.copy(isEndGameActivated = true)
    }

    fun returnToLobby(context: Context) {
        val resetPlayers = _state.value.players.map { it.copy(currentBet = 0, totalInvested = 0, isFolded = false, handsPlayed = 0, vpipCount = 0, pfrCount = 0, agqCount = 0, sawFlopCount = 0, wentToShowdownCount = 0, wonAtShowdownCount = 0, hasVpipThisHand = false, hasPfrThisHand = false, hasAgqThisHand = false, hasSawFlopThisHand = false, lastActedOnFullRaiseAmount = -1) }
        val newPool = _state.value.playerPool.toMutableList()
        resetPlayers.forEach { rp ->
            val idx = newPool.indexOfFirst { it.id == rp.id }
            if (idx != -1) newPool[idx] = rp
        }
        _state.value = _state.value.copy(
            isGameStarted = false,
            players = resetPlayers,
            playerPool = newPool,
            settlementResults = null,
            isEndGameActivated = false,
            biggestPot = 0
        )
        Prefs.saveSetup(context, _state.value)
    }
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PlODealerApp()
            }
        }
    }
}

// --- Theme Colors ---
val CasinoBgDark = Color(0xFF04120A)
val CasinoBgLight = Color(0xFF0A2215)
val Emerald400 = Color(0xFF34D399)
val Emerald300 = Color(0xFF6EE7B7)
val Emerald500 = Color(0xFF10B981)
val GoldYellow = Color(0xFFFFD700)
val PotGold = Color(0xFFEAB308)
val FoldRed = Color(0xCCEF4444)
val CallBlue = Color(0xCC2563EB)
val GlassWhite = Color.White.copy(alpha = 0.06f)
val GlassBorder = Color.White.copy(alpha = 0.15f)

@Composable
fun PlODealerApp() {
    val viewModel = remember { GameViewModel() }
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initSetup(context)
    }

    if (!state.isGameStarted) {
        SetupScreen(viewModel)
        return
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<Int?>(null) }
    var showBetDialogFor by remember { mutableStateOf<Int?>(null) }
    var showRebuyDialogFor by remember { mutableStateOf<Int?>(null) }
    var showSettleDialog by remember { mutableStateOf(false) }
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
    }

    LaunchedEffect(state.isSettlementPhase) {
        if (state.isSettlementPhase) {
            showSettleDialog = true
        }
    }

    if (state.settlementResults != null) {
        if (state.isEndGameActivated) {
            FinalSettlementScreen(
                players = state.players,
                biggestPot = state.biggestPot,
                onReturnToLobby = {
                    triggerVibration(context, 1)
                    viewModel.returnToLobby(context)
                }
            )
        } else {
            PnLScreen(
                results = state.settlementResults!!,
                players = state.players,
                quickChips = state.quickAddChips,
                onNextHand = {
                    triggerVibration(context, 1)
                    viewModel.startNextHand(context)
                },
                onRebuy = { playerId, amount ->
                    triggerVibration(context, 1)
                    viewModel.rebuyPlayer(playerId, amount, context)
                },
                onEndGame = {
                    triggerVibration(context, 2)
                    viewModel.activateEndGame()
                }
            )
        }
        return
    }

    Scaffold(
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
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 1. Header (20%)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.20f)
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    // Top Left: Undo & Logs
                    Row(modifier = Modifier.align(Alignment.TopStart)) {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Undo", tint = Color.White.copy(0.7f))
                        }
                        IconButton(onClick = { showLogsDialog = true }) {
                            Icon(Icons.Default.List, contentDescription = "Logs", tint = Color.White.copy(0.7f))
                        }
                    }

                    // Top Right: Gear
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSettingsDialog = true
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Settings", tint = Color.White.copy(0.7f))
                    }

                    // Center: Street Name, Main Pot, Max Bet / Settle Button
                    val activeNonFolded = state.players.filter { !it.isFolded && it.isInHand }
                    val canChopBlinds = state.street == Street.PRE_FLOP && activeNonFolded.size == 2 && state.players.sumOf { it.totalInvested } == state.sbAmount + state.bbAmount

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = state.street,
                        transitionSpec = {
                            fadeIn(tween(300, delayMillis = 150)) togetherWith fadeOut(tween(150))
                        },
                        label = "StreetFlip"
                    ) { street ->
                        val rotX by transition.animateFloat(
                            transitionSpec = {
                                if (targetState == EnterExitState.Visible) {
                                    tween(300, easing = LinearOutSlowInEasing)
                                } else {
                                    tween(150, easing = FastOutLinearInEasing)
                                }
                            },
                            label = "rotX"
                        ) { exitState ->
                            when (exitState) {
                                EnterExitState.PreEnter -> -90f
                                EnterExitState.Visible -> 0f
                                EnterExitState.PostExit -> 90f
                            }
                        }
                        val sColor = when (street) {
                            Street.PRE_FLOP -> Color(0xFF3B82F6) // Deep Blue
                            Street.FLOP -> Emerald400
                            Street.TURN -> Color(0xFFF97316) // Dark Orange
                            Street.RIVER -> FoldRed // Dark Red
                        }
                        Text(
                            text = street.name.replace("_", " "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = sColor,
                            modifier = Modifier
                                .graphicsLayer { rotationX = rotX }
                                .background(sColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
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
                        }

                        if (state.isSettlementPhase) {
                            Button(
                                onClick = { showSettleDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                modifier = Modifier.padding(top = 4.dp).height(32.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Text("SETTLE POT", color = CasinoBgDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val activePlayer = state.players.find { it.id == state.activePlayerId }
                            if (activePlayer != null && !activePlayer.isFolded) {
                                val callAmount = (state.highestBet - activePlayer.currentBet).coerceAtLeast(0)
                                val currentTotalPot = state.mainPot + state.players.sumOf { it.currentBet }
                                val maxRaise = currentTotalPot + callAmount
                                val theoreticalMaxBetTotal = state.highestBet + maxRaise
                                val actualMaxBetTotal = theoreticalMaxBetTotal.coerceAtMost(activePlayer.currentBet + activePlayer.stack)
                                val actualMaxExtra = actualMaxBetTotal - activePlayer.currentBet

                                val minRaiseDelta = state.lastRaiseDelta
                                val minBetTotal = (state.highestBet + minRaiseDelta).coerceAtMost(actualMaxBetTotal)
                                val minExtra = minBetTotal - activePlayer.currentBet

                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = Color.White.copy(0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium)) {
                                            append("MIN: ")
                                        }
                                        withStyle(SpanStyle(color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                                            append(minBetTotal.fmt())
                                        }
                                        if (minBetTotal != minExtra) {
                                            withStyle(SpanStyle(color = Color.Gray, fontSize = 10.sp)) {
                                                append("(+${minExtra.fmt()})")
                                            }
                                        }
                                        withStyle(SpanStyle(color = Color.White.copy(0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium)) {
                                            append(" — MAX: ")
                                        }
                                        withStyle(SpanStyle(color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                                            append(actualMaxBetTotal.fmt())
                                        }
                                        if (actualMaxBetTotal != actualMaxExtra) {
                                            withStyle(SpanStyle(color = Color.Gray, fontSize = 10.sp)) {
                                                append("(+${actualMaxExtra.fmt()})")
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Players Area (80%)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.80f)
                        .padding(horizontal = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val dIdx = state.dealerIdx
                    val isHeadsUp = state.players.count { it.isInHand } == 2
                    var sbIdx: Int
                    var bbIdx: Int
                    if (isHeadsUp) {
                        sbIdx = dIdx
                        bbIdx = (dIdx + 1) % state.playerCount
                        while (!state.players[bbIdx].isInHand) bbIdx = (bbIdx + 1) % state.playerCount
                    } else {
                        sbIdx = (dIdx + 1) % state.playerCount
                        while (!state.players[sbIdx].isInHand) sbIdx = (sbIdx + 1) % state.playerCount
                        bbIdx = (sbIdx + 1) % state.playerCount
                        while (!state.players[bbIdx].isInHand) bbIdx = (bbIdx + 1) % state.playerCount
                    }
                    val currentTotalPot = state.mainPot + state.players.sumOf { it.currentBet }

                    state.players.forEachIndexed { index, player ->
                        val callAmount = (state.highestBet - player.currentBet).coerceAtLeast(0)
                        val maxRaise = currentTotalPot + callAmount
                        val targetC = state.highestBet
                        val targetMinBet = state.highestBet + state.lastRaiseDelta
                        val targetMaxBet = state.highestBet + maxRaise
                        val canRaiseThisTurn = !state.actedPlayerIdsThisStreet.contains(player.id) || player.lastActedOnFullRaiseAmount < state.lastFullRaiseAmount || (player.currentBet == 0 && state.highestBet > 0)

                        PlayerCardCompact(
                            modifier = Modifier,
                            player = player,
                            isActive = (player.id == state.activePlayerId && !player.isFolded && !state.isSettlementPhase),
                            canRaise = canRaiseThisTurn,
                            bbAmount = state.bbAmount,
                            highestBet = state.highestBet,
                            targetC = targetC,
                            targetMinBet = targetMinBet,
                            targetMaxBet = targetMaxBet,
                            isDealer = index == dIdx,
                            isSb = index == sbIdx,
                            isBb = index == bbIdx,
                            isRaiser = player.id == state.lastAggressorId,
                            onRenameClick = {
                                triggerVibration(context, 1)
                                showRenameDialogFor = player.id
                            },
                            onFold = {
                                triggerVibration(context, 0)
                                viewModel.foldPlayer(player.id)
                            },
                            onCall = {
                                triggerVibration(context, 0)
                                viewModel.callPlayer(player.id)
                            },
                            onPot = {
                                triggerVibration(context, 1)
                                viewModel.potPlayer(player.id)
                            },
                            onBet = {
                                triggerVibration(context, 1)
                                showBetDialogFor = player.id
                            },
                            onMinRaise = {
                                triggerVibration(context, 1)
                                viewModel.minRaisePlayer(player.id)
                            },
                            onAllIn = {
                                triggerVibration(context, 2)
                                viewModel.customBetPlayer(player.id, player.currentBet + player.stack)
                            },
                            onRebuyClick = {
                                triggerVibration(context, 1)
                                showRebuyDialogFor = player.id
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = false,
                enter = scaleIn(tween(200), initialScale = 0.5f) + fadeIn(tween(200)),
                exit = scaleOut(tween(400), targetScale = 1.5f) + fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.Center)
            ) {
            }
        }
    }

    // --- Dialogs ---

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Return to Lobby") },
            text = { Text("Are you sure you want to end this hand and return to the Setup Lobby?", color = Color.White.copy(0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.returnToLobby(context)
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FoldRed)
                ) {
                    Text("Yes, Return")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            },
            containerColor = CasinoBgLight,
            titleContentColor = Emerald400
        )
    }

    if (showRenameDialogFor != null) {
        val player = state.players.find { it.id == showRenameDialogFor }
        val currentName = player?.name ?: ""

        CustomKeyboardDialog(
            title = "Rename P${player?.id}",
            initialValue = currentName,
            isNumeric = false,
            onDismiss = { showRenameDialogFor = null },
            onConfirm = { newVal ->
                viewModel.renameSetupPlayer(showRenameDialogFor!!, newVal, context)
                showRenameDialogFor = null
            }
        )
    }

    if (showBetDialogFor != null) {
        val player = state.players.find { it.id == showBetDialogFor }
        val callAmount = (state.highestBet - (player?.currentBet ?: 0)).coerceAtLeast(0)
        val currentTotalPot = state.mainPot + state.players.sumOf { it.currentBet }
        val maxRaise = currentTotalPot + callAmount
        val targetMaxBet = state.highestBet + maxRaise

        val maxNumeric = targetMaxBet.coerceAtMost((player?.currentBet ?: 0) + (player?.stack ?: 0))
        val minBet = (state.highestBet + state.lastRaiseDelta).coerceAtMost(maxNumeric)

        CustomKeyboardDialog(
            title = "Bet/Raise - ${player?.name}",
            initialValue = "",
            isNumeric = true,
            minNumeric = minBet,
            maxNumeric = maxNumeric,
            quickChips = state.quickAddChips,
            onDismiss = { showBetDialogFor = null },
            onConfirm = { newVal ->
                val inputVal = newVal.toIntOrNull() ?: 0
                viewModel.customBetPlayer(showBetDialogFor!!, inputVal)
                showBetDialogFor = null
            }
        )
    }

    if (showRebuyDialogFor != null) {
        val player = state.players.find { it.id == showRebuyDialogFor }

        CustomKeyboardDialog(
            title = "Rebuy - ${player?.name}",
            initialValue = "",
            isNumeric = true,
            minNumeric = 1,
            onDismiss = { showRebuyDialogFor = null },
            onConfirm = { newVal ->
                val inputVal = newVal.toIntOrNull() ?: 0
                if (inputVal > 0) {
                    viewModel.rebuyPlayer(showRebuyDialogFor!!, inputVal, context)
                }
                showRebuyDialogFor = null
            }
        )
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text("Action Logs", color = Emerald400) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(state.logs.size) { index ->
                        val log = state.logs[state.logs.size - 1 - index]
                        val logColor = when (log.type) {
                            LogType.FOLD -> Color.Gray
                            LogType.CALL -> Color(0xFFADD8E6) // Light Blue
                            LogType.RAISE -> Color(0xFFFFA500) // Orange
                            LogType.POT -> Color.Yellow
                            else -> Color.White.copy(0.8f)
                        }
                        Text(log.text, fontSize = 14.sp, color = logColor, modifier = Modifier.padding(vertical = 4.dp))
                        HorizontalDivider(color = Color.White.copy(0.1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) { Text("Close", color = Emerald400) }
            },
            containerColor = CasinoBgLight
        )
    }

    if (showSettleDialog && state.settlementResults == null) {
        val pots = remember(state.mainPot, state.players) { viewModel.calculatePots() }
        SettlePotDialog(
            state = state,
            pots = pots,
            onDismiss = { showSettleDialog = false },
            onConfirm = { settlements ->
                viewModel.settlePots(settlements)
                showSettleDialog = false
            }
        )
    }
}

@Composable
fun PlayerCardCompact(
    modifier: Modifier = Modifier,
    player: Player,
    isActive: Boolean,
    canRaise: Boolean,
    highestBet: Int,
    targetC: Int,
    targetMinBet: Int,
    targetMaxBet: Int,
    bbAmount: Int,
    isDealer: Boolean,
    isSb: Boolean,
    isBb: Boolean,
    isRaiser: Boolean,
    onRenameClick: () -> Unit,
    onFold: () -> Unit,
    onCall: () -> Unit,
    onPot: () -> Unit,
    onBet: () -> Unit,
    onMinRaise: () -> Unit,
    onAllIn: () -> Unit,
    onRebuyClick: () -> Unit
) {
    val isFolded = player.isFolded
    val isStrikeOut = !player.isInHand
    val isCollapsed = isFolded || isStrikeOut
    val isAllIn = player.stack == 0 && player.totalInvested > 0 && !isFolded

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val scale by animateFloatAsState(if (isActive) 1.02f else 1f, label = "scale")

    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(targetValue = swipeOffset, label = "swipe")

    val targetBgColor = when {
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
    val borderWidth = if (isActive || isRaiser || isAllIn) 2.dp else 1.dp

    val cardAlpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            isCollapsed -> 0.4f
            isRaiser -> 0.9f
            else -> 0.7f
        },
        label = "alpha"
    )
    val cardPadding by animateDpAsState(if (isCollapsed) 4.dp else 8.dp, label = "padding")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
            .alpha(cardAlpha)
            .offset { IntOffset(animatedSwipeOffset.roundToInt(), 0) }
            .pointerInput(isFolded, isStrikeOut) {
                if (isFolded || isStrikeOut) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset < -size.width * 0.25f) {
                            onFold()
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = {
                        swipeOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount < 0 || swipeOffset < 0) {
                            swipeOffset = (swipeOffset + dragAmount).coerceAtMost(0f)
                            if (swipeOffset < 0) change.consume()
                        }
                    }
                )
            }
            .background(bgColor, RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .animateContentSize(animationSpec = tween(400, easing = FastOutSlowInEasing))
            .padding(cardPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(400, easing = FastOutSlowInEasing))
        ) {
            // Top Row: Info (Always visible, but adapts)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Avatar & Name
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = !isCollapsed,
                        enter = expandHorizontally(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400)),
                        exit = shrinkHorizontally(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeOut(tween(400))
                    ) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (isFolded) Color.DarkGray else Emerald500, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${player.id}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                    
                    Column(
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
                    }
                }
                
                // Right: Bet Amount
                AnimatedVisibility(
                    visible = !isCollapsed,
                    enter = expandHorizontally(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400)),
                    exit = shrinkHorizontally(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeOut(tween(400))
                ) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                        if (player.totalRebuyAmount > 0) {
                            Text("[RB: +${player.totalRebuyAmount.fmt()}]", color = FoldRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        AnimatedVisibility(
                            visible = player.currentBet > 0,
                            enter = fadeIn(tween(300)),
                            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300))
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isAllIn) {
                                        Text("ALL-IN", fontSize = 9.sp, color = FoldRed, fontWeight = FontWeight.Black, modifier = Modifier.background(Color.White.copy(0.1f), RoundedCornerShape(2.dp)).padding(horizontal = 2.dp))
                                    } else {
                                        Text("BET", fontSize = 9.sp, color = Emerald300.copy(0.6f), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    if (isRaiser) {
                                        Text(" RAISE ", modifier = Modifier.background(FoldRed, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    AnimatedContent(
                                        targetState = player.currentBet,
                                        transitionSpec = { slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut() },
                                        label = "PlayerBetAnim"
                                    ) { bet ->
                                        Text(
                                            text = bet.fmt(),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isAllIn) GoldYellow else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Row: Actions
            AnimatedVisibility(
                visible = !isCollapsed && !isFolded,
                enter = expandVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400)),
                exit = shrinkVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeOut(tween(400))
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    val totalBetS = player.currentBet + player.stack
                    val isLevel1 = totalBetS <= targetC
                    val isLevel2 = totalBetS > targetC && totalBetS < targetMinBet
                    val isLevel3 = totalBetS >= targetMinBet && totalBetS < targetMaxBet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = onFold,
                            enabled = isActive,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FoldRed, disabledContainerColor = FoldRed.copy(0.3f)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("F", fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }

                        if (isLevel1) {
                            Button(
                                onClick = onAllIn,
                                enabled = isActive,
                                modifier = Modifier.weight(3f).fillMaxHeight(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FoldRed, contentColor = Color.White, disabledContainerColor = FoldRed.copy(0.3f)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("ALL IN", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            }
                        } else {
                            val canCheck = targetC <= player.currentBet
                            val callText = if (canCheck) "Ch" else "C"
                            val callColor = if (canCheck) Color.White else CallBlue
                            val callTextColor = if (canCheck) Color.Black else Color.White

                            Button(
                                onClick = onCall,
                                enabled = isActive,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = callColor,
                                    contentColor = callTextColor,
                                    disabledContainerColor = callColor.copy(0.3f),
                                    disabledContentColor = callTextColor.copy(0.3f)
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(callText, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }

                            if (canRaise) {
                                if (isLevel2) {
                                    Button(
                                        onClick = onAllIn,
                                        enabled = isActive,
                                        modifier = Modifier.weight(2f).fillMaxHeight(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = FoldRed, contentColor = Color.White, disabledContainerColor = FoldRed.copy(0.3f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("ALL IN", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                    }
                                } else if (isLevel3) {
                                    BetButtonWithLongPress(
                                        isActive = isActive,
                                        onTap = onBet,
                                        onLongPress = onMinRaise,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = onAllIn,
                                        enabled = isActive,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = FoldRed, contentColor = Color.White, disabledContainerColor = FoldRed.copy(0.3f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("ALL IN", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    }
                                } else {
                                    BetButtonWithLongPress(
                                        isActive = isActive,
                                        onTap = onBet,
                                        onLongPress = onMinRaise,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = onPot,
                                        enabled = isActive,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PotGold,
                                            contentColor = CasinoBgDark,
                                            disabledContainerColor = PotGold.copy(0.3f),
                                            disabledContentColor = CasinoBgDark.copy(0.3f)
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("P", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun BetButtonWithLongPress(
    isActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val color by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF39FF14) else Emerald500, // Bright Green
        animationSpec = tween(if (isPressed) 500 else 200, easing = LinearEasing),
        label = "BtnColor"
    )

    val containerColor = if (isActive) color else Emerald500.copy(0.3f)
    val contentColor = if (isActive) CasinoBgDark else CasinoBgDark.copy(0.3f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .pointerInput(isActive) {
                if (!isActive) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        var finished = false
                        val job = scope.launch {
                            delay(500)
                            if (!finished) {
                                onLongPress()
                                isPressed = false
                            }
                        }
                        val success = tryAwaitRelease()
                        finished = true
                        isPressed = false
                        job.cancel()
                        if (success && !job.isCompleted) {
                            onTap()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text("B/R", fontSize = 14.sp, fontWeight = FontWeight.Black, color = contentColor)
    }
}

// --- PnL Settlement Screen (完全剥离动画版) ---
@Composable
fun PnLScreen(
    results: List<PnLResult>,
    players: List<Player>,
    quickChips: List<Int>,
    onNextHand: () -> Unit,
    onRebuy: (Int, Int) -> Unit,
    onEndGame: () -> Unit
) {
    var showRebuyDialogFor by remember { mutableStateOf<Int?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = CasinoBgDark,
            contentColor = Color.White
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text("HAND SETTLED", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Emerald400)

                    Spacer(modifier = Modifier.height(24.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { result ->
                            val isPositive = result.netProfit > 0
                            val isNeutral = result.netProfit == 0
                            val amountColor = when {
                                isPositive -> Emerald400
                                isNeutral -> Color.Gray
                                else -> FoldRed
                            }
                            val sign = if (isPositive) "+" else ""
                            val player = players.find { it.id == result.playerId }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(result.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text((player?.stack ?: 0).fmt(), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Emerald300, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = result.payout.fmt(),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = Emerald400
                                    )
                                    Text(
                                        text = "$sign${result.netProfit.fmt()}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = amountColor
                                    )
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
                    }
                }

                val activePlayersCount = players.count { it.stack > 0 }
                if (activePlayersCount >= 2) {
                    Button(
                        onClick = onNextHand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PotGold, contentColor = CasinoBgDark)
                    ) {
                        Text("NEXT HAND", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                EndGameButton(
                    isActivated = false,
                    onActivated = onEndGame
                )
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
}

@Composable
fun SetupScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    var showRenameDialogFor by remember { mutableStateOf<Int?>(null) }
    var showStackDialogFor by remember { mutableStateOf<Int?>(null) }
    var showLobbySettings by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.White
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(CasinoBgDark, CasinoBgLight)))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Player Count Switcher & HiLo Switch
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OMAHA LOBBY", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Emerald400, letterSpacing = 2.sp)
                        Row {
                            IconButton(onClick = { showLobbySettings = true }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Settings", tint = Emerald400)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Player Count", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(3, 4, 5).forEach { count ->
                            val selected = state.playerCount == count
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .background(if (selected) Emerald500 else GlassWhite, RoundedCornerShape(12.dp))
                                    .border(if (selected) 2.dp else 1.dp, if (selected) Emerald400 else GlassBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.updateSetupCount(count, context)
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$count", color = if (selected) CasinoBgDark else Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassWhite, RoundedCornerShape(12.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Omaha Hi/Lo (8-or-Better)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Enable split pot settlement", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = state.isOmahaHiLo,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateSetupHiLo(it, context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Emerald400, checkedTrackColor = Emerald500.copy(0.5f))
                        )
                    }
                }

                // Middle: Players List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.players.forEachIndexed { index, player ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GlassWhite, RoundedCornerShape(16.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Emerald500.copy(0.2f), CircleShape)
                                        .border(1.dp, Emerald500, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("P${player.id}", color = Emerald400, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = player.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showRenameDialogFor = player.id
                                        }
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showStackDialogFor = player.id
                                        }
                                    ) {
                                        Text("Stack: ", color = Color.Gray, fontSize = 14.sp)
                                        Text(player.stack.fmt(), color = PotGold, fontWeight = FontWeight.Black, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                        Text("  ✎", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.movePlayer(index, -1, context)
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("▲", color = if (index > 0) Color.White else Color.Transparent)
                                }
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.movePlayer(index, 1, context)
                                    },
                                    enabled = index < state.players.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("▼", color = if (index < state.players.size - 1) Color.White else Color.Transparent)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startGame(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PotGold, contentColor = CasinoBgDark)
                ) {
                    Text("START GAME", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.clearAllChips(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset All Chips", color = FoldRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showRenameDialogFor != null) {
        val player = state.players.find { it.id == showRenameDialogFor }
        CustomKeyboardDialog(
            title = "Rename P${player?.id}",
            initialValue = player?.name ?: "",
            isNumeric = false,
            onDismiss = { showRenameDialogFor = null },
            onConfirm = { newVal ->
                viewModel.renameSetupPlayer(showRenameDialogFor!!, newVal, context)
                showRenameDialogFor = null
            }
        )
    }

    if (showStackDialogFor != null) {
        val player = state.players.find { it.id == showStackDialogFor }
        CustomKeyboardDialog(
            title = "Set Stack - ${player?.name}",
            initialValue = player?.stack?.toString() ?: "1000",
            isNumeric = true,
            minNumeric = 0,
            quickChips = state.quickAddChips,
            onDismiss = { showStackDialogFor = null },
            onConfirm = { newVal ->
                viewModel.updateSetupPlayerStack(showStackDialogFor!!, newVal.toIntOrNull() ?: 1000, context)
                showStackDialogFor = null
            }
        )
    }

    if (showLobbySettings) {
        LobbySettingsDialog(
            currentSb = state.sbAmount,
            currentBb = state.bbAmount,
            currentBuyIn = state.defaultBuyIn,
            currentChips = state.quickAddChips,
            onDismiss = { showLobbySettings = false },
            onSave = { sb, bb, buyin, chips ->
                viewModel.updateSettings(sb, bb, buyin, chips, context)
                showLobbySettings = false
            }
        )
    }
}

@Composable
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
    var chipsText by remember { mutableStateOf(currentChips.joinToString(",")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lobby Settings", color = Emerald400) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sbText,
                    onValueChange = { sbText = it },
                    label = { Text("Small Blind", color = Color.Gray) },
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
                    value = bbText,
                    onValueChange = { bbText = it },
                    label = { Text("Big Blind", color = Color.Gray) },
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
                    value = chipsText,
                    onValueChange = { chipsText = it },
                    label = { Text("Quick Chips (csv, max 8)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = Emerald400
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sb = sbText.toIntOrNull() ?: 10
                    val bb = bbText.toIntOrNull() ?: 20
                    val buyin = buyInText.toIntOrNull() ?: 1000
                    val parsedChips = chipsText.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .take(8)
                    val finalChips = if (parsedChips.isEmpty()) listOf(10,20,100,500) else parsedChips
                    onSave(sb, bb, buyin, finalChips)
                }
            ) {
                Text("Save", color = Emerald400)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = CasinoBgLight
    )
}

// --- Custom Numpad Keyboard Dialog ---
@Composable
fun CustomKeyboardDialog(
    title: String,
    initialValue: String,
    isNumeric: Boolean,
    minNumeric: Int = 0,
    maxNumeric: Int = Int.MAX_VALUE,
    quickChips: List<Int> = listOf(10, 20, 100, 500),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val haptics = LocalHapticFeedback.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 16.sp, color = Color.White.copy(0.6f))
                Spacer(modifier = Modifier.height(8.dp))

                // Display Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.Black.copy(0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textValue.ifEmpty { if (isNumeric) "0" else "" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                if (isNumeric && quickChips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // Smart Chips Grid
                    val chipRows = quickChips.chunked(4)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chipRows.forEach { rowChips ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowChips.forEach { chip ->
                                    Button(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val current = textValue.toIntOrNull() ?: 0
                                            val sum = current + chip
                                            textValue = if (sum <= maxNumeric) sum.toString() else maxNumeric.toString()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500.copy(0.2f), contentColor = Emerald400),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+$chip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (rowChips.size < 4) {
                                    repeat(4 - rowChips.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isNumeric) {
                    // Numpad Grid
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("CLR", "0", "DEL")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { key ->
                                    Button(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            when (key) {
                                                "CLR" -> textValue = ""
                                                "DEL" -> if (textValue.isNotEmpty()) textValue = textValue.dropLast(1)
                                                else -> {
                                                    val newVal = textValue + key
                                                    if (isNumeric) {
                                                        val num = newVal.toIntOrNull() ?: 0
                                                        textValue = if (num <= maxNumeric) newVal else maxNumeric.toString()
                                                    } else {
                                                        textValue = newVal
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GlassWhite, contentColor = Color.White),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        when (key) {
                                            "DEL" -> Text("⌫", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            else -> Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val keys = listOf(
                        listOf("Q","W","E","R","T","Y","U","I","O","P"),
                        listOf("A","S","D","F","G","H","J","K","L"),
                        listOf("Z","X","C","V","B","N","M"),
                        listOf("CLR", "SPACE", "DEL")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                row.forEach { key ->
                                    val isAction = key == "CLR" || key == "DEL" || key == "SPACE"
                                    val w = if (key == "SPACE") 0.4f else if (isAction) 0.2f else 0.1f
                                    Button(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            when (key) {
                                                "CLR" -> textValue = ""
                                                "DEL" -> if (textValue.isNotEmpty()) textValue = textValue.dropLast(1)
                                                "SPACE" -> textValue += " "
                                                else -> textValue += key
                                            }
                                        },
                                        modifier = Modifier.weight(w).height(40.dp).padding(horizontal = 1.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GlassWhite, contentColor = Color.White),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        when (key) {
                                            "DEL" -> Text("⌫", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            "SPACE" -> Text("␣")
                                            else -> Text(key, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val inputVal = textValue.toIntOrNull() ?: 0
                val isConfirmEnabled = !isNumeric || inputVal >= minNumeric

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm(textValue)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = CasinoBgDark),
                    enabled = isConfirmEnabled
                ) {
                    Text("CONFIRM", fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun EndGameButton(isActivated: Boolean, enabled: Boolean = true, onActivated: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.5f)
            .pointerInput(isActivated, enabled) {
                if (isActivated || !enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        try {
                            progress.snapTo(0f)
                            val animJob = scope.launch {
                                progress.animateTo(1f, animationSpec = tween(1500, easing = LinearEasing))
                                if (progress.value >= 1f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onActivated()
                                }
                            }
                            tryAwaitRelease()
                            animJob.cancel()
                            if (progress.value < 1f) {
                                scope.launch { progress.snapTo(0f) }
                            }
                        } catch (e: Exception) {
                            scope.launch { progress.snapTo(0f) }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sqSize = size.height / 2
            val cols = (size.width / sqSize).toInt() + 1
            for (r in 0 until 2) {
                for (c in 0 until cols) {
                    val color = if (isActivated) {
                        if ((r + c) % 2 == 0) FoldRed else Color.White
                    } else {
                        if ((r + c) % 2 == 0) Color(0xFF111111) else Color(0xFF333333)
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(c * sqSize, r * sqSize),
                        size = androidx.compose.ui.geometry.Size(sqSize, sqSize)
                    )
                }
            }
        }

        if (!isActivated && progress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(Emerald500.copy(alpha = 0.8f))
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isActivated) "END GAME ACTIVATED" else "HOLD TO END GAME",
                color = if (isActivated) Color.White else Color.White.copy(0.5f),
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 4.sp
            )
        }
    }
}

@Composable
fun FinalSettlementScreen(players: List<Player>, biggestPot: Int, onReturnToLobby: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(CasinoBgDark)) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("SESSION REPORT", fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("BIGGEST POT: 💰 ${biggestPot.fmt()}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = GoldYellow, style = LocalTextStyle.current.copy(shadow = Shadow(color = PotGold, blurRadius = 12f)))
                }

                Spacer(modifier = Modifier.height(24.dp))

                val sortedPlayers = players.map { it to (it.stack - it.totalBuyIn) }.sortedByDescending { it.second }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                    items(sortedPlayers) { (player, profit) ->
                        val isPositive = profit > 0
                        val isNeutral = profit == 0
                        val amountColor = when {
                            isPositive -> Emerald400
                            isNeutral -> Color.Gray
                            else -> FoldRed
                        }
                        val sign = if (isPositive) "+" else ""

                        val vpipPercent = if (player.handsPlayed > 0) (player.vpipCount.toFloat() / player.handsPlayed) else 0f
                        val agqPercent = if (player.handsPlayed > 0) (player.agqCount.toFloat() / player.handsPlayed) else 0f
                        val pfrPercent = if (player.handsPlayed > 0) (player.pfrCount.toFloat() / player.handsPlayed) else 0f
                        val wtsdPercent = if (player.sawFlopCount > 0) (player.wentToShowdownCount.toFloat() / player.sawFlopCount) else 0f
                        val wsdPercent = if (player.wentToShowdownCount > 0) (player.wonAtShowdownCount.toFloat() / player.wentToShowdownCount) else 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(player.name, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${player.handsPlayed} Hands | ${player.kills} Kills", fontSize = 12.sp, color = Color.White.copy(0.5f))
                                }
                                Text(
                                    text = "$sign${profit.fmt()}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = amountColor
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GradientStatBar(modifier = Modifier.weight(1f), label = "VPIP", percent = vpipPercent, startColor = Color(0xFF1E3A8A), endColor = Emerald500)
                                    GradientStatBar(modifier = Modifier.weight(1f), label = "PFR", percent = pfrPercent, startColor = Color(0xFF701A75), endColor = Color(0xFFD946EF))
                                    GradientStatBar(modifier = Modifier.weight(1f), label = "AGQ", percent = agqPercent, startColor = Color(0xFF9A3412), endColor = FoldRed)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GradientStatBar(modifier = Modifier.weight(1f), label = "WTSD%", percent = wtsdPercent, startColor = Color(0xFF0F766E), endColor = Emerald300)
                                    GradientStatBar(modifier = Modifier.weight(1f), label = "W\$SD%", percent = wsdPercent, startColor = Color(0xFF854D0E), endColor = GoldYellow)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onReturnToLobby,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FoldRed, contentColor = Color.White)
                ) {
                    Text("RETURN TO LOBBY", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            }
        }
    }
}

@Composable
fun GradientStatBar(modifier: Modifier, label: String, percent: Float, startColor: Color, endColor: Color) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))
            Text("${(percent * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = endColor)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(color = Color.Black.copy(0.5f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            if (percent > 0f) {
                drawRoundRect(brush = Brush.horizontalGradient(listOf(startColor, endColor)), size = size.copy(width = size.width * percent), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
        }
    }
}

@Composable
fun SettlePotDialog(
    state: GameState,
    pots: List<Pot>,
    onDismiss: () -> Unit,
    onConfirm: (List<PotSettlement>) -> Unit
) {
    var potSettlements by remember {
        mutableStateOf(
            pots.associate { pot ->
                if (pot.eligiblePlayerIds.size <= 1) {
                    pot.id to PotSettlement(pot.id, pot.eligiblePlayerIds, emptySet())
                } else {
                    pot.id to PotSettlement(pot.id, emptySet(), emptySet())
                }
            }
        )
    }

    val haptics = LocalHapticFeedback.current
    val activePlayers = state.players.filter { !it.isFolded }

    val showdownFirstPlayer = remember(state.players) {
        val inHandPlayers = state.players.filter { !it.isFolded && it.isInHand }
        val lastAggressor = inHandPlayers.find { it.id == state.lastAggressorIdOverall }
        if (lastAggressor != null) {
            lastAggressor
        } else {
            inHandPlayers.minByOrNull { p ->
                val idx = state.players.indexOfFirst { it.id == p.id }
                val distance = (idx - state.dealerIdx + state.players.size) % state.players.size
                if (distance == 0) state.players.size else distance
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1E293B), CasinoBgLight)), RoundedCornerShape(24.dp))
                .border(2.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("SETTLE POTS", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Emerald400, letterSpacing = 2.sp)

                if (showdownFirstPlayer != null) {
                    Text("Showdown: ${showdownFirstPlayer.name} shows first", fontSize = 14.sp, color = GoldYellow)
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(pots) { pot ->
                        val settlement = potSettlements[pot.id]!!
                        val eligibleList = activePlayers.filter { it.id in pot.eligiblePlayerIds }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(0.2f), RoundedCornerShape(12.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pot.name, fontWeight = FontWeight.Black, color = GoldYellow, fontSize = 16.sp)
                                Text("$${pot.amount.fmt()}", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                            }

                            if (pot.eligiblePlayerIds.size <= 1) {
                                val winner = state.players.find { it.id == pot.eligiblePlayerIds.firstOrNull() }
                                Text("Auto-win: ${winner?.name ?: "Unknown"}", color = Emerald400, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("High Winner(s):", fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f), fontSize = 12.sp)
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
                                                Text(p.stack.fmt(), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Emerald400, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }

                                if (state.isOmahaHiLo) {
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
                                                    Text(p.stack.fmt(), fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Emerald400, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val allSettled = pots.all { pot ->
                    val s = potSettlements[pot.id]!!
                    pot.eligiblePlayerIds.size <= 1 || s.highWinners.isNotEmpty()
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassWhite, contentColor = Color.White)
                    ) {
                        Text("CANCEL")
                    }

                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm(potSettlements.values.toList())
                        },
                        enabled = allSettled,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = CasinoBgDark, disabledContainerColor = Emerald500.copy(0.3f))
                    ) {
                        Text("CONFIRM", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}