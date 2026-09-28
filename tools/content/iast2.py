"""Second transliteration engine (Stage 3 differential).

Deliberately different ALGORITHM from iast.py (char-walker with
lookahead / greedy pending-state tokenizer):

- transliterate2: akṣara-cluster FIRST (regex segments the string into
  akṣaras, then each akṣara maps atomically), vs iast.py's
  character-by-character walk.
- detransliterate2: IAST SYLLABLE parse (consonant-cluster + vowel
  split, each syllable emits one akṣara), vs iast.py's greedy
  longest-token tokenizer with pending-consonant state.

Table VALUES are shared linguistic facts (ka=k); independence lives in
the segmentation and state handling — the parts that ever break.
Quirk-compatibility is REQUIRED: observable behavior must match iast.py
exactly, including documented approximations (nukta pass-through, the
bare-ḷ -> ṛ-matra rule, word-initial oṃ -> ॐ). If a quirk is ever
fixed, it is fixed in BOTH engines together; a silent behavioral split
between them is itself a failure.

Differential harness: cmd_iast2 runs both engines over every stored
Devanagari string (700 verses), every stored IAST string, and every
GRETIL witness half, and fails loudly on ANY difference.
"""
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "data" / "content" / "work"
RAW = ROOT / "data" / "content" / "raw"

VOW = {"अ": "a", "आ": "ā", "इ": "i", "ई": "ī", "उ": "u", "ऊ": "ū",
       "ऋ": "ṛ", "ॠ": "ṝ", "ऌ": "ḷ", "ॡ": "ḹ",
       "ए": "e", "ऐ": "ai", "ओ": "o", "औ": "au"}
CONS = {"क": "k", "ख": "kh", "ग": "g", "घ": "gh", "ङ": "ṅ",
        "च": "c", "छ": "ch", "ज": "j", "झ": "jh", "ञ": "ñ",
        "ट": "ṭ", "ठ": "ṭh", "ड": "ḍ", "ढ": "ḍh", "ण": "ṇ",
        "त": "t", "थ": "th", "द": "d", "ध": "dh", "न": "n",
        "प": "p", "फ": "ph", "ब": "b", "भ": "bh", "म": "m",
        "य": "y", "र": "r", "ल": "l", "व": "v",
        "श": "ś", "ष": "ṣ", "स": "s", "ह": "h",
        "ळ": "ḷ", "ज्ञ": "jña", "क्ष": "kṣ"}
MATRA = {"ा": "ā", "ि": "i", "ी": "ī", "ु": "u", "ू": "ū",
         "ृ": "ṛ", "ॄ": "ṝ", "ॆ": "e", "े": "e", "ै": "ai",
         "ॉ": "o", "ो": "o", "ौ": "au", "ॎ": "o", "ॏ": "o"}
NUKTA = {"क": "q", "ख": "kh", "ग": "ġ", "ज": "z", "झ": "zh",
         "ड": "ṛ", "ढ": "ṛh", "फ": "f", "य": "ẏ", "ल": "ḷ"}

C = "".join(CONS)  # consonant class for the cluster regex
_MARKS = "ािीुूृॄॆेैॉोौंःँ"  # matras + anusvara/visarga/candrabindu
CLUSTER_RE = re.compile(
    "[" + C + "]़?(?:्[" + C + "]़?)*्?(?:[" + _MARKS + "])*"  # cluster+marks
    "|[अ-औ](?:[ंःँ])?"              # independent vowel + optional sign
    "|ॐ|ऽ|[।॥]|[०-९]"                    # signs, dandas, digits
    "|[्]"                                # stray virama (dropped below)
    "|.", re.S)                          # anything else, one char


def transliterate2(dev: str) -> str:
    """Devanagari -> IAST via akṣara clustering."""
    out = []
    for m in CLUSTER_RE.finditer(dev):
        ak = m.group(0)
        if ak[0] in VOW and (len(ak) == 1 or ak[1:] in "ंःँ"):
            out.append(VOW[ak[0]])
            if ak[1:] == "ं":
                out.append("ṃ")
            elif ak[1:] == "ः":
                out.append("ḥ")
            elif ak[1:] == "ँ":
                out.append("m̐")
        elif ak == "ॐ":
            out.append("oṃ")
        elif ak == "ऽ":
            out.append("'")
        elif ak == "।":
            out.append("|")
        elif ak == "॥":
            out.append("||")
        elif ak in "०१२३४५६७८९":
            out.append(str("०१२३४५६७८९".index(ak)))
        elif ak[0] in CONS:
            # Split cluster on viramas. Bare heads (all but last) emit
            # plain romans; the final head carries trailing marks.
            # A trailing virama means halanta: final head is bare too.
            bare_final = ak.endswith("्")
            core_ak = ak[:-1] if bare_final else ak
            chunks = core_ak.split("्")
            m = re.match(r"^(.+?)([" + _MARKS + "]*)$", chunks[-1])
            core, marks = m.group(1), m.group(2)
            bare = []
            for h in chunks[:-1]:
                base = h[0]
                bare.append(NUKTA.get(base, CONS[base])
                            if "़" in h else CONS[base])
            base = core[0]
            piece = [NUKTA.get(base, CONS[base])
                     if "़" in core else CONS[base]]
            vowel_out = False
            for mk in marks:
                if mk in MATRA:
                    piece.append(MATRA[mk])
                    vowel_out = True
                elif mk == "ं":
                    # After a matra the anusvara is a bare sign (tā + ṃ),
                    # exactly like iast.py's standalone-sign iteration.
                    piece.append("ṃ" if vowel_out else "aṃ")
                elif mk == "ः":
                    piece.append("ḥ" if vowel_out else "aḥ")
                elif mk == "ँ":
                    piece.append("m̐" if vowel_out else "am̐")
                else:
                    piece.append(mk)
            if not marks and not bare_final:
                piece.append("a")  # inherent -a
            bare.append("".join(piece))
            out.append("".join(bare))
        else:
            # Stray mark or foreign char: iast.py drops stray viramas
            # and passes everything else through.
            if ak != "्":
                out.append(ak)
    return "".join(out)


# IAST syllable: consonant cluster + vowel nucleus (or bare vowel,
# or special sign). Ordered longest-first for the regex alternation.
_DIGRAPHS = ["kh", "gh", "ch", "jh", "ṭh", "ḍh", "th", "dh",
             "ph", "bh", "kṣ", "jñ"]
_SINGLES = ["ṅ", "ñ", "ṭ", "ḍ", "ṇ", "ś", "ṣ", "ḷ",
            "k", "g", "c", "j", "t", "d", "n", "p", "b", "m",
            "y", "r", "l", "v", "s", "h"]
_VOWELS = ["ai", "au", "aṃ", "aḥ", "am̐", "ā", "ī", "ū", "ṛ", "ṝ",
           "e", "o", "a", "i", "u", "oṃ", "m̐", "ṃ"]
SYL_RE = re.compile(
    "(?P<cons>(?:" + "|".join(_DIGRAPHS + _SINGLES) + ")+)"
    "(?P<vow>" + "|".join(_VOWELS) + ")?"
    "|(?P<bare>[aāiīuūṛṝeēoōaiṅñṭḍṇśṣḥṃ ḷ]+?)"
    "|(?P<other>\\|\\||.|\\s)", re.S)

CONS_DEVA = {"k": "क", "kh": "ख", "g": "ग", "gh": "घ", "ṅ": "ङ",
             "c": "च", "ch": "छ", "j": "ज", "jh": "झ", "ñ": "ञ",
             "ṭ": "ट", "ṭh": "ठ", "ḍ": "ड", "ḍh": "ढ", "ṇ": "ण",
             "t": "त", "th": "थ", "d": "द", "dh": "ध", "n": "न",
             "p": "प", "ph": "फ", "b": "ब", "bh": "भ", "m": "म",
             "y": "य", "r": "र", "l": "ल", "v": "व",
             "ś": "श", "ṣ": "ष", "s": "स", "h": "ह",
             "kṣ": "क्ष", "jñ": "ज्ञ"}
MATRA_DEVA = {"ā": "ा", "i": "ि", "ī": "ी", "u": "ु", "ū": "ू",
              "ṛ": "ृ", "ṝ": "ॄ", "e": "े", "ai": "ै",
              "o": "ो", "au": "ौ", "ḷ": "ृ"}  # bare-ḷ quirk, as iast.py
VOW_DEVA = {"a": "अ", "ā": "आ", "i": "इ", "ī": "ई", "u": "उ",
            "ū": "ऊ", "ṛ": "ऋ", "ṝ": "ॠ", "e": "ए", "ai": "ऐ",
            "o": "ओ", "au": "औ"}


def _cons_seq(seq: str) -> str:
    """IAST consonant run -> virama-joined Devanagari consonants."""
    out = []
    i = 0
    toks = sorted(CONS_DEVA, key=len, reverse=True)
    while i < len(seq):
        for tok in toks:
            if seq.startswith(tok, i):
                if out:
                    out.append("्")
                out.append(CONS_DEVA[tok])
                i += len(tok)
                break
        else:
            out.append(seq[i])  # unknown (nukta q/z/f): pass through
            i += 1
    return "".join(out)


def detransliterate2(iast: str) -> str:
    """IAST -> Devanagari via syllable parsing."""
    out = []
    i = 0
    n = len(iast)
    # Tokenizer: longest-match over consonant runs, vowel nuclei,
    # specials, and single-char fallbacks — then syllables assemble
    # from the token stream. Word-final bare consonants take halanta,
    # exactly like iast.py's pending rule.
    ctoks = sorted(CONS_DEVA, key=len, reverse=True)
    vtoks = sorted([v for v in _VOWELS if v not in ("oṃ",)],
                   key=len, reverse=True)
    while i < n:
        # Bare nasal signs win over consonant parsing: iast.py's greedy
        # tokenizer matches "m̐"/"ṃ" as units (candrabindu/anusvara),
        # never as consonant onsets.
        if iast.startswith("m̐", i):
            out.append("ँ")
            i += 2
            continue
        if iast[i] == "ṃ":
            out.append("ं")
            i += 1
            continue
        if iast.startswith("oṃ", i):
            # Word-initial oṃ is the praṇava; after a cluster vowel it
            # reads as matra-o + anusvara — reproduce iast.py exactly.
            prev = out[-1] if out else ""
            if not out or out[-1] in (" ", "\n", "।", "॥", "'"):
                out.append("ॐ")
            else:
                # Mid-word: if previous char ends a bare cluster, the o
                # is its matra; else independent syllable + anusvara.
                if prev and prev in "कखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसहळक्षज्ञ":
                    out.append("ो")
                    out.append("ं")
                else:
                    out.append("ओ")
                    out.append("ं")
            i += 2
            continue
        matched = False
        for tok in ctoks:
            if iast.startswith(tok, i):
                # Consonant run: extend while more consonants follow.
                j = i
                run = ""
                while j < n:
                    for t2 in ctoks:
                        if iast.startswith(t2, j):
                            run += t2
                            j += len(t2)
                            break
                    else:
                        break
                # Vowel nucleus after the run? (bare ḷ reads as ṛ-matra:
                # quirk-compatibility with iast.py, never in the corpus.)
                vow = None
                for v in vtoks + ["ḷ"]:
                    if iast.startswith(v, j):
                        vow = v
                        j += len(v)
                        break
                deva_cluster = _cons_seq(run)
                if vow is None:
                    # Bare run: halanta unless a vowel comes later in a
                    # way iast.py would keep pending — pending closes on
                    # anything non-vocalic, so halanta now; a following
                    # 'a' is then inherent and implicit. Peek: bare 'a'
                    # right after means the cluster takes inherent -a
                    # (no halanta).
                    if iast.startswith("a", j) and not iast.startswith(
                            ("ai", "au", "aṃ", "aḥ", "am̐"), j):
                        out.append(deva_cluster)
                        j += 1
                    else:
                        out.append(deva_cluster)
                        out.append("्")
                elif vow == "a":
                    out.append(deva_cluster)  # inherent -a, nothing added
                elif vow in ("aṃ", "aḥ", "am̐"):
                    out.append(deva_cluster)
                    out.append("ं" if "ṃ" in vow
                               else ("ँ" if "m̐" in vow else "ः"))
                else:
                    out.append(deva_cluster)
                    out.append(MATRA_DEVA[vow])
                i = j
                matched = True
                break
        if matched:
            continue
        for v in vtoks + ["oṃ", "m̐", "ṃ"]:
            if iast.startswith(v, i):
                if v in VOW_DEVA:
                    out.append(VOW_DEVA[v])
                elif v in ("aṃ", "am̐", "ṃ"):
                    out.append("ं")
                elif v == "m̐":
                    out.append("ँ")
                elif v == "aḥ":
                    out.append("ः")
                i += len(v)
                matched = True
                break
        if matched:
            continue
        c = iast[i]
        if c.isdigit():
            out.append("०१२३४५६७८९"[int(c)])
        elif c == "|":
            if iast.startswith("||", i):
                out.append("॥")
                i += 1
            else:
                out.append("।")
        elif c == "'":
            out.append("ऽ")
        elif c == "ḥ":
            out.append("ः")
        elif c == "ṃ":
            out.append("ं")
        elif c == "m̐":
            out.append("ँ")
        else:
            out.append(c)
        i += 1
    return "".join(out)


def cmd_iast2(args) -> int:
    """Differential: iast vs iast2 over every production string."""
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from iast import transliterate, detransliterate
    from witnesses import parse_gretil
    failures = 0

    def check(tag, a, b):
        nonlocal failures
        if a != b:
            failures += 1
            if failures <= 10:
                print(f"iast2: MISMATCH {tag}\n  iast : {a!r}\n  iast2: {b!r}")

    deva_strings, iast_strings = [], []
    chapters = [args.chapter] if args.chapter else list(range(1, 19))
    for ch in chapters:
        doc = json.loads((WORK / f"ch{ch:02d}.json").read_text(
            encoding="utf-8"))
        for v in doc["verses"]:
            deva_strings.append((v["id"], v["devanagari"]))
            iast_strings.append((v["id"], v.get("iast", "")))
    for vid, deva in deva_strings:
        check(f"{vid} transliterate", transliterate(deva),
              transliterate2(deva))
    for vid, txt in iast_strings:
        check(f"{vid} detransliterate", detransliterate(txt),
              detransliterate2(txt))
        # Round-trip through engine 2 must return NFC Devanagari.
        rt = detransliterate2(transliterate2(
            detransliterate(txt) if txt else ""))
        if txt and rt != unicodedata.normalize(
                "NFC", detransliterate(txt)):
            check(f"{vid} round-trip2", detransliterate(txt), rt)
    wit = parse_gretil(RAW / "gretil_bhgce.htm")
    for vid, w in sorted(wit.items()):
        for half in ("a", "c"):
            tag = f"gretil-{vid}{half}"
            check(tag, detransliterate(w[half]), detransliterate2(w[half]))
    print(f"iast2: {len(deva_strings)} deva + {len(iast_strings)} iast + "
          f"{2 * len(wit)} gretil checked, {failures} mismatches")
    return 1 if failures else 0
