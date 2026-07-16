with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    count += line.count("{")
    count -= line.count("}")
    if count < 0:
        print(f"Negative at {i+1}: {line.strip()}")
        break
print("Final count:", count)
