#!/usr/bin/env python3
"""Count LOC of the methods of every @Service class (rule of Lab 2 / Report 5.1):
from the method declaration line to its closing brace, WITHOUT blank lines and comment-only lines
(Javadoc, // and /* */ lines). Annotations above the method are not counted.

Usage:  python3 tools/count_loc.py            (public methods of every @Service class)
        python3 tools/count_loc.py --all      (also private / static helper methods)
        python3 tools/count_loc.py --csv      (CSV: file,class,method,start,end,loc)
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "backend/src/main/java"
PUBLIC = re.compile(r"^    public\s+(?!class|interface|enum|record)[\w<>\[\],.? ]+\s+(\w+)\s*\(")
ANY = re.compile(r"^    (?:public|private|protected|static)[\w<>\[\],.? ]*\s+(\w+)\s*\(")


def code_part(raw, state):
    """Return the code on a line after removing comments; state['block'] tracks /* ... */."""
    text = raw.strip()
    if state["block"]:
        if "*/" not in text:
            return ""
        state["block"] = False
        text = text.split("*/", 1)[1].strip()
    if text.startswith("/*"):
        if "*/" not in text:
            state["block"] = True
            return ""
        text = text.split("*/", 1)[1].strip()
    return "" if text.startswith("//") else text


def methods(path, include_private):
    lines = path.read_text(encoding="utf-8").splitlines()
    decl = ANY if include_private else PUBLIC
    i = 0
    while i < len(lines):
        m = decl.match(lines[i])
        if not m or lines[i].rstrip().endswith(";"):
            i += 1
            continue
        state = {"block": False}
        depth, seen_open, loc, j = 0, False, 0, i
        while j < len(lines):
            code = code_part(lines[j], state)
            if code:
                loc += 1
            no_strings = re.sub(r'"(\\.|[^"\\])*"', '""', code)
            depth += no_strings.count("{") - no_strings.count("}")
            seen_open = seen_open or "{" in no_strings
            if seen_open and depth == 0:
                break
            j += 1
        yield m.group(1), i + 1, j + 1, loc
        i = j + 1


def main():
    rows = []
    for f in sorted(ROOT.rglob("*.java")):
        if "@Service" not in f.read_text(encoding="utf-8"):
            continue
        for name, start, end, loc in methods(f, "--all" in sys.argv):
            rows.append((str(f.relative_to(ROOT.parents[2])), f.stem, name, start, end, loc))
    if "--csv" in sys.argv:
        print("file,class,method,start,end,loc")
        for r in rows:
            print(",".join(map(str, r)))
        return
    print(f"{'Class':<26}{'Method':<26}{'Lines':<12}{'LOC':>5}")
    for _, cls, name, start, end, loc in rows:
        print(f"{cls:<26}{name:<26}{f'{start}-{end}':<12}{loc:>5}")
    print(f"\nTotal: {len(rows)} methods, {sum(r[5] for r in rows)} LOC")


if __name__ == "__main__":
    main()
