"""Stage 1 verification: mechanical integrity across all content.

Re-proves every gate in one auditable pass and writes a unified report:
- draft gates (approved, non-blank, grade <= 8.0, provenance, glossary)
  reusing drafting.flesch_kincaid_grade / drafting.load_glossary
- align anchors byte-exact against triptych paras (same rule as
  cmd_check_align; kept local to avoid a gita.py import cycle)
- Sanskrit coverage: contiguous 1..N ids, vulgate counts, IAST present
- provenance rule: every align record has telang spans OR notes
  (quarantines must be documented, never silent)
- global: feelings refs resolve; exported bundle matches work files
  (ignoring only the export datestamp)

Writes data/content/work/verify_report.json. Exit 1 on any failure.
"""

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "data" / "content" / "work"
DATA = ROOT / "data" / "content"
sys.path.insert(0, str(ROOT / "tools" / "content"))

from chapters import CHAPTERS, EXPECTED_TOTAL  # noqa: E402
from drafting import flesch_kincaid_grade, load_glossary  # noqa: E402

GRADE_CAP = 8.0


def check_draft_records(ch, glossary, out):
    """Returns (approved_count, ok). Appends failures to out."""
    doc = json.loads((WORK / f"draft_ch{ch:02d}.json").read_text(encoding="utf-8"))
    ok = True
    approved = 0
    for r in doc["records"]:
        tag = r["id"]
        if r.get("status") != "approved":
            out.append({"id": tag, "check": "approved", "detail": r.get("status")})
            ok = False
            continue
        approved += 1
        for field in ("meaning_simple", "takeaway"):
            if not r.get(field, "").strip():
                out.append({"id": tag, "check": f"blank-{field}", "detail": ""})
                ok = False
        grade = flesch_kincaid_grade(r.get("meaning_simple", ""))
        if grade > GRADE_CAP:
            out.append({"id": tag, "check": "grade",
                        "detail": f"{grade:.1f} > {GRADE_CAP}"})
            ok = False
        if not r.get("provenance"):
            out.append({"id": tag, "check": "provenance", "detail": "missing"})
            ok = False
        blob = (r.get("meaning_simple", "") + " " + r.get("takeaway", "")).lower()
        for term, rendered in glossary.items():
            if term.lower() in blob and rendered.lower() not in blob:
                out.append({"id": tag, "check": "glossary",
                            "detail": f"'{term}' without '{rendered}'"})
                ok = False
    return approved, ok


def check_align_records(ch, out):
    """Anchor byte-exactness + quarantine-documentation rule."""
    al = json.loads((WORK / f"align_ch{ch:02d}.json").read_text(encoding="utf-8"))
    tri = json.loads((WORK / f"triptych_ch{ch:02d}.json").read_text(encoding="utf-8"))
    paras = {f"T{i:02d}": p["text"] for i, p in enumerate(
        x for x in tri["telang_paras"] if not x["footnote?"])}
    ok = True
    ids = sorted((r["id"] for r in al["records"]),
                 key=lambda s: int(s.split(":")[1]))
    want = [f"{ch}:{i}" for i in range(1, len(al["records"]) + 1)]
    if ids != want:
        out.append({"id": f"{ch}:*", "check": "id-coverage",
                    "detail": f"{len(ids)} records"})
        return ok and False
    for r in al["records"]:
        if not r["telang"] and not r.get("notes", "").strip():
            out.append({"id": r["id"], "check": "quarantine-note",
                        "detail": "no telang spans and no notes"})
            ok = False
        for s in r["telang"]:
            hay = paras.get(s["para"])
            if hay is None:
                out.append({"id": r["id"], "check": "unknown-para",
                            "detail": s["para"]})
                ok = False
                continue
            for q in s["anchors"]:
                if q not in hay:
                    out.append({"id": r["id"], "check": "anchor",
                                "detail": q[:60]})
                    ok = False
    # Coverage: every non-footnote para used or dropped.
    kept = [x for x in tri["telang_paras"] if not x["footnote?"]]
    used = {s2["para"] for r in al["records"] for s2 in r["telang"]}
    dropped = {d2["para"] for d2 in al.get("dropped", [])}
    for i in range(len(kept)):
        tno = f"T{i:02d}"
        if tno not in used and tno not in dropped:
            out.append({"id": f"{ch}:*", "check": "para-coverage",
                        "detail": f"{tno} unaccounted"})
            ok = False
    return ok


def check_sanskrit(ch, out):
    """Contiguous ids, vulgate counts, IAST present."""
    _, expected, _ = CHAPTERS[ch]
    doc = json.loads((WORK / f"ch{ch:02d}.json").read_text(encoding="utf-8"))
    verses = doc["verses"]
    ok = True
    if len(verses) != expected:
        out.append({"id": f"{ch}:*", "check": "verse-count",
                    "detail": f"{len(verses)} != {expected}"})
        return False
    for i, v in enumerate(verses, start=1):
        if v["id"] != f"{ch}:{i}":
            out.append({"id": v.get("id", "?"), "check": "verse-id",
                        "detail": f"want {ch}:{i}"})
            ok = False
        if not v.get("devanagari", "").strip():
            out.append({"id": v["id"], "check": "blank-devanagari", "detail": ""})
            ok = False
        if not v.get("iast", "").strip():
            out.append({"id": v["id"], "check": "blank-iast", "detail": ""})
            ok = False
    return ok


def check_feelings(out):
    path = DATA / "feelings.json"
    if not path.exists():
        out.append({"id": "*:*", "check": "feelings-missing", "detail": ""})
        return False
    feelings = json.loads(path.read_text(encoding="utf-8"))["feelings"]
    ids = set()
    for ch in sorted(CHAPTERS):
        doc = json.loads((WORK / f"draft_ch{ch:02d}.json").read_text(encoding="utf-8"))
        ids.update(r["id"] for r in doc["records"])
    ok = True
    for f in feelings:
        bad = [vid for vid in f["verses"] if vid not in ids]
        if bad:
            out.append({"id": "*:*", "check": "feelings-ref",
                        "detail": f"{f['name']}: {bad}"})
            ok = False
    return ok


def check_bundle(out):
    """Exported bundle matches work files (ignoring export datestamp)."""
    with tempfile.TemporaryDirectory() as tmp:
        tmppath = Path(tmp) / "bundle.json"
        proc = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "content" / "gita.py"),
             "export_app", "--out", str(tmppath)],
            capture_output=True, text=True, cwd=ROOT / "tools" / "content",
        )
        if proc.returncode != 0:
            out.append({"id": "*:*", "check": "bundle-export",
                        "detail": proc.stdout.strip()[-200:]})
            return False
        live = json.loads((ROOT / "app" / "src" / "main"
                           / "assets" / "gita-bundle.json").read_text(encoding="utf-8"))
        fresh = json.loads(tmppath.read_text(encoding="utf-8"))
        ok = True
        for key in ("chapters", "verses", "feelings"):
            if live[key] != fresh[key]:
                out.append({"id": "*:*", "check": f"bundle-{key}",
                            "detail": "committed bundle differs from work files"})
                ok = False
                break
        total = len(fresh["verses"])
        if total != EXPECTED_TOTAL:
            out.append({"id": "*:*", "check": "bundle-total",
                        "detail": f"{total} != {EXPECTED_TOTAL}"})
            ok = False
        return ok


def cmd_verify(args) -> int:
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    glossary = load_glossary()
    report = {"chapters": {}, "global": {}, "failures": []}
    failures = 0
    for ch in chapters:
        out: list = []
        _, expected, _ = CHAPTERS[ch]
        approved, ok1 = check_draft_records(ch, glossary, out)
        ok2 = check_align_records(ch, out)
        ok3 = check_sanskrit(ch, out)
        ch_ok = ok1 and ok2 and ok3
        report["chapters"][str(ch)] = {
            "verses": expected, "approved": approved,
            "draft": ok1, "align": ok2, "sanskrit": ok3,
        }
        failures += len(out)
        report["failures"].extend(out)
        print(f"verify: ch{ch:02d} {approved}/{expected} "
              f"{'OK' if ch_ok else f'{len(out)} FAILURES'}")
    if args.chapter is None:
        gout: list = []
        g1 = check_feelings(gout)
        g2 = check_bundle(gout)
        report["global"] = {"feelings": g1, "bundle": g2}
        failures += len(gout)
        report["failures"].extend(gout)
        print(f"verify: global feelings={'OK' if g1 else 'FAIL'} "
              f"bundle={'OK' if g2 else 'FAIL'}")
    report["failures_total"] = failures
    (WORK / "verify_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"verify: {failures} total failures "
          f"-> data/content/work/verify_report.json")
    return 1 if failures else 0
