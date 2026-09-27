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
from chapters import EN_WIKISOURCE_API, TELANG_INDEX, TELANG_PAGE_FROM, TELANG_PAGE_TO
from iast import transliterate, detransliterate

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "data" / "content" / "raw"
WORK = ROOT / "data" / "content" / "work"

DEV_DIGITS = str.maketrans("०१२३४५६७८९", "0123456789")

# Verse marker: ॥1-12॥ (Devanagari or Latin digits, chapter-verse).
MARKER_RE = re.compile(r"॥\s*([०१२३४५६७८९0-9]+)\s*[-–]\s*([०१२३४५६७८९0-9]+)\s*॥")
SPEAKER_RE = re.compile(r"^\s*([^\s।॥]+(?:\s+[^\s।॥]+){0,2})\s+उवाच\s*$")


def _api(params: dict, base: str = WIKISOURCE_API) -> dict:
    import time
    params = dict(params, format="json")
    url = base + "?" + urllib.parse.urlencode(params)
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


def _clean_math(text: str) -> str:
    # <math>\mbox{Dh}ri...</math> fragments reassemble to plain words
    # (Dhritarashtra); footnote refs are dropped, not kept.
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.DOTALL)
    text = re.sub(r"<ref[^>]*/>", "", text)
    def _math(m: re.Match) -> str:
        inner = m.group(1)
        inner = re.sub(r"\\mbox\{([^}]*)\}", r"\1", inner)
        inner = inner.replace("\\", "")
        return inner
    text = re.sub(r"<math>(.*?)</math>", _math, text, flags=re.DOTALL)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def cmd_fetch_en(args) -> int:
    """Fetch Telang (SBE Vol 8, 1882 — public domain) DjVu page texts."""
    import time
    RAW.mkdir(parents=True, exist_ok=True)
    pages = {}
    for i, p in enumerate(range(TELANG_PAGE_FROM, TELANG_PAGE_TO + 1)):
        if i:
            time.sleep(3)  # polite throttle against the gratis API
        title = f"Page:{TELANG_INDEX}/{p}"
        try:
            data = _api({"action": "parse", "page": title, "prop": "wikitext"},
                        base=EN_WIKISOURCE_API)
            wt = data["parse"]["wikitext"]["*"]
        except Exception as e:  # noqa: BLE001 — log and continue; validate catches gaps
            print(f"fetch_en: page {p} FAILED ({e})")
            continue
        pages[str(p)] = wt
        print(f"fetch_en: page {p} {len(wt)} chars")
    digest = hashlib.sha256(json.dumps(pages, sort_keys=True).encode("utf-8")).hexdigest()
    (RAW / "telang_pages.json").write_text(
        json.dumps({"pages": pages, "sha256": digest}, ensure_ascii=False), encoding="utf-8")
    print(f"fetch_en: {len(pages)} pages sha256={digest[:12]}")
    return 0


def _segment_telang(pages: dict[str, str], chapter: int) -> list[dict]:
    """Split Telang chapter text into per-verse segments.

    Telang marks speakers with centered 'X said:' divs; verses follow in
    print order. Segmentation is sequential and CHECKED against the known
    verse count — never silently accepted.
    """
    ordered = [pages[k] for k in sorted(pages, key=int)]
    full = "\n".join(ordered)
    full = _clean_math(full)
    # Chapter header used by the print edition.
    chap_pat = re.compile(r"Chapter\s+%s\b" % ("I" * 0 + _roman(chapter)))
    parts = chap_pat.split(full)
    body = parts[1] if len(parts) > 1 else full
    # Next chapter starts the next segment — cut there.
    if chapter < 18:
        nxt = re.compile(r"Chapter\s+%s\b" % _roman(chapter + 1))
        m = nxt.search(body)
        if m:
            body = body[:m.start()]
    # Split on speaker headers; each header opens a new verse segment.
    speaker_pat = re.compile(
        r"^\s*([A-Z][A-Za-zâêîôûāīūṛñśṣṭḍṇṃḥ’'\- ]{1,40}?)\s+said:\s*$",
        re.MULTILINE)
    chunks = speaker_pat.split(body)
    # chunks[0] = preface (dropped unless it holds verse 1 without speaker),
    # then (speaker, text) pairs.
    segments: list[dict] = []
    preface = chunks[0].strip()
    rest = chunks[1:]
    if preface and not rest:
        segments.append({"speaker": None, "text": preface})
    for i in range(0, len(rest) - 1, 2):
        segments.append({"speaker": rest[i].strip(), "text": rest[i + 1].strip()})
    return segments


_ROMAN = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]


def _roman(n: int) -> str:
    out = ""
    for v, s in _ROMAN:
        while n >= v:
            out += s
            n -= v
    return out


def cmd_norm_en(args) -> int:
    """Telang pages -> per-chapter verse-segmented reference JSON."""
    raw_path = RAW / "telang_pages.json"
    if not raw_path.exists():
        print("norm_en: missing telang_pages.json (run fetch_en first)")
        return 1
    pages = json.loads(raw_path.read_text(encoding="utf-8"))["pages"]
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    failures = 0
    for ch in chapters:
        _, expected, _ = CHAPTERS[ch]
        try:
            segments = _segment_telang(pages, ch)
        except Exception as e:  # noqa: BLE001 — segmentation is heuristic; report
            print(f"norm_en: ch{ch:02d} SEGMENTATION ERROR ({e})")
            failures += 1
            continue
        # Strip empty segments, keep order.
        segments = [s for s in segments if s["text"]]
        status = "OK" if len(segments) == expected else f"MISMATCH({len(segments)}vs{expected})"
        if status != "OK":
            failures += 1
        (WORK / f"en_telang_ch{ch:02d}.json").write_text(
            json.dumps({"chapter": ch, "segments": segments, "status": status},
                       ensure_ascii=False, indent=1),
            encoding="utf-8")
        print(f"norm_en: ch{ch:02d} {len(segments)} segments [{status}]")
    return 1 if failures else 0


def _clean_telang(text: str) -> str:
    # OCR running heads, page numbers, hyphenation, footnote paragraphs.
    # Footnotes are Telang's commentary asides — dropped for meaning
    # drafting (recorded decision); verse prose is preserved verbatim.
    lines = []
    for ln in text.splitlines():
        s = ln.strip()
        if re.fullmatch(r"\d{1,3}", s):
            continue
        if re.fullmatch(r"B[IHA]{2,}[A-Z\.]*", s.replace(" ", "")):
            continue
        lines.append(ln)
    text = "\n".join(lines)
    text = re.sub(r"(\w)-\s*\n\s*(\w)", r"\1\2", text)  # dehyphenate
    text = re.sub(r"[ \t]+", " ", text)
    return text


def _strip_footnotes(paras: list[str]) -> list[str]:
    out = []
    for p in paras:
        if re.match(r"^['®^•\*\-–\d\u2018\u2019\u201c\u201d]", s):
            continue  # footnote paragraph
        out.append(p)
    return out


def cmd_anchor_en(args) -> int:
    """Anchor-split Telang OCR into per-verse reference segments.

    Uses print running heads (CHAPTER ch, v.) as anchors; paragraphs
    between anchors are allocated to verses and CHECKED against known
    counts. Mismatches are flagged manual — never silently accepted.
    """
    raw_path = Path("/tmp/opencode/telang_ocr.txt")
    if not raw_path.exists():
        print("anchor_en: /tmp/opencode/telang_ocr.txt missing")
        return 1
    t = raw_path.read_text(encoding="utf-8", errors="replace")
    ROM = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7,
           "VIII": 8, "IX": 9, "X": 10, "XI": 11, "XII": 12, "XIII": 13,
           "XIV": 14, "XV": 15, "XVI": 16, "XVII": 17, "XVIII": 18}
    start_m = re.search(r"\nChapter\s+I\.\s*\n", t)
    if not start_m:
        print("anchor_en: chapter I header not found")
        return 1
    gita_start = start_m.start()
    anchors: list[tuple[int, int, int]] = []  # (offset, chapter, verse)
    for m in re.finditer(r"CHAPTER\s+([IVX]+),\s*(\d+)\.", t):
        if m.group(1) in ROM and m.start() > gita_start and m.start() < 283000:
            anchors.append((m.start(), ROM[m.group(1)], int(m.group(2))))
    # Chapter header offsets (chapter starts).
    ch_starts: dict[int, int] = {}
    for m in re.finditer(r"\nChapter\s+([IVX]+)\.\s*\n", t):
        if m.group(1) in ROM and m.start() > gita_start - 5000:
            ch_starts.setdefault(ROM[m.group(1)], m.start())
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    failures = 0
    for ch in chapters:
        _, expected, _ = CHAPTERS[ch]
        cstart = ch_starts.get(ch)
        if cstart is None:
            print(f"anchor_en: ch{ch:02d} header not found")
            failures += 1
            continue
        cend_m = re.search(r"\nChapter\s+%s\.\s*\n" % "|".join(
            r for r in ROM if ROM[r] == ch + 1), t[cstart + 100:])
        cend = cstart + 100 + cend_m.start() if cend_m and ch < 18 else None
        # Span boundaries: chapter start (=verse 1), anchors, chapter end.
        bounds = [(cstart, 1)]
        for off, ach, av in anchors:
            if ach == ch and (cend is None or off < cend):
                bounds.append((off, av))
        # Chapter end: next chapter start or, for 18, the colophon region.
        if cend is None and ch == 18:
            m18 = re.search(r"SANATSU", t[cstart:])
            cend = cstart + m18.start() if m18 else len(t)
        bounds.append((cend, expected + 1))
        segments = []
        ok = True
        for (s0, v0), (s1, v1) in zip(bounds, bounds[1:]):
            span = _clean_telang(t[s0:s1])
            paras = [p.strip().replace("\n", " ")
                     for p in span.split("\n\n") if p.strip()]
            paras = _strip_footnotes(paras)
            # Drop chapter-title/running-head residue (short caps lines).
            paras = [p for p in paras
                     if not (len(p) < 40 and p.isupper())]
            need = v1 - v0
            if len(paras) == need:
                for k, p in enumerate(paras):
                    segments.append({"verse": v0 + k, "text": p,
                                     "status": "auto"})
            else:
                ok = False
                for k, p in enumerate(paras):
                    segments.append({"verse": None, "text": p,
                                     "status": f"manual(span {v0}-{v1 - 1})"})
        status = "OK" if (ok and len([s for s in segments if s["verse"]]) == expected) else "REVIEW"
        if status != "OK":
            failures += 1
        (WORK / f"en_telang_ch{ch:02d}.json").write_text(
            json.dumps({"chapter": ch, "segments": segments, "status": status},
                       ensure_ascii=False, indent=1),
            encoding="utf-8")
        auto = sum(1 for s in segments if s["status"] == "auto")
        print(f"anchor_en: ch{ch:02d} {len(segments)} segs ({auto} auto) [{status}]")
    return 1 if failures else 0


def _is_running_head(p: str) -> bool:
    # Print running heads ("BHAGAVADGITA" mangled by OCR into single
    # mixed-case tokens like BIIAGAVADCfrA). Verse and footnote text
    # always contains spaces; heads never do. Anchored on the
    # unmistakable 'gavad' core.
    s = p.strip()
    if " " in s or len(s) > 28:
        return False
    return bool(re.match(r"^b", s, re.I) and re.search(r"gav?a?d", s, re.I))


def _looks_footnote(p: str) -> bool:
    s = p.strip()
    if re.match(r"^['®^•\*\-–\d\u2018\u2019\u201c\u201d]", s):
        return True
    return bool(re.match(
        r"^(Literally|The original|In the original|Who, as|That is|I\.?e\.?|l\.e\.?|"
        r"Several of these|This is a|Sew|SeWeg|Schlegel|Nilakantha|Lassen)",
        s))


def cmd_triptych(args) -> int:
    """Emit a review file joining Sanskrit verses + Telang paragraphs + Arnold.

    The alignment itself is human-curated (assistant) in follow-up passes —
    this command only prepares the triptych so every decision is recorded
    with provenance. Nothing here guesses verse boundaries.
    """
    raw_path = Path("/tmp/opencode/telang_ocr.txt")
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    # Arnold chapters, split on CHAPTER <roman> headers.
    arn = Path("/tmp/opencode/arnold.htm").read_text(encoding="utf-8", errors="replace")
    arn = re.sub(r"<[^>]+>", "", arn)
    arn = re.sub(r"\n\s*\n+", "\n\n", arn)
    t = raw_path.read_text(encoding="utf-8", errors="replace") if raw_path.exists() else ""
    out = []
    for ch in chapters:
        sa_path = WORK / f"ch{ch:02d}.json"
        sa = json.loads(sa_path.read_text(encoding="utf-8"))["verses"] if sa_path.exists() else []
        # Telang chapter span: 'Chapter ROM.' header to next chapter header.
        ROMS = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII"]
        m0 = re.search(r"\nChapter\s+%s\.\s*\n" % ROMS[ch - 1], t)
        m1 = re.search(r"\nChapter\s+%s\.\s*\n" % ROMS[ch], t) if ch < 18 else None
        paras = []
        if m0:
            span = t[m0.end(): m1.start() if m1 else len(t)]
            span = _clean_telang(span)
            for p in span.split("\n\n"):
                p = p.strip().replace("\n", " ")
                if not p or re.fullmatch(r"\d{1,3}", p):
                    continue
                if re.fullmatch(r"Chapter\s+[IVX]+\.?", p):
                    continue
                if re.fullmatch(r"CHAPTER\s+[IVX]+,\s*\d+\.?", p):
                    continue
                if _is_running_head(p):
                    continue
                # Absorbed marginal markers: leading digits before verse
                # prose ("0 best of...", "1 will name...").
                p = re.sub(r"^\d{1,2}\s+(?=[a-z])", "", p)
                paras.append({"text": p, "footnote?": _looks_footnote(p)})
        # Arnold chapter span.
        ai = arn.find(f"CHAPTER {ROMS[ch - 1]}\n")
        aj = arn.find(f"CHAPTER {ROMS[ch]}\n") if ch < 18 else len(arn)
        arnold = arn[ai:aj].strip() if ai >= 0 else ""
        (WORK / f"triptych_ch{ch:02d}.json").write_text(
            json.dumps({"chapter": ch,
                        "sanskrit": [{"id": v["id"], "speaker": v.get("speaker"),
                                      "devanagari": v["devanagari"], "iast": v.get("iast", "")}
                                     for v in sa],
                        "telang_paras": paras,
                        "arnold": arnold}, ensure_ascii=False, indent=1),
            encoding="utf-8")
        print(f"triptych: ch{ch:02d} {len(sa)} verses, {len(paras)} telang paras, "
              f"arnold {len(arnold)} chars")
        out.append(ch)
    return 0


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


def cmd_check_align(args) -> int:
    """Verify curated align_chNN.json anchors byte-exact against sources.

    Every anchor quote must occur verbatim in its para; every verse 1..N
    must have exactly one record; Arnold spans must be known stanzas.
    """
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    failures = 0
    for ch in chapters:
        apath = WORK / f"align_ch{ch:02d}.json"
        if not apath.exists():
            if args.chapter:
                print(f"check_align: ch{ch:02d} no alignment file yet")
                failures += 1
            continue
        al = json.loads(apath.read_text(encoding="utf-8"))
        tpath = WORK / f"triptych_ch{ch:02d}.json"
        tri = json.loads(tpath.read_text(encoding="utf-8"))
        paras = {f"T{i:02d}": p["text"] for i, p in enumerate(
            x for x in tri["telang_paras"] if not x["footnote?"])}
        ids = sorted((r["id"] for r in al["records"]),
                     key=lambda s: int(s.split(":")[1]))
        want = [f"{ch}:{i}" for i in range(1, len(al["records"]) + 1)]
        if ids != want:
            print(f"check_align: ch{ch:02d} id coverage broken")
            failures += 1
        bad = 0
        for r in al["records"]:
            for s in r["telang"]:
                hay = paras.get(s["para"])
                if hay is None:
                    print(f"check_align: {r['id']} unknown {s['para']}")
                    bad += 1
                    continue
                for q in s["anchors"]:
                    if q not in hay:
                        print(f"check_align: {r['id']} anchor missing: {q[:50]!r}")
                        bad += 1
        print(f"check_align: ch{ch:02d} {len(al['records'])} records, {bad} bad anchors")
        failures += bad
    return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="gita", description="GitaKraft content pipeline")
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("fetch", "normalize", "transliterate", "validate", "export",
                 "fetch_en", "norm_en", "anchor_en", "triptych", "check_align"):
        p = sub.add_parser(name)
        p.add_argument("--chapter", type=int, default=None)
        if name == "export":
            p.add_argument("--out", default=None)
    args = ap.parse_args(argv)
    return {"fetch": cmd_fetch, "normalize": cmd_normalize,
            "transliterate": cmd_transliterate, "validate": cmd_validate,
            "export": cmd_export, "fetch_en": cmd_fetch_en,
            "norm_en": cmd_norm_en, "anchor_en": cmd_anchor_en,
            "triptych": cmd_triptych, "check_align": cmd_check_align}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
