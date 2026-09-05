#!/usr/bin/env python3
"""M2.3 - is a migration reversible?

Reads target/matrix-reversibility/<policy>/config<A>-config<B>/ and reports, for every ordered
configuration pair, whether config A -> config B -> config A returns the V-SUM it started from.

Writes target/matrix-reversibility/README.md.

    python reversibility/compare.py [policy ...]      (default: every policy present)
"""
import re, sys, difflib
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
from pathlib import Path
from collections import OrderedDict, Counter

HERE = Path(__file__).resolve().parent
RESULTS = HERE.parent / "target" / "matrix-reversibility"
N = 9
TWO_COLUMN_SEPARATOR = "|---|---|"


def props(path):
    d = OrderedDict()
    if path.is_file():
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                d[k.strip()] = v.strip()
    return d


def read(path):
    if not path.is_file():
        return None
    return path.read_text(encoding="utf-8", errors="replace").replace("\r\n", "\n")


TAG_DEF = re.compile(r'<([A-Za-z:]+)([^>]*)>?')
ID_ATTR = re.compile(r'\bxmi:id="([^"]+)"')
TOKEN = re.compile(r'(?<![A-Za-z0-9_\-])[A-Za-z0-9_\-]{6,}(?![A-Za-z0-9_\-])')


def canon_uml(text):
    """Map every xmi:id to a content-derived label so references follow the element they point
    at, whatever id it was given in this run. A round trip never reproduces ids."""
    if text is None:
        return None
    labels, used = {}, {}
    for m in TAG_DEF.finditer(text):
        tag, attrs = m.groups()
        ident = ID_ATTR.search(attrs)
        if not ident:
            continue
        t = re.search(r'xmi:type="([^"]+)"', attrs)
        n = re.search(r'\bname="([^"]*)"', attrs)
        label = (t.group(1) if t else tag) + (":" + n.group(1) if n else "")
        count = used.get(label, 0) + 1
        used[label] = count
        labels[ident.group(1)] = label if count == 1 else f"{label}~{count}"
    return TOKEN.sub(lambda m: labels.get(m.group(0), m.group(0)), text)


def classify(a, b):
    if a is None or b is None:
        return "missing", ""
    if a == b:
        return "identical", ""
    la, lb = a.splitlines(), b.splitlines()
    kind = "order" if sorted(la) == sorted(lb) else "content"
    diff = "\n".join(list(difflib.unified_diff(la, lb, "original", "returned", lineterm="", n=1))[:120])
    return kind, diff


MARK = {"identical": "=", "order": "~", "content": "X", "missing": "?"}


def correspondence_count(folder):
    text = read(folder / "consistencymetadata" / "vitruv" / "correspondences.correspondence")
    return None if text is None else text.count("<correspondences")


def write_details(details, policy, a, b, uml, cell, diff):
    (details / f"{policy}-config{a}-config{b}.md").write_text(
        f"# {policy} config{a} -> config{b} -> config{a}\n\n"
        f"## UML, original vs returned ({uml})\n```\n{diff}\n```\n\n"
        f"## Java files differing\n\n{cell.get('javaDiffering') or '(none)'}\n",
        encoding="utf-8")


def collect_rows(root, details, policy):
    """One row per cell that ran, keyed by the (A, B) pair it belongs to."""
    rows = {}
    for a in range(1, N + 1):
        for b in range(1, N + 1):
            d = root / f"config{a}-config{b}"
            p = props(d / "cell.properties")
            if not p:
                continue
            uml, diff = classify(canon_uml(read(d / "original" / "model" / "library.uml")),
                                 canon_uml(read(d / "returned" / "model" / "library.uml")))
            p["uml"] = uml
            p["corrOriginal"] = correspondence_count(d / "original")
            p["corrReturned"] = correspondence_count(d / "returned")
            p["reversible"] = (p.get("javaStrict") == "identical"
                               and uml == "identical"
                               and p.get("ruleHashesEqual") == "true")
            rows[(a, b)] = p
            if diff:
                write_details(details, policy, a, b, uml, p, diff)
    return rows


def criteria_section(off, rows, rev_off):
    loose = sum(1 for k in off if rows[k].get("javaModuloOrder") in ("identical", "order")
                and rows[k]["uml"] in ("identical", "order"))
    java_ok = sum(1 for k in off if rows[k].get("javaStrict") == "identical")
    uml_ok = sum(1 for k in off if rows[k]["uml"] == "identical")
    hash_ok = sum(1 for k in off if rows[k].get("ruleHashesEqual") == "true")
    compiles = sum(1 for k in off if rows[k].get("javacReturned") == "0")
    compiles0 = sum(1 for k in off if rows[k].get("javacOriginal") == "0")
    return ["| criterion | pairs (of %d) |" % len(off),
            TWO_COLUMN_SEPARATOR,
            f"| Java byte-identical | {java_ok} |",
            f"| UML identical up to `xmi:id` | {uml_ok} |",
            f"| rule registry back to config A | {hash_ok} |",
            f"| all three (**M2.3**) | {rev_off} |",
            f"| Java and UML identical up to member order | {loose} |",
            f"| returned V-SUM compiles | {compiles} (original: {compiles0}) |"]


def cell_mark(r):
    if not r:
        return "-"
    bits = MARK.get(r.get("javaStrict"), "?") + MARK.get(r["uml"], "?") + \
        ("=" if r.get("ruleHashesEqual") == "true" else "X")
    tag = "**" + bits + "**" if r["reversible"] else bits
    return f"{tag} / {r.get('javacOriginal', '?')}→{r.get('javacReturned', '?')}"


def matrix_section(rows):
    lines = ["\nCell = Java / UML / registry, then `javac` original → returned.\n",
             "| A \\ B | " + " | ".join(f"config{b}" for b in range(1, N + 1)) + " |",
             "|---|" + "---|" * N]
    for a in range(1, N + 1):
        cells = [cell_mark(rows.get((a, b))) for b in range(1, N + 1)]
        lines.append(f"| **config{a}** | " + " | ".join(cells) + " |")
    return lines


def broken_criteria(r):
    why = []
    if r.get("javaStrict") != "identical":
        why.append("Java")
    if r["uml"] != "identical":
        why.append("UML")
    if r.get("ruleHashesEqual") != "true":
        why.append("registry")
    return why


def failure_section(off, rows):
    broke = Counter()
    for k in off:
        r = rows[k]
        if not r["reversible"]:
            broke[", ".join(broken_criteria(r))] += 1
    if not broke:
        return []
    return ["\nWhat fails, for the pairs that are not reversible:\n",
            "| differs in | pairs |",
            TWO_COLUMN_SEPARATOR] + [f"| {why} | {count} |" for why, count in broke.most_common()]


def differing_files_section(off, rows):
    files = Counter()
    for k in off:
        for f in (rows[k].get("javaDiffering") or "").split():
            files[f] += 1
    if not files:
        return []
    return ["\nJava files that come back different, and in how many pairs:\n",
            "| file | pairs |",
            TWO_COLUMN_SEPARATOR] + [f"| `{f}` | {count} |" for f, count in files.most_common()]


def leg_notes_section(off, rows):
    lines = []
    stuck = [f"{a}→{b}" for (a, b) in off if rows[(a, b)].get("backNothingToMigrate") == "true"]
    if stuck:
        lines.append(f"\nPairs whose **return leg found nothing to migrate**: {len(stuck)} — "
                     + ", ".join(stuck))
    bad = [f"{a}→{b}" for (a, b) in off
           if rows[(a, b)].get("exitForward") != "0" or rows[(a, b)].get("exitBack") != "0"]
    if bad:
        lines.append(f"\nPairs where a leg failed: {len(bad)} — " + ", ".join(bad))
    return lines


def policy_section(policy, rows):
    off = [k for k in rows if k[0] != k[1]]
    diag = [k for k in rows if k[0] == k[1]]
    rev_off = sum(1 for k in off if rows[k]["reversible"])
    rev_diag = sum(1 for k in diag if rows[k]["reversible"])
    lines = [f"\n## `--preserve {policy}`\n",
             f"### M2.3 = **{rev_off} of {len(off)}** configuration pairs are reversible "
             f"({rev_diag} of {len(diag)} trivial self-pairs also hold).\n"]
    lines += criteria_section(off, rows, rev_off)
    lines += matrix_section(rows)
    lines += failure_section(off, rows)
    lines += differing_files_section(off, rows)
    lines += leg_notes_section(off, rows)
    return lines


def main(policies):
    details = RESULTS / "details"
    details.mkdir(parents=True, exist_ok=True)
    out = []
    out.append("# M2.3 - Is a migration reversible?\n")
    out.append("For every ordered pair, the library V-SUM derived with **config A** is migrated to "
               "**config B** and straight back to **config A**, then compared with the V-SUM it started "
               "from. Both legs: `--strategy explicit --dominant uml --mode ids --source-update none "
               "--ask never`, one JVM each.\n")
    out.append("A pair counts as **reversible** when the returned V-SUM has byte-identical Java, a UML "
               "model identical up to `xmi:id` (a round trip never reproduces ids), and a rule registry "
               "equal to the original's. Marks: `=` identical, `~` same lines reordered, `X` differs, "
               "`?` missing.\n")

    for policy in policies:
        root = RESULTS / policy
        if not root.is_dir():
            continue
        rows = collect_rows(root, details, policy)
        if rows:
            out += policy_section(policy, rows)

    (RESULTS / "README.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    given = sys.argv[1:]
    if not given:
        given = sorted(p.name for p in RESULTS.iterdir()
                       if p.is_dir() and p.name not in ("work", "details")) if RESULTS.is_dir() else []
    main(given)
