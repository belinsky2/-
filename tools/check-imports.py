#!/usr/bin/env python3
"""
Проверка импортов внутри проекта.

Ловит класс ошибок, из-за которого сборка уже падала: тип переезжает в другой
модуль, а импортирующие файлы остаются со старым путём. Android-часть локально
не компилируется, поэтому такая ошибка иначе доходит только до CI — а это
несколько минут на каждую опечатку.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PREFIX = "ru.punchline."

# Генерируются Android Gradle Plugin во время сборки, в исходниках их нет.
GENERATED = {"R", "BuildConfig"}

declared: dict[str, set[str]] = {}
imports: list[tuple[Path, int, str]] = []

TOP_LEVEL = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public |internal |private |abstract |open |sealed |data |value |enum |annotation |fun )*"
    r"(?:class|interface|object|typealias)\s+(\w+)"
)
# Функции-расширения объявляются как `fun Receiver.name(...)`: без учёта
# получателя проверка считала объявленным имя типа, а не функции, и потом
# ругалась на совершенно корректный импорт.
TOP_FUN_OR_VAL = re.compile(
    r"^(?:public |internal )?(?:fun|val|const val)\s+"
    r"(?:<[^>]+>\s+)?"          # обобщённые параметры
    r"(?:[\w.<>?, ]+\.)?"       # получатель расширения
    r"(\w+)"
)

for path in ROOT.rglob("*.kt"):
    if "/build/" in str(path):
        continue
    package = None
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith("package "):
            package = stripped.removeprefix("package ").strip()
        elif stripped.startswith("import " + PREFIX):
            imports.append((path, lineno, stripped.removeprefix("import ").strip()))
        elif package:
            match = TOP_LEVEL.match(stripped) or TOP_FUN_OR_VAL.match(stripped)
            if match and not line.startswith((" ", "\t")):
                declared.setdefault(package, set()).add(match.group(1))

# Отдельная проверка: делегат `by remember { mutableStateOf(...) }` работает
# только при импорте операторов getValue/setValue. Без них компилятор ругается
# на «no method setValue», и понять это по сообщению непросто. Ошибка сделана
# в проекте дважды, поэтому проверяется механически.
delegate_problems = []
for path in ROOT.rglob("*.kt"):
    if "/build/" in str(path):
        continue
    text = path.read_text(encoding="utf-8")
    if not re.search(r"\bby\s+remember\b|\bby\s+rememberSaveable\b", text):
        continue
    if "mutableStateOf" not in text and "mutableIntStateOf" not in text:
        continue
    missing = [
        name for name in ("getValue", "setValue")
        if f"import androidx.compose.runtime.{name}" not in text
    ]
    if missing:
        delegate_problems.append((path, missing))

if delegate_problems:
    print("Делегат `by remember { mutableStateOf }` без импорта операторов:\n")
    for path, missing in delegate_problems:
        print(f"  {path.relative_to(ROOT)}  — не хватает: {', '.join(missing)}")
    print()

broken = []
for path, lineno, statement in imports:
    target = statement.removesuffix(".*")
    if statement.endswith(".*"):
        if target not in declared:
            broken.append((path, lineno, statement, "нет такого пакета"))
        continue
    package, _, name = target.rpartition(".")
    if name in GENERATED:
        continue
    if package not in declared:
        broken.append((path, lineno, statement, "нет такого пакета"))
    elif name not in declared[package]:
        # Может быть членом enum/companion — проверяем, объявлено ли имя где-то ещё.
        elsewhere = [p for p, names in declared.items() if name in names]
        hint = f"есть в {', '.join(elsewhere)}" if elsewhere else "нигде не объявлено"
        broken.append((path, lineno, statement, hint))

if broken or delegate_problems:
    if not broken:
        sys.exit(1)
    print("Импорты, которые никуда не ведут:\n")
    for path, lineno, statement, why in broken:
        print(f"  {path.relative_to(ROOT)}:{lineno}  {statement}  — {why}")
    sys.exit(1)

print(f"Проверено импортов: {len(imports)}. Все разрешаются.")
