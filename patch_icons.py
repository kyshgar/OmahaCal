import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace('Text("⚙️", fontSize = 24.sp)', 'Icon(imageVector = androidx.compose.material.icons.filled.Menu, contentDescription = "Settings", tint = Color.White.copy(0.7f))')
content = content.replace('Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Emerald400)', 'Icon(imageVector = androidx.compose.material.icons.filled.Menu, contentDescription = "Settings", tint = Emerald400)')

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
print("Icons replaced")
