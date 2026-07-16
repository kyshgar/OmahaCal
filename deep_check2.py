with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    count += line.count("{")
    count -= line.count("}")
    if i > 750 and count == 0:
        print(f"Zero at {i+1}: {line.strip()}")
