**OmahaCal**

A precise, real-time score tracking and table management engine designed for live Pot-Limit Omaha (PLO) cash games. Built entirely with Android, Kotlin, and Jetpack Compose.

This system focuses on solving the most complex challenges in live games: multi-side-pot calculations, strict pot-limit rule enforcement, and professional post-game data tracking. It provides game hosts and dealers with a bulletproof ledger and strict state-machine logic.

Core Features
Math & Pot Engine
Strict Pot-Limit Calculation: Built-in standard PLO math formulas. It calculates the legal Max Bet in real-time by aggregating the current pot and dead money, mechanically preventing any over-betting scenarios.

Automated Side Pots Resolution: Flawlessly handles complex cascading All-in scenarios involving 4+ players. Based on each player's actual total investment, it automatically splits the Main Pot and infinite Side Pots, accurately determining the eligible contenders for each.

Uncalled Bet & Refund Protocol: Automatically calculates and returns uncalled excess chips. During the settlement phase, side pots with only a single contributor are strictly designated with the professional term [REFUND] rather than being evaluated as a regular win.

Odd Chip Split: In Split Pot scenarios where the total amount cannot be evenly divided by the minimum physical chip denomination (e.g., a $10 chip), the system automatically allocates the remaining indivisible chip to the player in the worst physical position (closest to the Small Blind).

Table Management
Inline Pot Breakdown Bar: Features an expandable, segmented proportion bar beneath the main pot value. It displays each player's financial contribution percentage and absolute value in descending order, offering an instant visual breakdown of the pot's composition.

Smart Active Player Focus: In 8-handed deep-stack games, the system automatically calculates the minimum scroll offset as the action rotates, smoothly bringing the current active player's card into the viewport without manual scrolling.

In-Game Mis-touch Shield: Upon entering active game mode, the system locks non-essential interactions (such as editing player names or triggering ripple effects) to ensure absolute UI stability during high-frequency dealing operations.

Analytics & Pro HUD
Zero-Sum Session Ledger: Automatically generates a structured receipt at the end of a session, detailing total buy-ins, final cash-outs, and net profits for every player, guaranteeing a strictly zero-sum financial flow.

Pro HUD Stats Tracker: Silently tracks player actions hand-by-hand in the background, quantifying core poker metrics:

VPIP: Voluntarily Put $ in Pot

PFR: Pre-Flop Raise %

AGQ: Aggression Quotient

WTSD% / W$SD%: Went To Showdown % & Won$ at Showdown %

Tech Stack
Language & UI: Kotlin / Jetpack Compose

Architecture: Unidirectional Data Flow (UDF), fully state-driven UI.

Environment: 100% Offline. Highly cohesive local computation with zero network dependency.
