with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

import re
target = """                        }
                        }
                        }
                        
                        if \(state.isSettlementPhase\) \{"""

replacement = """                        }
                        }
                        
                        if (state.isSettlementPhase) {"""

code = re.sub(target, replacement, code)
with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)
