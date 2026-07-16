    ) {
        if (isCollapsed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.6f))
                    Spacer(Modifier.width(8.dp))
                    if (isStrikeOut) {
                        Text("STRIKE OUT", fontSize = 10.sp, fontWeight = FontWeight.Black, color = FoldRed.copy(0.8f), letterSpacing = 1.sp)
                    } else if (isFolded) {
                        Text("FOLDED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$${player.stack.fmt()}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Emerald400.copy(0.6f))
                    if (isStrikeOut) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onRebuyClick,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500.copy(0.2f), contentColor = Emerald500),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("REBUY", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), verticalArrangement = Arrangement.SpaceBetween) {

                // Top Row: Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Avatar & Name
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (isFolded) Color.DarkGray else Emerald500, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${player.id}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(
                            modifier = Modifier
                                .clickable(onClick = onRenameClick)
                                .padding(end = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = player.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
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
                                Text(
                                    text = "$${player.stack}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Emerald400,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.width(6.dp))
                                if (isActive) {
                                    Text("▶ ACTING", fontSize = 10.sp, color = GoldYellow.copy(alpha = glowAlpha), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                } else if (isFolded) {
                                    Text("Folded", fontSize = 10.sp, color = Color.Gray)
                                } else {
                                    Text("Total Inv: ${player.totalInvested.fmt()}", fontSize = 11.sp, color = Emerald300, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Right: Bet Amount
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

                // Bottom Row: Actions
                if (!isFolded) {
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
