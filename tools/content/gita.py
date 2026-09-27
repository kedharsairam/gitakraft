"""GitaKraft content pipeline CLI (stdlib only — no third-party deps).

Stages:
  fetch        pinned Wikisource chapter wikitext -> data/content/raw/ (+ manifest)
  normalize    raw -> verse records (id, speaker, devanagari) -> data/content/work/
  transliterate work records gain IAST (round-trip verified)
  validate     counts, NFC, continuity, IAST round-trip — CI-blocked
  export       work records -> SQLite asset (skeleton: schema + verses table)

Every stage reads/writes JSON records; every stage is diffable in git.
"""

import argparse
import hashlib
import json
import re
import sqlite3
import sys
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

from chapters import CHAPTERS, EXPECTED_TOTAL, WIKISOURCE_API
from iast import transliterate, detransliterate

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "data" / "content" / "raw"
WORK = ROOT / "data" / "content" / "work"

DEV_DIGITS = str.maketrans("०१२३४५६७८९", "0123456789")

# Verse marker: ॥1-12॥ (Devanagari or Latin digits, chapter-verse).
MARKER_RE = re.compile(r"॥\s*([०१२३४५६७८९0-9]+)\s*[-–]\s*([०१२३४५६७८९0-9]+)\s*॥")
SPEAKER_RE = re.compile(r"^\s*([^\s।॥]+(?:\s+[^\s।॥]+){0,2})\s+उवाच\s*$")


def _api(params: dict) -> dict:
    import time
    params = dict(params, format="json")
    url = WIKISOURCE_API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "GitaKraft-content/0.1 (local research import)"})
    last = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001 — 429s from polite sources; back off
            last = e
            time.sleep(5 * (attempt + 1))
    raise last  # type: ignore[misc]


def cmd_fetch(args) -> int:
    RAW.mkdir(parents=True, exist_ok=True)
    manifest = {}
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    import time
    for i, ch in enumerate(chapters):
        if i:
            time.sleep(3)  # polite throttle against the gratis API
        subpage, _, _ = CHAPTERS[ch]
        title = f"भगवद्गीता/{subpage}"
        data = _api({"action": "parse", "page": title, "prop": "wikitext"})
        try:
            wt = data["parse"]["wikitext"]["*"]
        except KeyError:
            print(f"fetch: chapter {ch} ({title}) not found in API response")
            return 1
        blob = json.dumps({"title": title, "wikitext": wt}, ensure_ascii=False)
        digest = hashlib.sha256(blob.encode("utf-8")).hexdigest()
        (RAW / f"ch{ch:02d}.json").write_text(blob, encoding="utf-8")
        manifest[f"ch{ch:02d}"] = {"title": title, "sha256": digest, "chars": len(wt)}
        print(f"fetch: ch{ch:02d} {len(wt)} chars sha256={digest[:12]}")
    (RAW / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0


def _clean_line(line: str) -> str:
    # Wiki markup is presentation, not scripture: bold/italic quotes,
    # line-break tags, and non-breaking spaces are stripped before parsing.
    line = line.replace("'''", "").replace("''", "")
    line = re.sub(r"<br\s*/?>", " ", line)
    line = line.replace("&nbsp;", " ")
    return line.strip()


def _extract_poem(wikitext: str) -> str:
    # Verses live in one <poem> block PER VERSE (roughly) — concatenate
    # all of them in order. Commentary sections carry verse-number-only
    # markers (॥ N ॥) and must never enter the verse stream.
    return "\n".join(re.findall(r"<poem>(.*?)</poem>", wikitext, re.DOTALL))


def parse_verses(poem: str, chapter: int) -> list[dict]:
    """Split a <poem> block into verse records.

    Verse boundaries are ॥ch-verse॥ markers. Speaker lines (X उवाच)
    attach to the following verse. Preamble (invocations before the
    first marker) is returned separately.
    """
    records: list[dict] = []
    preamble: list[str] = []
    buf: list[str] = []
    speaker: str | None = None
    started = False
    verse_no = 0

    def flush(marker_ch: int, marker_vs: int) -> None:
        nonlocal verse_no
        verse_no += 1
        text = "\n".join(buf).strip()
        records.append({
            "id": f"{chapter}:{verse_no}",
            "marker": {"chapter": marker_ch, "verse": marker_vs},
            "speaker": speaker,
            "devanagari": unicodedata.normalize("NFC", text),
        })

    for raw_line in poem.splitlines():
        line = _clean_line(raw_line)
        if not line:
            continue
        m = MARKER_RE.search(line)
        sp = SPEAKER_RE.match(line)
        if m:
            mch = int(m.group(1).translate(DEV_DIGITS))
            mvs = int(m.group(2).translate(DEV_DIGITS))
            # Text before the marker on the same line belongs to the verse.
            head = MARKER_RE.sub("", line).strip()
            if head:
                buf.append(head)
            if not started:
                started = True
                if preamble and not buf:
                    pass
            flush(mch, mvs)
            buf = []
            speaker = None
        elif sp and not started:
            # Speaker attribution before/at the start (e.g. धृतराष्ट्र उवाच).
            speaker = sp.group(1).strip()
        elif sp and started and not buf:
            speaker = sp.group(1).strip()
        else:
            if not started and speaker is None:
                preamble.append(line)
            else:
                # A speaker line opens verse territory: following lines
                # belong to the verse even before its marker arrives.
                started = True
                buf.append(line)
    return records


def cmd_normalize(args) -> int:
    WORK.mkdir(parents=True, exist_ok=True)
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    for ch in chapters:
        raw_path = RAW / f"ch{ch:02d}.json"
        if not raw_path.exists():
            print(f"normalize: missing raw ch{ch:02d} (run fetch first)")
            return 1
        wt = json.loads(raw_path.read_text(encoding="utf-8"))["wikitext"]
        records = parse_verses(_extract_poem(wt), ch)
        # Renumber sequentially (markers can repeat in commentaries-free poem).
        for i, r in enumerate(records, start=1):
            r["id"] = f"{ch}:{i}"
        out = {"chapter": ch, "verses": records}
        (WORK / f"ch{ch:02d}.json").write_text(
            json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"normalize: ch{ch:02d} {len(records)} verses")
    return 0


def cmd_transliterate(args) -> int:
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    for ch in chapters:
        path = WORK / f"ch{ch:02d}.json"
        if not path.exists():
            print(f"transliterate: missing work ch{ch:02d} (run normalize first)")
            return 1
        doc = json.loads(path.read_text(encoding="utf-8"))
        bad = 0
        for v in doc["verses"]:
            v["iast"] = transliterate(v["devanagari"])
            if detransliterate(v["iast"]) != v["devanagari"]:
                bad += 1
                print(f"transliterate: ROUND-TRIP FAIL {v['id']}")
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"transliterate: ch{ch:02d} ok, round-trip failures: {bad}")
        if bad:
            return 1
    return 0


def cmd_validate(args) -> int:
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    failures: list[str] = []
    total = 0
    for ch in chapters:
        path = WORK / f"ch{ch:02d}.json"
        if not path.exists():
            failures.append(f"ch{ch:02d}: missing work file")
            continue
        doc = json.loads(path.read_text(encoding="utf-8"))
        verses = doc["verses"]
        total += len(verses)
        _, expected, _ = CHAPTERS[ch]
        if len(verses) != expected:
            failures.append(f"ch{ch:02d}: got {len(verses)} verses, expected {expected}")
        ids = [v["id"] for v in verses]
        want = [f"{ch}:{i}" for i in range(1, len(verses) + 1)]
        if ids != want:
            failures.append(f"ch{ch:02d}: id sequence broken")
        for v in verses:
            if not v.get("devanagari", "").strip():
                failures.append(f"{v['id']}: empty text")
            if unicodedata.normalize("NFC", v["devanagari"]) != v["devanagari"]:
                failures.append(f"{v['id']}: not NFC-normalized")
            if "iast" in v and detransliterate(v["iast"]) != v["devanagari"]:
                failures.append(f"{v['id']}: IAST round-trip broken")
    if not args.chapter and total != EXPECTED_TOTAL:
        failures.append(f"total: got {total} verses, expected {EXPECTED_TOTAL}")
    if failures:
        print("validate: FAILURES")
        for f in failures:
            print(" -", f)
        return 1
    print(f"validate: OK ({total} verses)")
    return 0


SCHEMA = """
CREATE TABLE IF NOT EXISTS verses(
  id TEXT PRIMARY KEY, chapter INTEGER, verse INTEGER,
  speaker TEXT, devanagari TEXT NOT NULL, iast TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS chapters(
  number INTEGER PRIMARY KEY, title_sa TEXT, title_en TEXT, verse_count INTEGER
);
"""


def cmd_export(args) -> int:
    out = Path(args.out or (ROOT / "app" / "src" / "main" / "assets" / "gita.db"))
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    for ch in chapters:
        path = WORK / f"ch{ch:02d}.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        subpage, _, title_en = CHAPTERS[ch]
        db.execute("INSERT INTO chapters VALUES(?,?,?,?)",
                   (ch, subpage, title_en, len(doc["verses"])))
        for i, v in enumerate(doc["verses"], start=1):
            db.execute("INSERT INTO verses VALUES(?,?,?,?,?,?)",
                       (v["id"], ch, i, v.get("speaker"), v["devanagari"], v.get("iast", "")))
    db.commit()
    n = db.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
    db.close()
    print(f"export: {n} verses -> {out}")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="gita", description="GitaKraft content pipeline")
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("fetch", "normalize", "transliterate", "validate", "export"):
        p = sub.add_parser(name)
        p.add_argument("--chapter", type=int, default=None)
        if name == "export":
            p.add_argument("--out", default=None)
    args = ap.parse_args(argv)
    return {"fetch": cmd_fetch, "normalize": cmd_normalize,
            "transliterate": cmd_transliterate, "validate": cmd_validate,
            "export": cmd_export}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
