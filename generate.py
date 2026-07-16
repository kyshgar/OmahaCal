import sys

def write_code():
    code = """package com.example.omahacalculator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// --- Preferences ---
object Prefs {
    fun savePlayers(context: Context, players: List<SetupPlayer>) {
        val prefs = context.getSharedPreferences("OmahaPrefs", Context.MODE_PRIVATE)
        val str = players.joinToString(";") { "${it.id},${it.name},${it.stack}" }
        prefs.edit().putString("players", str).apply()
    }
    fun loadPlayers(context: Context): List<SetupPlayer> {
        val prefs = context.getSharedPreferences("OmahaPrefs", Context.MODE_PRIVATE)
        val str = prefs.getString("players", null)
        if (!str.isNullOrEmpty()) {
            try {
                return str.split(";").map {
                    val parts = it.split(",")
                    SetupPlayer(parts[0].toInt(), parts[1], parts[2].toInt())
                }
            } catch (e: Exception) {}
        }
        return listOf(
            SetupPlayer(1, "P1", 1000),
            SetupPlayer(2, "P2", 1000),
            SetupPlayer(3, "P3", 1000),
            SetupPlayer(4, "P4", 1000)
        )
    }
}

// --- Data Classes ---
enum class Street { PRE_FLOP, FLOP, TURN, RIVER }
data class SetupPlayer(val id: Int, val name: String, val stack: Int)
data class Player(
    val id: Int,
    val name: String,
    val stack: Int,
    val currentBet: Int = 0,
    val totalInvested: Int = 0,
    val isFolded: Boolean = false,
    val isAllIn: Boolean = false,
    val isStrikeOut: Boolean = false
)
data class SidePot(val amount: Int, val eligiblePlayerIds: Set<Int>)
data class ActionLog(val msg: String)
data class PnLResult(val playerId: Int, val name: String, val netProfit: Int, val scoop: Boolean = false)

data class GameState(
    val players: List<Player> = emptyList(),
    val dealerIdx: Int = 0,
    val activePlayerId: Int = -1,
    val mainPot: Int = 0,
    val sidePots: List<SidePot> = emptyList(),
    val highestBet: Int = 0,
    val lastAggressorId: Int = -1,
    val street: Street = Street.PRE_FLOP,
    val logs: List<ActionLog> = emptyList(),
    val history: List<GameState> = emptyList(),
    val settlementResults: List<PnLResult>? = null,
    val initialStacks: Map<Int, Int> = emptyMap(),
    val isHiLo: Boolean = false,
    val actedPlayerIdsThisStreet: Set<Int> = emptySet(),
    val smallBlind: Int = 10,
    val bigBlind: Int = 20,
    val isEndGame: Boolean = false
)

sealed class Screen {
    object Setup : Screen()
    object Game : Screen()
    object EndGame : Screen()
}

// --- ViewModel ---
class GameViewModel : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Setup)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _setupPlayers = MutableStateFlow<List<SetupPlayer>>(emptyList())
    val setupPlayers: StateFlow<List<SetupPlayer>> = _setupPlayers.asStateFlow()
    
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    var isHiLo by mutableStateOf(false)

    fun initSetup(context: Context) {
        _setupPlayers.value = Prefs.loadPlayers(context)
    }

    fun updateSetupPlayer(id: Int, name: String, stack: Int, context: Context) {
        _setupPlayers.value = _setupPlayers.value.map {
            if (it.id == id) it.copy(name = name, stack = stack) else it
        }
        Prefs.savePlayers(context, _setupPlayers.value)
    }

    fun moveSetupPlayer(index: Int, up: Boolean, context: Context) {
        val list = _setupPlayers.value.toMutableList()
        if (up && index > 0) {
            val temp = list[index]
            list[index] = list[index - 1]
            list[index - 1] = temp
        } else if (!up && index < list.size - 1) {
            val temp = list[index]
            list[index] = list[index + 1]
            list[index + 1] = temp
        }
        _setupPlayers.value = list
        Prefs.savePlayers(context, list)
    }
    
    fun addSetupPlayer(context: Context) {
        if (_setupPlayers.value.size >= 5) return
        val newId = (_setupPlayers.value.maxOfOrNull { it.id } ?: 0) + 1
        val list = _setupPlayers.value + SetupPlayer(newId, "P$newId", 1000)
        _setupPlayers.value = list
        Prefs.savePlayers(context, list)
    }
    
    fun removeSetupPlayer(id: Int, context: Context) {
        if (_setupPlayers.value.size <= 3) return
        val list = _setupPlayers.value.filter { it.id != id }
        _setupPlayers.value = list
        Prefs.savePlayers(context, list)
    }

    fun startGame() {
        val sp = _setupPlayers.value
        val initialStacks = sp.associate { it.id to it.stack }
        val players = sp.map { Player(it.id, it.name, it.stack) }
        
        startHand(GameState(players = players, initialStacks = initialStacks, isHiLo = isHiLo))
        _screen.value = Screen.Game
    }

    private fun startHand(baseState: GameState) {
        var dealerIdx = baseState.dealerIdx
        var loop = 0
        while (baseState.players[dealerIdx].isStrikeOut && loop < baseState.players.size) {
            dealerIdx = (dealerIdx + 1) % baseState.players.size
            loop++
        }
        
        val activeCount = baseState.players.count { !it.isStrikeOut }
        if (activeCount < 2) {
            _screen.value = Screen.EndGame
            return
        }

        val sbIdx = nextActive(dealerIdx, baseState.players)
        val bbIdx = nextActive(sbIdx, baseState.players)
        var firstActorIdx = nextActive(bbIdx, baseState.players)
        if (activeCount == 2) firstActorIdx = dealerIdx

        val sbAmount = baseState.smallBlind
        val bbAmount = baseState.bigBlind

        val newPlayers = baseState.players.mapIndexed { index, p ->
            if (p.isStrikeOut) p.copy(currentBet = 0, totalInvested = 0, isFolded = true)
            else {
                var bet = 0
                if (index == sbIdx) bet = minOf(p.stack, sbAmount)
                if (index == bbIdx) bet = minOf(p.stack, bbAmount)
                p.copy(
                    currentBet = bet,
                    stack = p.stack - bet,
                    totalInvested = bet,
                    isFolded = false,
                    isAllIn = (p.stack - bet == 0)
                )
            }
        }

        _state.value = baseState.copy(
            players = newPlayers,
            dealerIdx = dealerIdx,
            activePlayerId = newPlayers[firstActorIdx].id,
            mainPot = 0,
            sidePots = emptyList(),
            highestBet = bbAmount,
            lastAggressorId = -1,
            street = Street.PRE_FLOP,
            logs = listOf(ActionLog("Hand Started. D: ${newPlayers[dealerIdx].name}")),
            history = emptyList(),
            settlementResults = null,
            actedPlayerIdsThisStreet = emptySet()
        )
    }

    private fun nextActive(current: Int, players: List<Player>): Int {
        var next = (current + 1) % players.size
        while (players[next].isStrikeOut || players[next].isFolded || players[next].isAllIn) {
            next = (next + 1) % players.size
        }
        return next
    }

    private fun saveHistory() {
        val s = _state.value
        _state.value = s.copy(history = s.history + s.copy(history = emptyList()))
    }

    fun undo() {
        val s = _state.value
        if (s.history.isNotEmpty()) {
            _state.value = s.history.last().copy(history = s.history.dropLast(1))
        }
    }
    
    fun fold() {
        saveHistory()
        val s = _state.value
        val p = s.players.find { it.id == s.activePlayerId } ?: return
        
        val newPlayers = s.players.map { if (it.id == p.id) it.copy(isFolded = true) else it }
        val newLogs = s.logs + ActionLog("${p.name} Folds")
        
        advance(s.copy(players = newPlayers, logs = newLogs, actedPlayerIdsThisStreet = s.actedPlayerIdsThisStreet + p.id))
    }
    
    fun callOrCheck() {
        saveHistory()
        val s = _state.value
        val p = s.players.find { it.id == s.activePlayerId } ?: return
        
        val diff = s.highestBet - p.currentBet
        val callAmount = minOf(diff, p.stack)
        
        val newPlayers = s.players.map { 
            if (it.id == p.id) {
                it.copy(currentBet = it.currentBet + callAmount, stack = it.stack - callAmount, totalInvested = it.totalInvested + callAmount, isAllIn = (it.stack - callAmount == 0))
            } else it
        }
        
        val actionName = if (callAmount == 0) "Checks" else if (newPlayers.find{it.id==p.id}?.isAllIn == true) "Calls ALL-IN" else "Calls $callAmount"
        val newLogs = s.logs + ActionLog("${p.name} $actionName")
        
        advance(s.copy(players = newPlayers, logs = newLogs, actedPlayerIdsThisStreet = s.actedPlayerIdsThisStreet + p.id))
    }

    fun betOrRaise(amount: Int) {
        saveHistory()
        val s = _state.value
        val p = s.players.find { it.id == s.activePlayerId } ?: return
        
        val diff = amount - p.currentBet
        val realBet = minOf(diff, p.stack)
        val newTotalBet = p.currentBet + realBet
        
        val newPlayers = s.players.map { 
            if (it.id == p.id) {
                it.copy(currentBet = newTotalBet, stack = it.stack - realBet, totalInvested = it.totalInvested + realBet, isAllIn = (it.stack - realBet == 0))
            } else it
        }
        
        val isRaise = s.highestBet > 0 && newTotalBet > s.highestBet
        val actionName = if (newPlayers.find{it.id==p.id}?.isAllIn == true) "ALL-IN ($newTotalBet)" else if (isRaise) "Raises to $newTotalBet" else "Bets $newTotalBet"
        val newLogs = s.logs + ActionLog("${p.name} $actionName")
        
        advance(s.copy(
            players = newPlayers, 
            logs = newLogs, 
            actedPlayerIdsThisStreet = s.actedPlayerIdsThisStreet + p.id,
            highestBet = maxOf(s.highestBet, newTotalBet),
            lastAggressorId = p.id
        ))
    }

    fun pot() {
        val s = _state.value
        val p = s.players.find { it.id == s.activePlayerId } ?: return
        val sumOfBets = s.players.sumOf { it.currentBet }
        val newBet = sumOfBets + s.mainPot + 2 * s.highestBet - p.currentBet
        betOrRaise(newBet)
    }

    private fun advance(s: GameState) {
        val activePlayers = s.players.filter { !it.isFolded }
        if (activePlayers.size == 1) {
            endHandEarly(s, activePlayers.first())
            return
        }

        val mustAct = s.players.filter { !it.isFolded && !it.isAllIn }
        val allActed = mustAct.all { s.actedPlayerIdsThisStreet.contains(it.id) }
        val allMatched = mustAct.all { it.currentBet == s.highestBet }

        if ((allActed && allMatched) || (mustAct.size <= 1 && allMatched)) {
            nextStreet(s)
        } else {
            val nextId = s.players[nextActive(s.players.indexOfFirst { it.id == s.activePlayerId }, s.players)].id
            _state.value = s.copy(activePlayerId = nextId)
        }
    }

    private fun nextStreet(s: GameState) {
        val totalPotThisStreet = s.players.sumOf { it.currentBet }
        val newMain = s.mainPot + totalPotThisStreet
        val newPlayers = s.players.map { it.copy(currentBet = 0) }

        if (s.street == Street.RIVER || s.players.count { !it.isFolded && !it.isAllIn } <= 1) {
            _state.value = s.copy(
                players = newPlayers, mainPot = newMain, highestBet = 0, street = Street.RIVER,
                logs = s.logs + ActionLog("All actions complete. Ready to settle.")
            )
        } else {
            val nextSt = Street.values()[s.street.ordinal + 1]
            val firstActorIdx = nextActive(s.dealerIdx, newPlayers)
            _state.value = s.copy(
                players = newPlayers, mainPot = newMain, highestBet = 0, lastAggressorId = -1,
                street = nextSt, actedPlayerIdsThisStreet = emptySet(), activePlayerId = newPlayers[firstActorIdx].id,
                logs = s.logs + ActionLog("--- ${nextSt.name} ---")
            )
        }
    }

    private fun endHandEarly(s: GameState, winner: Player) {
        val totalPot = s.mainPot + s.players.sumOf { it.currentBet }
        val results = s.players.map { p -> PnLResult(p.id, p.name, if (p.id == winner.id) totalPot - p.totalInvested else -p.totalInvested) }
        val newPlayers = s.players.map { if (it.id == winner.id) it.copy(stack = it.stack + totalPot) else it }
        _state.value = s.copy(players = newPlayers, settlementResults = results, logs = s.logs + ActionLog("${winner.name} wins $totalPot (Others folded)"))
    }

    fun settle(highWinners: Set<Int>, lowWinners: Set<Int>) {
        val s = _state.value
        val totalPot = s.mainPot + s.players.sumOf { it.currentBet }
        val payouts = mutableMapOf<Int, Int>()
        
        var remainder = 0
        if (s.isHiLo && lowWinners.isNotEmpty()) {
            val highShare = totalPot / 2
            val lowShare = totalPot / 2
            remainder = totalPot % 2

            if (highWinners.isNotEmpty()) {
                val perHigh = highShare / highWinners.size
                remainder += highShare % highWinners.size
                highWinners.forEach { payouts[it] = (payouts[it] ?: 0) + perHigh }
            }
            if (lowWinners.isNotEmpty()) {
                val perLow = lowShare / lowWinners.size
                remainder += lowShare % lowWinners.size
                lowWinners.forEach { payouts[it] = (payouts[it] ?: 0) + perLow }
            }
        } else {
            if (highWinners.isNotEmpty()) {
                val perHigh = totalPot / highWinners.size
                remainder = totalPot % highWinners.size
                highWinners.forEach { payouts[it] = (payouts[it] ?: 0) + perHigh }
            }
        }

        val results = s.players.map { p ->
            val winAmount = payouts[p.id] ?: 0
            val scoop = s.isHiLo && highWinners.contains(p.id) && lowWinners.contains(p.id) && highWinners.size == 1 && lowWinners.size == 1
            PnLResult(p.id, p.name, winAmount - p.totalInvested, scoop)
        }
        
        val newPlayers = s.players.map { p ->
            val winAmount = payouts[p.id] ?: 0
            val newStack = p.stack + winAmount
            p.copy(stack = newStack, isStrikeOut = newStack == 0)
        }
        
        _state.value = s.copy(players = newPlayers, settlementResults = results, mainPot = remainder)
    }

    fun nextHand() {
        val s = _state.value
        if (s.isEndGame) {
            _screen.value = Screen.EndGame
            return
        }
        startHand(s.copy(dealerIdx = (s.dealerIdx + 1) % s.players.size))
    }

    fun rebuy(playerId: Int, amount: Int) {
        val s = _state.value
        val newPlayers = s.players.map { if (it.id == playerId) it.copy(stack = it.stack + amount, isStrikeOut = false) else it }
        _state.value = s.copy(players = newPlayers)
    }

    fun setEndGame() {
        val s = _state.value
        _state.value = s.copy(isEndGame = true)
        if (s.settlementResults != null) _screen.value = Screen.EndGame
    }
    
    fun returnToSetup() {
        _screen.value = Screen.Setup
    }
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
val CallWhite = Color(0xFFFFFFFF)
val RaiseGreen = Color(0xFF22C55E)
val GlassWhite = Color.White.copy(alpha = 0.06f)
val GlassBorder = Color.White.copy(alpha = 0.15f)
val ScoopGold = Color(0xFFFFD700)
val ScoopBlack = Color(0xFF111111)

@Composable
fun PlODealerApp() {
    val viewModel: GameViewModel = viewModel()
    val screen by viewModel.screen.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.initSetup(context) }

    when (screen) {
        Screen.Setup -> SetupScreen(viewModel)
        Screen.Game -> GameScreen(viewModel)
        Screen.EndGame -> EndGameScreen(viewModel)
    }
}

@Composable
fun SetupScreen(viewModel: GameViewModel) {
    val setupPlayers by viewModel.setupPlayers.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var editPlayerId by remember { mutableStateOf<Int?>(null) }
    
    Scaffold(containerColor = CasinoBgDark, contentColor = Color.White) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("OMAHA SETUP", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Emerald400)
            Spacer(Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hi/Lo (8-or-Better)", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.isHiLo, onCheckedChange = { viewModel.isHiLo = it })
            }
            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(setupPlayers.size) { index ->
                    val sp = setupPlayers[index]
                    Row(
                        modifier = Modifier.fillMaxWidth().background(GlassWhite, RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).clickable { editPlayerId = sp.id }) {
                            Text(sp.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Stack: ${sp.stack}", color = Emerald300, fontSize = 14.sp)
                        }
                        Column {
                            IconButton(onClick = { viewModel.moveSetupPlayer(index, true, context) }) {
                                Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.moveSetupPlayer(index, false, context) }) {
                                Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { viewModel.removeSetupPlayer(sp.id, context) }) {
                            Icon(Icons.Default.Clear, "Remove", tint = FoldRed)
                        }
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { viewModel.addSetupPlayer(context) }, enabled = setupPlayers.size < 5) { Text("+ Add Player") }
                Button(
                    onClick = { viewModel.startGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = CasinoBgDark)
                ) { Text("START GAME", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (editPlayerId != null) {
        val sp = setupPlayers.find { it.id == editPlayerId }
        if (sp != null) {
            var tempName by remember { mutableStateOf(sp.name) }
            var tempStack by remember { mutableStateOf(sp.stack.toString()) }
            AlertDialog(
                onDismissRequest = { editPlayerId = null },
                title = { Text("Edit Player") },
                text = {
                    Column {
                        OutlinedTextField(value = tempName, onValueChange = { tempName = it }, label = { Text("Name") })
                        OutlinedTextField(value = tempStack, onValueChange = { tempStack = it }, label = { Text("Stack") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateSetupPlayer(sp.id, tempName, tempStack.toIntOrNull() ?: 1000, context)
                        editPlayerId = null
                    }) { Text("Save") }
                }
            )
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    
    var showLogs by remember { mutableStateOf(false) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showBetDialogFor by remember { mutableStateOf<Int?>(null) }
    var showRebuyDialogFor by remember { mutableStateOf<Int?>(null) }

    if (state.settlementResults != null) {
        PnLScreen(
            results = state.settlementResults!!,
            isHiLo = state.isHiLo,
            onNextHand = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.nextHand()
            }
        )
        return
    }

    val headerColor = when(state.street) {
        Street.PRE_FLOP -> Color(0xFF1E3A8A) // Blue
        Street.FLOP -> Color(0xFF065F46) // Green
        Street.TURN -> Color(0xFF9A3412) // Orange
        Street.RIVER -> Color(0xFF991B1B) // Red
    }

    Scaffold(containerColor = CasinoBgDark, contentColor = Color.White) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header
            Box(modifier = Modifier.fillMaxWidth().background(headerColor).padding(16.dp)) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row {
                            IconButton(onClick = { viewModel.undo() }) { Icon(Icons.Default.ArrowBack, "Undo", tint = Color.White) }
                            IconButton(onClick = { showLogs = !showLogs }) { Icon(Icons.Default.List, "Logs", tint = Color.White) }
                        }
                        Text(state.street.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White.copy(0.8f), modifier = Modifier.padding(top=12.dp))
                        if (state.street == Street.RIVER || state.players.count{!it.isFolded && !it.isAllIn} <= 1) {
                            Button(onClick = { showSettleDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Emerald500)) {
                                Text("SETTLE", color = CasinoBgDark, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                    }
                    Text("POT: ${state.mainPot}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = GoldYellow)
                    
                    val activePlayer = state.players.find { it.id == state.activePlayerId }
                    val minBet = state.highestBet + (if (state.highestBet == 0) state.bigBlind else (state.highestBet - (activePlayer?.currentBet ?: 0)).coerceAtLeast(state.bigBlind))
                    
                    val diff = state.highestBet - (activePlayer?.currentBet ?: 0)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)) { append("BET RANGE: $minBet ") }
                            withStyle(SpanStyle(color = Color.White.copy(0.6f), fontSize = 12.sp)) { append("(+${diff.coerceAtLeast(0)})") }
                            append(" - MAX")
                        }
                    )
                }
            }

            if (showLogs) {
                LazyColumn(modifier = Modifier.weight(0.3f).background(Color.Black.copy(0.5f)).padding(8.dp)) {
                    items(state.logs.reversed()) { log ->
                        Text(log.msg, fontSize = 12.sp, color = Color.White.copy(0.7f))
                    }
                }
            }

            // Players
            LazyColumn(modifier = Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.players) { p ->
                    val isActive = p.id == state.activePlayerId
                    val alpha = if (isActive) 1f else if (p.isFolded || p.isStrikeOut) 0.3f else 0.7f
                    val border = if (p.id == state.lastAggressorId) BorderStroke(2.dp, FoldRed) else if (isActive) BorderStroke(2.dp, GoldYellow) else null
                    
                    Box(modifier = Modifier.fillMaxWidth().alpha(alpha).background(GlassWhite, RoundedCornerShape(12.dp)).border(border ?: BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(12.dp)).padding(12.dp)) {
                        if (p.isStrikeOut) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${p.name} - STRIKE OUT", color = FoldRed, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                Button(onClick = { showRebuyDialogFor = p.id }, colors = ButtonDefaults.buttonColors(containerColor = GoldYellow)) { Text("REBUY", color = Color.Black) }
                            }
                        } else {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val dealers = state.players.filter { !it.isStrikeOut }
                                        val d = dealers.getOrNull(state.dealerIdx % dealers.size.coerceAtLeast(1))?.id
                                        val sb = dealers.getOrNull((state.dealerIdx + 1) % dealers.size.coerceAtLeast(1))?.id
                                        val bb = dealers.getOrNull((state.dealerIdx + 2) % dealers.size.coerceAtLeast(1))?.id
                                        
                                        if (p.id == d) Text(" D ", modifier = Modifier.background(Color.White, CircleShape).padding(2.dp), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        else if (p.id == sb) Text(" SB ", modifier = Modifier.background(GoldYellow, CircleShape).padding(2.dp), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        else if (p.id == bb) Text(" BB ", modifier = Modifier.background(CallWhite, CircleShape).padding(2.dp), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (p.isAllIn) Text(" [ALL IN]", color = FoldRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                                    }
                                    Text("BET: ${p.currentBet}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Emerald300)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Stack: ${p.stack}", color = Color.White.copy(0.7f))
                                    Text("Total Inv: ${p.totalInvested}", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                
                                if (isActive && !p.isFolded && !p.isAllIn) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { viewModel.fold() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = FoldRed)) { Text("F", fontWeight = FontWeight.Black) }
                                        val cText = if (p.currentBet == state.highestBet) "Ch" else "C"
                                        Button(onClick = { viewModel.callOrCheck() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CallWhite, contentColor = Color.Black)) { Text(cText, fontWeight = FontWeight.Black) }
                                        Button(onClick = { showBetDialogFor = p.id }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = RaiseGreen, contentColor = Color.Black)) { Text("B", fontWeight = FontWeight.Black) }
                                        Button(onClick = { viewModel.pot() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PotGold, contentColor = Color.Black)) { Text("P", fontWeight = FontWeight.Black) }
                                    }
                                    if (p.stack <= state.highestBet - p.currentBet || p.stack <= state.mainPot) {
                                        Button(onClick = { viewModel.betOrRaise(p.stack + p.currentBet) }, modifier = Modifier.fillMaxWidth().padding(top=4.dp), colors = ButtonDefaults.buttonColors(containerColor = FoldRed)) { Text("ALL IN", fontWeight = FontWeight.Black) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // End Game Button
            var isEndGamePressed by remember { mutableStateOf(false) }
            val pressProgress by animateFloatAsState(targetValue = if (isEndGamePressed) 1f else 0f, animationSpec = tween(if (isEndGamePressed) 2000 else 500))
            
            LaunchedEffect(pressProgress) {
                if (pressProgress == 1f) {
                    viewModel.setEndGame()
                }
            }
            
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).background(Color.Black).pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isEndGamePressed = true
                            tryAwaitRelease()
                            isEndGamePressed = false
                        }
                    )
                },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxWidth(pressProgress).fillMaxHeight().background(FoldRed).align(Alignment.CenterStart))
                Text(if (state.isEndGame) "END GAME ACTIVATED" else "HOLD TO END GAME", color = if (state.isEndGame) Color.White else Color.Gray, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
    }

    if (showBetDialogFor != null) {
        val p = state.players.find { it.id == showBetDialogFor }
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBetDialogFor = null },
            title = { Text("Bet / Raise - ${p?.name}") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Amount") }) },
            confirmButton = {
                Button(onClick = { viewModel.betOrRaise(input.toIntOrNull() ?: 0); showBetDialogFor = null }) { Text("Confirm") }
            }
        )
    }

    if (showRebuyDialogFor != null) {
        var input by remember { mutableStateOf("1000") }
        AlertDialog(
            onDismissRequest = { showRebuyDialogFor = null },
            title = { Text("Rebuy") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Amount") }) },
            confirmButton = {
                Button(onClick = { viewModel.rebuy(showRebuyDialogFor!!, input.toIntOrNull() ?: 1000); showRebuyDialogFor = null }) { Text("Rebuy") }
            }
        )
    }

    if (showSettleDialog) {
        var selectedHighs by remember { mutableStateOf(setOf<Int>()) }
        var selectedLows by remember { mutableStateOf(setOf<Int>()) }
        val activePlayers = state.players.filter { !it.isFolded }

        AlertDialog(
            onDismissRequest = { showSettleDialog = false },
            title = { Text("Settle Pot") },
            text = {
                Column {
                    Text("High Winner(s):", fontWeight = FontWeight.Bold)
                    activePlayers.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedHighs = if (selectedHighs.contains(p.id)) selectedHighs - p.id else selectedHighs + p.id }) {
                            Checkbox(checked = selectedHighs.contains(p.id), onCheckedChange = null)
                            Text(p.name)
                        }
                    }
                    if (state.isHiLo) {
                        Spacer(Modifier.height(16.dp))
                        Text("Low Winner(s):", fontWeight = FontWeight.Bold)
                        activePlayers.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedLows = if (selectedLows.contains(p.id)) selectedLows - p.id else selectedLows + p.id }) {
                                Checkbox(checked = selectedLows.contains(p.id), onCheckedChange = null)
                                Text(p.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.settle(selectedHighs, selectedLows); showSettleDialog = false }, enabled = selectedHighs.isNotEmpty()) { Text("Settle") }
            }
        )
    }
}

@Composable
fun PnLScreen(results: List<PnLResult>, isHiLo: Boolean, onNextHand: () -> Unit) {
    val hasScoop = results.any { it.scoop }
    val bgColor = if (hasScoop) ScoopBlack else CasinoBgDark
    val highlightColor = if (hasScoop) ScoopGold else Emerald400

    Scaffold(containerColor = bgColor, contentColor = Color.White) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(if (hasScoop) "🏆 SCOOP 🏆" else "HAND SETTLED", fontSize = 32.sp, fontWeight = FontWeight.Black, color = highlightColor)
                Spacer(Modifier.height(32.dp))

                results.forEach { r ->
                    val color = if (r.netProfit > 0) highlightColor else if (r.netProfit == 0) Color.Gray else FoldRed
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White.copy(0.1f), RoundedCornerShape(8.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (r.netProfit > 0) "+${r.netProfit}" else "${r.netProfit}", color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Button(onClick = onNextHand, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = highlightColor, contentColor = Color.Black)) {
                Text("NEXT HAND", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun EndGameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(containerColor = CasinoBgDark, contentColor = Color.White) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("END GAME REPORT", fontSize = 32.sp, fontWeight = FontWeight.Black, color = GoldYellow)
            Spacer(Modifier.height(24.dp))
            
            val profits = state.players.map { p ->
                val initial = state.initialStacks[p.id] ?: 0
                val net = p.stack - initial
                Pair(p, net)
            }.sortedByDescending { it.second }
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(profits) { (p, net) ->
                    val color = if (net > 0) Emerald400 else if (net < 0) FoldRed else Color.Gray
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Final Stack: ${p.stack}", color = Color.White.copy(0.7f))
                        }
                        Text(if (net > 0) "+$net" else "$net", color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            
            Button(onClick = { viewModel.returnToSetup() }, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = GlassWhite)) {
                Text("RETURN TO LOBBY", fontSize = 18.sp)
            }
        }
    }
}
"""
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(code)

if __name__ == "__main__":
    write_code()
