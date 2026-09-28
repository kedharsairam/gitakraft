"""Stage 2 verification: independent Sanskrit witnesses, character-level.

Fetches third-party Sanskrit e-texts (GRETIL first; structured for N),
parses them to per-verse readings, and diffs against our Devanagari
character-by-character. Divergences are FLAGGED with both readings —
never auto-fixed, never averaged. Human adjudicates.

The systematic trap: witnesses print external sandhi SPLIT
("ca eva") where our text is UNSPLIT ("caiva") and vice versa.
A small external-sandhi JOINER rewrites both sides at word joints
before comparison. Joints it cannot explain become DIVERGENCE
(high priority); explained ones become SANDHI-JOINT (low priority).

Statuses: MATCH, SANDHI-JOINT, DIVERGENCE, MISSING, SPEAKER-NOTE.
Exit code counts DIVERGENCE + MISSING only.

Report: data/content/work/witness_report.json
Raw witness: data/content/raw/gretil_bhgce.htm (pinned by sha256)
"""

import hashlib
import json
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "data" / "content" / "work"
RAW = ROOT / "data" / "content" / "raw"
DATA = ROOT / "data" / "content"

sys.path.insert(0, str(ROOT / "tools" / "content"))
from chapters import CHAPTERS  # noqa: E402
from iast import detransliterate, transliterate  # noqa: E402

WITNESSES = {
    "gretil": {
        "url": "http://gretil.sub.uni-goettingen.de/gretil/1_sanskr/2_epic/mbh/ext/bhgce__u.htm",  # noqa: E501
        "file": "gretil_bhgce.htm",
        "note": ("BORI-based Mahabharata text, input Tokunaga et al., "
                 "revised Smith; via GRETIL. Reference only."),
    },
}

SHORT_A = {"a", "ā"}
VOWELS_ALL = {"a", "ā", "i", "ī", "u", "ū", "ṛ", "ṝ", "e", "o", "ai", "au"}
VOICED_1 = ({"a", "ā", "i", "ī", "u", "ū", "ṛ", "ṝ", "e", "o"} |
            {"g", "ṅ", "j", "ñ", "ḍ", "ṇ", "d", "n", "b", "m",
             "y", "r", "l", "v", "h", "ṃ"})
UNVOICED_1 = {"k", "c", "ṭ", "t", "p", "ś", "ṣ", "s"}


def fetch_witness(name: str) -> dict:
    """Download + sha256 pin. Returns provenance record."""
    spec = WITNESSES[name]
    out = RAW / spec["file"]
    req = urllib.request.Request(
        spec["url"], headers={"User-Agent": "GitaKraft-content/0.1 (research import)"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        blob = resp.read()
    out.write_bytes(blob)
    sha = hashlib.sha256(blob).hexdigest()
    print(f"witness: {name} {len(blob)} bytes sha256={sha[:12]} -> {out.name}")
    return {"name": name, "url": spec["url"], "sha256": sha, "note": spec["note"]}


def parse_gretil(path: Path) -> dict:
    """HTML -> {vid: {'a': iast, 'c': iast, 'speaker': iast|None}}."""
    html = path.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"<[^>]+>", "", html)
    text = unicodedata.normalize("NFC", text)
    verses: dict = {}
    # Speaker lines carry a bare verse ref: "<name> uvāca  Bhg_CC.VVV"
    for m in re.finditer(r"([^\n]+?)\s+uvāca\s+Bhg_(\d+)\.(\d+)(?![a-d])", text):
        name, ch, v = m.group(1).strip(), int(m.group(2)), int(m.group(3))
        vid = f"{ch}:{v}"
        verses.setdefault(vid, {"a": "", "c": "", "speaker": None})
        verses[vid]["speaker"] = name
    # Pāda lines: "<text>  Bhg_CC.VVV{a,c}"
    for m in re.finditer(r"([^\n]+?)\s+Bhg_(\d+)\.(\d+)([ac])\b", text):
        line, ch, v, pada = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
        vid = f"{ch}:{v}"
        verses.setdefault(vid, {"a": "", "c": "", "speaker": None})
        verses[vid][pada] = line.strip()
    return verses


def strip_iast_noise(s: str) -> str:
    s = re.sub(r"\s*\[=MBh_[^\]]*\]", "", s)
    return s.strip()


IAST_WORD = r"A-Za-zāīūṛṝēōṅñṭḍṇśṣḥṃ"


def clean_iast(s: str) -> str:
    """Keep only IAST letters, spaces, apostrophes.

    GRETIL lines carry editorial punctuation (notably ';' as an
    intra-half pāda separator). Left in place, it blocks both joining
    ("itāram; aṇor" never joins) and folding (C+halant+V needs
    adjacency), manufacturing false divergences.
    """
    s = re.sub(r"[^" + IAST_WORD + r" '\-]", " ", s)
    s = s.replace("-", "")
    return re.sub(r"\s+", " ", s).strip()


def join_external(words: list) -> str:
    """Join word list applying external sandhi at each joint, left to right.

    Only high-confidence classical rules. Anything unmatched keeps its
    space (survives to flagging, never forced). Non-word tokens
    (dandas, pipes, refs) are dropped first: otherwise a rule like
    āḥ+voiced would misfire on "jīvitāḥ |" and eat a real visarga.
    """
    words = [w for w in words if re.search(r"[a-zA-Zāīūṛṝēōṅñṭḍṇśṣḥṃ]", w)]
    out = [words[0]] if words else []
    for w in words[1:]:
        prev = out[-1]
        joined = try_join(prev, w)
        if joined is not None:
            out[-1] = joined
        else:
            out.append(w)
    return " ".join(out)


def try_join(left: str, right: str) -> str | None:
    """Join one word joint, or None if no rule fires.

    Convention: stem_r (right minus first char) is used ONLY when the
    rule consumes right[0] (vowel mergers, gemination). Rules that
    preserve right[0] (visarga/anusvāra reflexes) use full `right`.
    """
    if not left or not right:
        return None
    a, b = left[-1], right[0]
    stem_l, stem_r = left[:-1], right[1:]
    # Vowel hiatus after a/ā (both sides consumed).
    if a in SHORT_A:
        if b in ("a", "ā"):
            return stem_l + "ā" + stem_r
        if b in ("i", "ī"):
            return stem_l + "e" + stem_r
        if b in ("u", "ū"):
            return stem_l + "o" + stem_r
        if b in ("ṛ", "ṝ"):
            return stem_l + "ar" + stem_r
        if b == "e":
            return stem_l + "ai" + stem_r
        if b == "o":
            return stem_l + "au" + stem_r
        if b == "ai":
            return stem_l + "ai" + stem_r
        if b == "au":
            return stem_l + "au" + stem_r
        return None
    # i/ī/u/ū/ṛ + DISSIMILAR VOWEL -> glide (right preserved).
    # The vowel condition is load-bearing: without it, "tu pāṇḍava"
    # would misjoin to "tvpāṇḍava".
    if a in ("i", "ī") and b in VOWELS_ALL and b not in ("i", "ī"):
        return stem_l + "y" + right
    if a in ("u", "ū") and b in VOWELS_ALL and b not in ("u", "ū"):
        return stem_l + "v" + right
    if a in ("ṛ", "ṝ") and b in VOWELS_ALL and b not in ("ṛ", "ṝ"):
        return stem_l + "r" + right
    # Diphthong + a -> diphthong (a consumed).
    if a in ("e", "o", "ai", "au") and b == "a":
        return left + stem_r
    # Visarga (right preserved).
    if left.endswith("aḥ") and b in VOICED_1:
        return left[:-2] + "o" + right
    if left.endswith("āḥ") and b not in UNVOICED_1:
        return left[:-2] + "ā" + right
    if re.search(r"[iīuū]ḥ$", left) and b in VOWELS_ALL:
        return left[:-1] + "r" + right
    if re.search(r"[aāiīuū]ḥ$", left) and b in ("c", "ch", "ṭ", "ṭh",
                                                "t", "th", "ś", "ṣ", "s"):
        return left[:-1] + _sib(b) + right
    # Anusvāra: -m + consonant (right preserved).
    if left.endswith("m") and b not in VOWELS_ALL:
        return left[:-1] + "ṃ" + right
    # Stop assimilation at joints (right onset geminates: consumed).
    if left[-1] in ("t", "d") and b == "c":
        return left[:-1] + "cc" + stem_r
    if left[-1] in ("t", "d") and b == "j":
        return left[:-1] + "jj" + stem_r
    if left[-1] in ("t", "d") and b == "l":
        return left[:-1] + "ll" + stem_r
    if left.endswith("n") and b in ("c", "j"):
        return left[:-1] + "ñ" + right
    return None


def _sib(b: str) -> str:
    """Visarga reflex before a sibilant/palatal/dental/retroflex."""
    if b in ("c", "ch", "ś"):
        return "ś"
    if b in ("ṭ", "ṭh", "ṣ"):
        return "ṣ"
    return "s"


MATRA = {"अ": "", "आ": "ा", "इ": "ि", "ई": "ी", "उ": "ु",
         "ऊ": "ू", "ऋ": "ृ", "ॠ": "ॄ", "ए": "े", "ऐ": "ै",
         "ओ": "ो", "औ": "ौ"}


# Homorganic nasal + stop of its own class <-> anusvara + stop.
# Always the same word ("sambandha" == "saṃbandha"); folded both sides.
# (Classes written in Devanagari code points — the string under test
# is Deva, where IAST letters never occur.)
NASAL_RE = re.compile("([\u0919\u091e\u0923\u0928\u092e])\u094d(?=[\u0915-\u0939])")


def canon_deva(s: str) -> str:
    """Devanagari string -> canonical comparison form (no spaces/punct).

    Folds (applied to BOTH sides, so only phonetically identical
    spellings can ever match):
    - C+halant+independent-vowel into CV ("kim"+"akurvata" -> "kimakurvata")
    - homorganic nasal+halant+stop into anusvara+stop
      ("sambandha" -> "saṃbandha")
    - anusvara + independent vowel into m+CV ("kiṃ"+"atra" -> "kimatra";
      true anusvara never precedes a vowel letter)
    True C+halant+C conjuncts are untouched (no vowel follows).
    """
    s = unicodedata.normalize("NFC", s)
    # Folds run on spaceless text: joints hide behind spaces otherwise.
    s = re.sub(r"\s+", "", s)
    # ॐ is always and only the syllable om: fold to ओ + anusvara.
    # (Without this, detransliterate's word-initial OM rule makes
    # identical "oṃkāra" readings differ by spacing alone.)
    s = s.replace("ॐ", "ओ\u0902")
    # Candrabindu and anusvara never contrast in Sanskrit orthography
    # (शुभाँल्लोकान् == शुभांल्लोकान्); fold to anusvara.
    s = s.replace("ँ", "ं")
    # Visarga before a sibilant is optionally assimilated in writing
    # (समःशत्रौ == समश्शत्रौ); fold to the doubled sibilant.
    s = re.sub("ः([शषस])", "\\1\u094d\\1", s)
    # Consonant doubling after anusvara is optional external sandhi
    # (कृतांलोकान् == कृतांल्लोकान्, where ll is ल+halant+ल);
    # fold to single.
    s = re.sub("ं([क-ह])\u094d\\1", r"ं\1", s)
    s = re.sub("([\u0915-\u0939])\u094d([\u0905-\u0914])",
               lambda m: m.group(1) + MATRA.get(m.group(2), m.group(2)), s)
    s = NASAL_RE.sub("ं", s)
    s = re.sub("ं([\u0905-\u0914])",
               lambda m: "म" + MATRA.get(m.group(1), m.group(1)), s)
    return "".join(c for c in s if "\u0900" <= c <= "\u097F"
                   and c not in "।॥ऽ'’")


UVACA_RE = re.compile(r"^\s*\S+(?:\s+\S+)?\s*[उु]वाच\s*$")
# NOTE: glued headers fuse as ...न् + ु + वाच (u-matra, U+0941), NOT
# independent उ (U+0909). Matching only उ silently skipped every
# श्रीभगवानुवाच header — that bug cost 19 false STRUCTURE flags.


def split_halves(devanagari: str):
    """Our stored lines -> (first-half, second-half) or None.

    2-line verses split cleanly; 4-line verses (triṣṭubh-style layout
    in ch 8/9/11/15) pair pādas; a leading uvāca header line is
    dropped (metadata, identical across traditions). Anything else
    returns None -> flagged, never guessed.
    """
    lines = [ln for ln in devanagari.split("\n") if ln.strip()]
    if lines and UVACA_RE.match(lines[0]):
        lines = lines[1:]
    if len(lines) == 2:
        return lines[0], lines[1]
    if len(lines) == 4:
        return lines[0] + " " + lines[1], lines[2] + " " + lines[3]
    return None


def witness_to_deva(iast_text: str) -> str:
    """IAST witness half -> joined Devanagari (spaces kept pre-canon)."""
    words = clean_iast(strip_iast_noise(iast_text)).replace("'", "").split()
    joined = join_external(words)
    return detransliterate(joined)


def char_diff(a: str, b: str, context: int = 12) -> str:
    """First divergence window, human-readable."""
    n = min(len(a), len(b))
    for i in range(n):
        if a[i] != b[i]:
            return (f"@{i}: ours[..{a[max(0,i-context):i+context]}..] "
                    f"vs wit[..{b[max(0,i-context):i+context]}..]")
    if len(a) != len(b):
        return f"common prefix {n}, lengths {len(a)} vs {len(b)}"
    return ""


def diff_verse(ours_deva: str, wit: dict) -> dict:
    """Compare our two halves against witness a/c pādas."""
    halves = split_halves(ours_deva)
    if halves is None:
        return {"status": "DIVERGENCE",
                "detail": "our line structure is not 2-or-4 lines",
                "ours": ours_deva,
                "witness": (wit.get("a", "") + " / " + wit.get("c", ""))}
    our_halves = list(halves)
    results = []
    all_match = True
    any_joined = False
    for ours_h, key in zip(our_halves, ("a", "c")):
        wraw = wit.get(key, "")
        if not wraw:
            return {"status": "MISSING", "detail": f"witness lacks pāda {key}",
                    "ours": ours_deva, "witness": ""}
        w_deva = witness_to_deva(wraw)
        o_c = canon_deva(ours_h)
        w_c = canon_deva(w_deva)
        if o_c == w_c:
            results.append("match")
            continue
        # Tier 2: join OUR side through IAST (transliterate is audited
        # separately in Stage 3; any fault here surfaces as flags,
        # never as silent passes).
        try:
            o_joined = canon_deva(detransliterate(
                join_external(clean_iast(transliterate(ours_h)).split())))
        except Exception:
            o_joined = ""
        if o_joined and o_joined == w_c:
            results.append("sandhi")
            any_joined = True
            continue
        all_match = False
        results.append("div:" + char_diff(o_c, w_c))
    if all_match and not any_joined:
        return {"status": "MATCH"}
    if all_match:
        return {"status": "SANDHI-JOINT"}
    divs = [r for r in results if r.startswith("div:")]
    return {"status": "DIVERGENCE", "detail": "; ".join(divs),
            "ours": ours_deva,
            "witness": (wit.get("a", "") + " / " + wit.get("c", ""))}


def detransliterate_stable(s: str) -> str:
    """detransliterate that never throws (flags feed human review)."""
    try:
        return detransliterate(join_external(s.split()))
    except Exception:
        return s


INVISIBLE = set("\u200b\u200c\u200d\ufeff\u00ad\u2060")


def scan_invisible(out):
    """Find format/invisible chars in our Devanagari (informational).

    A ZWNJ mid-conjunct (as in 1:22's योद्‌धुकामान्) changes rendering
    on some fonts. Reported, never auto-stripped: identical policy to
    textual divergences (flag, human decides).
    """
    for ch in sorted(CHAPTERS):
        doc = json.loads((WORK / f"ch{ch:02d}.json").read_text(encoding="utf-8"))
        for v in doc["verses"]:
            found = sorted({c for c in v["devanagari"] if c in INVISIBLE})
            if found:
                out.append({"id": v["id"], "check": "invisible-char",
                            "detail": ",".join(f"U+{ord(c):04X}" for c in found)})
    return out


def cmd_witness(args) -> int:
    if args.fetch:
        spec = WITNESSES["gretil"]
        req = urllib.request.Request(
            spec["url"],
            headers={"User-Agent": "GitaKraft-content/0.1 (research import)"})
        with urllib.request.urlopen(req, timeout=120) as resp:
            blob = resp.read()
        out = RAW / spec["file"]
        out.write_bytes(blob)
        sha = hashlib.sha256(blob).hexdigest()
        print(f"witness: gretil {len(blob)} bytes sha256={sha[:12]}")
        return 0
    chapters = [args.chapter] if args.chapter else sorted(CHAPTERS)
    raw = RAW / WITNESSES["gretil"]["file"]
    if not raw.exists():
        print("witness: missing raw file (run witness --fetch first)")
        return 1
    wit = parse_gretil(raw)
    import hashlib
    sha = hashlib.sha256(raw.read_bytes()).hexdigest()
    report = {"witness": {"name": "gretil",
                          "url": WITNESSES["gretil"]["url"],
                          "file": WITNESSES["gretil"]["file"],
                          "sha256": sha,
                          "note": WITNESSES["gretil"]["note"]},
              "verses": {}, "summary": {}, "encoding": {}}
    counts: dict = {}
    failures = 0
    for ch in chapters:
        doc = json.loads((WORK / f"ch{ch:02d}.json").read_text(encoding="utf-8"))
        for v in doc["verses"]:
            vid = v["id"]
            w = wit.get(vid)
            if w is None or (not w["a"] and not w["c"]):
                rec = {"status": "MISSING", "detail": "absent from witness"}
                failures += 1
            else:
                rec = diff_verse(v["devanagari"], w)
                # Speaker cross-check where we carry a tag.
                if v.get("speaker") and w.get("speaker"):
                    ours_sp = canon_deva(v["speaker"])
                    wit_sp = canon_deva(detransliterate_stable(w["speaker"]))
                    if ours_sp not in wit_sp and wit_sp not in ours_sp:
                        rec["speaker_note"] = (
                            f"ours={v['speaker']} wit={w['speaker']}")
                if rec["status"] in ("DIVERGENCE", "MISSING"):
                    failures += 1
            report["verses"][vid] = rec
            counts[rec["status"]] = counts.get(rec["status"], 0) + 1
    report["summary"] = counts
    enc: list = []
    if args.chapter is None:
        scan_invisible(enc)
    report["encoding"] = enc
    npath = DATA / "witness_notes.json"
    if npath.exists():
        notes = json.loads(npath.read_text(encoding="utf-8"))
        for vid, note in notes.items():
            if vid in report["verses"]:
                report["verses"][vid]["adjudication"] = note
    (WORK / "witness_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"witness: {counts} failures={failures} "
          f"-> data/content/work/witness_report.json")
    return 1 if failures else 0
