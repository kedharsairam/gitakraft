"""Stage 4 verification: side-by-side review packs for human QC.

Generates ~10-verse markdown packs in chapter order under
data/content/review/packs/, each verse carrying Sanskrit + IAST +
meaning + takeaway + provenance + align notes + Telang anchors.
Sign-off is tracked in data/content/review/status.json (never inside
the generated files, so regeneration preserves it).

Commands (via gita.py review):
  review [--chapter N]   generate/refresh packs (default: all)
  review --status        print sign-off table
  review --sign-off ID   mark pack signed off (e.g. ch01-1)
  review --reopen ID     return pack to pending
"""

import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "data" / "content" / "work"
REVIEW = ROOT / "data" / "content" / "review"
PACKS = REVIEW / "packs"
STATUS_PATH = REVIEW / "status.json"

sys.path.insert(0, str(ROOT / "tools" / "content"))
from chapters import CHAPTERS, DISPLAY_TITLES  # noqa: E402

PACK_SIZE = 10


def load_status() -> dict:
    if STATUS_PATH.exists():
        return json.loads(STATUS_PATH.read_text(encoding="utf-8"))
    return {}


def save_status(status: dict) -> None:
    REVIEW.mkdir(parents=True, exist_ok=True)
    STATUS_PATH.write_text(json.dumps(status, ensure_ascii=False, indent=1),
                           encoding="utf-8")


def pack_id(ch: int, k: int) -> str:
    return f"ch{ch:02d}-{k}"


def render_pack(ch: int, k: int, verses: list, status: str) -> str:
    _, _, traditional = CHAPTERS[ch]
    display = DISPLAY_TITLES[ch]
    first, last = verses[0]["id"], verses[-1]["id"]
    lines = [
        f"# Pack {pack_id(ch, k)} — Chapter {ch}: {display}",
        "",
        f"Traditional: {traditional} · Verses {first}–{last} · " +
        f"Status: **{status}**",
        "",
    ]
    for r in verses:
        spk = f" ({r['speaker']})" if r.get("speaker") else ""
        lines.append(f"## {r['id']}{spk}")
        lines.append("")
        lines.append(f"**Sanskrit:** {r['devanagari']}")
        lines.append("")
        if r.get("iast", "").strip():
            lines.append(f"**IAST:** {r['iast']}")
            lines.append("")
        lines.append(f"**Meaning:** {r['meaning_simple']}")
        lines.append("")
        lines.append(f"**Takeaway:** {r['takeaway']}")
        lines.append("")
        prov = ", ".join(r.get("provenance", []))
        lines.append(f"**Provenance:** {prov}")
        if r.get("align_notes", "").strip():
            lines.append("")
            lines.append(f"**Notes:** {r['align_notes']}")
        anchors = r.get("_anchors", [])
        if anchors:
            lines.append("")
            for para, quotes in anchors:
                quoted = " ".join(f'"{q}"' for q in quotes)
                lines.append(f"**Telang [{para}]:** {quoted}")
        lines.append("")
    return "\n".join(lines)


def cmd_review(args) -> int:
    if args.sign_off:
        pid = args.sign_off
        status = load_status()
        if pid not in status:
            print(f"review: unknown pack {pid}")
            return 1
        status[pid] = {"status": "signed-off",
                       "signed_off_at": date.today().isoformat()}
        save_status(status)
        print(f"review: {pid} signed off")
        return 0
    if args.reopen:
        pid = args.reopen
        status = load_status()
        if pid not in status:
            print(f"review: unknown pack {pid}")
            return 1
        status[pid] = {"status": "pending"}
        save_status(status)
        print(f"review: {pid} reopened")
        return 0
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    PACKS.mkdir(parents=True, exist_ok=True)
    status = load_status()
    total_packs = 0
    for ch in chapters:
        doc = json.loads((WORK / f"draft_ch{ch:02d}.json").read_text(encoding="utf-8"))
        al = json.loads((WORK / f"align_ch{ch:02d}.json").read_text(encoding="utf-8"))
        anchors = {r["id"]: [(s["para"], s["anchors"]) for s in r["telang"]]
                   for r in al["records"]}
        records = doc["records"]
        for k in range((len(records) + PACK_SIZE - 1) // PACK_SIZE):
            chunk = records[k * PACK_SIZE:(k + 1) * PACK_SIZE]
            for r in chunk:
                r["_anchors"] = anchors.get(r["id"], [])
            pid = pack_id(ch, k + 1)
            st = status.get(pid, {"status": "pending"})
            status.setdefault(pid, {"status": "pending"})
            (PACKS / f"{pid}.md").write_text(
                render_pack(ch, k + 1, chunk, st["status"]), encoding="utf-8")
            total_packs += 1
    save_status(status)
    if args.status:
        signed = sum(1 for v in status.values() if v["status"] == "signed-off")
        print(f"review: {signed}/{len(status)} packs signed off")
        for pid in sorted(status):
            print(f"  {pid}: {status[pid]['status']}")
    else:
        print(f"review: {total_packs} packs written "
              f"-> data/content/review/packs/ ({len(status)} tracked)")
    return 0
