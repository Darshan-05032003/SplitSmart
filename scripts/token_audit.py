#!/usr/bin/env python3
"""Token audit: screen code must use design-system tokens only.

Scans Kotlin under app/src/{main,debug}/kotlin/com/darshan/splitsmart/ui/, except ui/designsystem/ (where the tokens are
defined) and ui/theme/, for:

  colour    raw hex colours and named colours (Color(0xFF...), Color(1f, ...), "#RRGGBB", Color.White ...)
  size      non-zero .dp / .sp literals
  duration  numbers inside animation specs (tween, snap, delay, durationMillis, delayMillis, keyframes), raw spring(...),
            literal .milliseconds/.seconds and *_MS / *MILLIS constants
  easing    easing curves (CubicBezierEasing, LinearEasing, FastOutSlowInEasing, Ease*, Easing { })

A line that must break a rule says why with a trailing `// token-audit: allow (reason)` comment, on the line or the line
above. Comment lines and imports are ignored. TokenAuditTest runs the same rules on the JVM, so the test gate enforces them.

The screens built before the design system are listed in scripts/token_audit_legacy.txt with the number of raw values each
still has. That number may only go down: a rebuilt screen is removed from the list and must then be clean. Every other file
must be clean now.

Usage: scripts/token_audit.py [project root]          exit 1 when anything is found
       scripts/token_audit.py --counts [project root] print per-file counts (to refresh the legacy list)
"""
import re
import sys
from pathlib import Path

RULES = {
    "colour": r'Color\(\s*0x|Color\(\s*[0-9]|"#[0-9A-Fa-f]{3,8}"|parseColor\(|Color\.(White|Black|Red|Green|Blue|Gray|Yellow|Cyan|Magenta|DarkGray|LightGray)\b',
    "size": r'(?<![\w.])(?!0+(\.0+)?f?\.(dp|sp)\b)\d+(\.\d+)?f?\.(dp|sp)\b',
    "duration": r'\btween\(\s*\d|\bsnap\(\s*\d|\bdelay\(\s*\d|durationMillis\s*=\s*\d|delayMillis\s*=\s*\d|\bkeyframes\s*\{|\bspring\s*(<[^>]*>)?\(|\d+\.(milliseconds|seconds)\b|const val \w*(_MS|MILLIS)\w*\s*(:\s*\w+)?\s*=\s*\d',
    "easing": r'CubicBezierEasing\(|\b(LinearEasing|FastOutSlowInEasing|LinearOutSlowInEasing|FastOutLinearInEasing)\b|\bEase(In|Out|InOut)\w*\b|\bEasing\s*\{',
}
COMPILED = {name: re.compile(pattern) for name, pattern in RULES.items()}
ALLOW = "token-audit: allow"
SCANNED = ("app/src/main/kotlin/com/darshan/splitsmart/ui", "app/src/debug/kotlin/com/darshan/splitsmart/ui")
EXCLUDED = ("/ui/designsystem/", "/ui/theme/")
LEGACY = "scripts/token_audit_legacy.txt"


def screen_files(root: Path):
    for base in SCANNED:
        for path in sorted((root / base).rglob("*.kt")):
            if not any(part in path.as_posix() for part in EXCLUDED):
                yield path


def violations(path: Path):
    previous = ""
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        allowed_above = previous.startswith("//") and ALLOW in previous
        skip = stripped.startswith(("import ", "package ", "//", "*", "/*")) or ALLOW in line or allowed_above
        previous = stripped
        if skip:
            continue
        code = line.split("//", 1)[0]
        for name, rule in COMPILED.items():
            if rule.search(code):
                yield number, name, stripped


def legacy_budget(root: Path):
    budget = {}
    path = root / LEGACY
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                name, count = line.rsplit(" ", 1)
                budget[name] = int(count)
    return budget


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    root = Path(args[0]) if args else Path(__file__).resolve().parent.parent
    files = list(screen_files(root))
    if "--counts" in sys.argv:
        for path in files:
            count = sum(1 for _ in violations(path))
            if count:
                print(f"{path.relative_to(root).as_posix()} {count}")
        return 0
    budget = legacy_budget(root)
    failures = 0
    for path in files:
        name = path.relative_to(root).as_posix()
        found = list(violations(path))
        allowed = budget.get(name, 0)
        if len(found) > allowed:
            failures += 1
            print(f"{name}: {len(found)} raw value(s), allowed {allowed}")
            if allowed == 0:
                for number, rule, text in found:
                    print(f"  {number}: [{rule}] {text}")
    if failures:
        print(f"token audit: {failures} file(s) over budget in {len(files)} files", file=sys.stderr)
        return 1
    print(f"token audit: clean ({len(files)} files, {len(budget)} legacy screens still to rebuild)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
