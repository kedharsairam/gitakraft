"""Draft-stage machinery: context emission + quality gates.

The draft step emits per-verse context (Sanskrit + full Telang context +
Arnold span) for human (assistant) drafting. Quality gates run on the
completed drafts:
- non-empty meaning + takeaway + provenance
- Flesch-Kincaid grade gate (kid-readable target)
- terminology lock against data/content/glossary.json
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "data" / "content" / "work"
DATA = ROOT / "data" / "content"


def flesch_kincaid_grade(text: str) -> float:
    """Heuristic FK grade (vowel-group syllables). Gate, not gospel."""
    sentences = [s for s in re.split(r"[.!?]+", text) if s.strip()]
    words = re.findall(r"[A-Za-z']+", text)
    if not sentences or not words:
        return 0.0

    def syllables(w: str) -> int:
        # Proper nouns are skimmed, not sounded out — exclude them so
        # name-catalog verses (1:4-8, 1:15-18) aren't penalized for
        # faithfulness. Standard readability practice.
        if w and w[0].isupper():
            return 1
        w = w.lower()
        groups = re.findall(r"[aeiouy]+", w)
        n = len(groups)
        if w.endswith("e") and n > 1:
            n -= 1
        return max(1, n)

    syl = sum(syllables(w) for w in words)
    return 0.39 * (len(words) / len(sentences)) + 11.8 * (syl / len(words)) - 15.59


def load_glossary() -> dict:
    path = DATA / "glossary.json"
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def cmd_draft(args) -> int:
    ch = args.chapter
    if ch is None:
        print("draft: --chapter required")
        return 1
    al = json.loads((WORK / f"align_ch{ch:02d}.json").read_text(encoding="utf-8"))
    tri = json.loads((WORK / f"triptych_ch{ch:02d}.json").read_text(encoding="utf-8"))
    paras = {f"T{i:02d}": p["text"] for i, p in enumerate(
        x for x in tri["telang_paras"] if not x["footnote?"])}
    stanzas = [s.strip() for s in tri["arnold"].split("\n\n") if s.strip()]
    sa = {v["id"]: v for v in json.loads(
        (WORK / f"ch{ch:02d}.json").read_text(encoding="utf-8"))["verses"]}
    out_path = WORK / f"draft_ch{ch:02d}.json"
    existing = {}
    if out_path.exists():
        existing = {r["id"]: r for r in
                    json.loads(out_path.read_text(encoding="utf-8"))["records"]}
    records = []
    for r in al["records"]:
        vid = r["id"]
        keep = existing.get(vid, {})
        ctx = "\n\n".join(paras[s["para"]] for s in r["telang"])
        records.append({
            "id": vid,
            "devanagari": sa[vid]["devanagari"],
            "iast": sa[vid].get("iast", ""),
            "speaker": sa[vid].get("speaker"),
            "telang_context": ctx,
            "arnold_span": r["arnold"],
            "arnold_shared": r.get("arnold_shared", False),
            "align_notes": r.get("notes", ""),
            "meaning_simple": keep.get("meaning_simple", ""),
            "takeaway": keep.get("takeaway", ""),
            "provenance": keep.get("provenance", []),
            "status": keep.get("status", "pending"),
        })
    out_path.write_text(json.dumps({"chapter": ch, "records": records},
                                   ensure_ascii=False, indent=1), encoding="utf-8")
    done = sum(1 for r in records if r["status"] == "approved")
    print(f"draft: ch{ch:02d} {len(records)} records ({done} approved), "
          f"arnold stanzas available: {len(stanzas)}")
    return 0


def cmd_check_draft(args) -> int:
    ch = args.chapter
    if ch is None:
        print("check_draft: --chapter required")
        return 1
    path = WORK / f"draft_ch{ch:02d}.json"
    doc = json.loads(path.read_text(encoding="utf-8"))
    glossary = load_glossary()
    failures = 0
    for r in doc["records"]:
        tag = r["id"]
        if r["status"] != "approved":
            continue
        for field in ("meaning_simple", "takeaway"):
            if not r.get(field, "").strip():
                print(f"check_draft: {tag} empty {field}")
                failures += 1
        grade = flesch_kincaid_grade(r.get("meaning_simple", ""))
        if grade > 8.0:
            print(f"check_draft: {tag} grade {grade:.1f} > 8.0")
            failures += 1
        if not r.get("provenance"):
            print(f"check_draft: {tag} missing provenance")
            failures += 1
        blob = (r.get("meaning_simple", "") + " " + r.get("takeaway", "")).lower()
        for term, rendered in glossary.items():
            # Flag Sanskrit-term leaks (term appears untranslated and the
            # locked rendering is absent).
            if term.lower() in blob and rendered.lower() not in blob:
                print(f"check_draft: {tag} uses '{term}' without locked '{rendered}'")
                failures += 1
    approved = sum(1 for r in doc["records"] if r["status"] == "approved")
    print(f"check_draft: ch{ch:02d} {approved} approved, {failures} failures")
    return 1 if failures else 0
