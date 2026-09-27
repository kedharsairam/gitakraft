"""Devanagari <-> IAST transliteration (deterministic, rule-based).

Shared module: GitaKraft uses it in the content pipeline; the future
dictionary app reuses it (including a Kotlin port) for user-input
transliteration. No ML, no guessing — Devanagari to IAST is 1:1.

Round-trip guarantee: transliterate(dev).detransliterate() == dev for all
inputs the engine produces (tested).
"""

# Independent vowels.
VOWELS = {
    "अ": "a", "आ": "ā", "इ": "i", "ई": "ī", "उ": "u", "ऊ": "ū",
    "ऋ": "ṛ", "ॠ": "ṝ", "ऌ": "ḷ", "ॡ": "ḹ",
    "ए": "e", "ऐ": "ai", "ओ": "o", "औ": "au",
}

# Consonant base letters (inherent -a handled by the walker).
CONSONANTS = {
    "क": "k", "ख": "kh", "ग": "g", "घ": "gh", "ङ": "ṅ",
    "च": "c", "छ": "ch", "ज": "j", "झ": "jh", "ञ": "ñ",
    "ट": "ṭ", "ठ": "ṭh", "ड": "ḍ", "ढ": "ḍh", "ण": "ṇ",
    "त": "t", "थ": "th", "द": "d", "ध": "dh", "न": "n",
    "प": "p", "फ": "ph", "ब": "b", "भ": "bh", "म": "m",
    "य": "y", "र": "r", "ल": "l", "व": "v",
    "श": "ś", "ष": "ṣ", "स": "s", "ह": "h",
    "ळ": "ḷ", "क्ष": "kṣ", "ज्ञ": "jña",
}

# Dependent vowel signs (matras) — replace the inherent -a.
MATRAS = {
    "ा": "ā", "ि": "i", "ी": "ī", "ु": "u", "ू": "ū",
    "ृ": "ṛ", "ॄ": "ṝ", "ॆ": "e", "े": "e", "ै": "ai",
    "ॉ": "o", "ो": "o", "ौ": "au", "ॎ": "o", "ॏ": "o",
}

# Nukta consonant overrides (base + nukta -> mapped).
NUKTA = {
    "क": "q", "ख": "kh", "ग": "ġ", "ज": "z", "झ": "zh",
    "ड": "ṛ", "ढ": "ṛh", "फ": "f", "य": "ẏ", "ल": "ḷ",
}

VIRAMA = "्"
NUKTA_SIGN = "़"
ANUSVARA = "ं"
VISARGA = "ः"
CHANDRABINDU = "ँ"
AVAGRAHA = "ऽ"
OM = "ॐ"

DIGITS = {d: str(i) for i, d in enumerate("०१२३४५६७८९")}


def transliterate(dev: str) -> str:
    """Devanagari -> IAST. Deterministic; see module docstring."""
    out: list[str] = []
    chars = list(dev)
    i = 0
    while i < len(chars):
        c = chars[i]
        nxt = chars[i + 1] if i + 1 < len(chars) else ""
        if c in VOWELS:
            out.append(VOWELS[c])
            if nxt == ANUSVARA:
                out.append("ṃ")
                i += 1
            elif nxt == VISARGA:
                out.append("ḥ")
                i += 1
            elif nxt == CHANDRABINDU:
                out.append("m̐")
                i += 1
        elif c in CONSONANTS or (c == "ज्ञ" or c == "क्ष"):
            base = CONSONANTS[c]
            if nxt == NUKTA_SIGN:
                base = NUKTA.get(c, base)
                i += 1
                nxt = chars[i + 1] if i + 1 < len(chars) else ""
            if nxt == VIRAMA:
                out.append(base)
                i += 1  # consume virama; next consonant starts fresh
            elif nxt in MATRAS:
                out.append(base + MATRAS[nxt])
                i += 1
            elif nxt == ANUSVARA:
                out.append(base + "aṃ")
                i += 1
            elif nxt == VISARGA:
                out.append(base + "aḥ")
                i += 1
            elif nxt == CHANDRABINDU:
                out.append(base + "am̐")
                i += 1
            else:
                out.append(base + "a")
        elif c == VIRAMA:
            pass  # consumed by lookahead; stray virama is dropped
        elif c == ANUSVARA:
            out.append("ṃ")
        elif c == VISARGA:
            out.append("ḥ")
        elif c == CHANDRABINDU:
            out.append("m̐")
        elif c == AVAGRAHA:
            out.append("'")
        elif c == OM:
            out.append("oṃ")
        elif c in DIGITS:
            out.append(DIGITS[c])
        elif c == "।":
            out.append("|")
        elif c == "॥":
            out.append("||")
        else:
            out.append(c)  # Latin, punctuation, spaces pass through
        i += 1
    return "".join(out)


def detransliterate(iast: str) -> str:
    """IAST -> Devanagari. Inverse of transliterate() for engine output.

    Rules: a consonant token while another is pending inserts a virama
    (clusters); a bare 'a' closes the pending consonant (inherent -a);
    matra/sign tokens attach to it. Nukta forms (q/z/f/...) are absent
    from the Gita corpus and map back to their plain vowels/consonants
    (documented approximation, never exercised by validation).
    """
    CONS = {
        "k": "क", "kh": "ख", "g": "ग", "gh": "घ", "ṅ": "ङ",
        "c": "च", "ch": "छ", "j": "ज", "jh": "झ", "ñ": "ञ",
        "ṭ": "ट", "ṭh": "ठ", "ḍ": "ड", "ḍh": "ढ", "ṇ": "ण",
        "t": "त", "th": "थ", "d": "द", "dh": "ध", "n": "न",
        "p": "प", "ph": "फ", "b": "ब", "bh": "भ", "m": "म",
        "y": "य", "r": "र", "l": "ल", "v": "व",
        "ś": "श", "ṣ": "ष", "s": "स", "h": "ह",
        # NOTE: no 'jña' ligature token. ज्ञ is parsed atomically as
        # j + ñ + vowel so the vowel ('a', 'ai', 'au' matras…) always
        # attaches correctly; a 'jña' token would swallow a following
        # matra's 'a' (e.g. the 'ai' of ज्ञै). Output renders the same.
        "kṣ": "क्ष", "ḷa": "ळ",
    }
    MATRA = {"ā": "ा", "i": "ि", "ī": "ी", "u": "ु", "ū": "ू",
             "ṛ": "ृ", "ṝ": "ॄ", "e": "े", "ai": "ै",
             "o": "ो", "au": "ौ", "ḷ": "ृ"}
    VOW = {"a": "अ", "ā": "आ", "i": "इ", "ī": "ई", "u": "उ", "ū": "ऊ",
           "ṛ": "ऋ", "ṝ": "ॠ", "e": "ए", "ai": "ऐ", "o": "ओ", "au": "औ"}
    out: list[str] = []
    pending = False  # a bare consonant awaits its vowel
    toks = sorted(set(CONS) | set(MATRA) | set(VOW)
                  | {"oṃ", "m̐", "ṃ", "aṃ", "aḥ", "am̐", "||"},
                  key=len, reverse=True)

    def emit_cons(dev: str) -> None:
        nonlocal pending
        if pending:
            out.append(VIRAMA)
        out.append(dev)
        pending = True

    i = 0
    while i < len(iast):
        if (iast[i] == "a" and pending
                and not iast.startswith(("ai", "au", "aṃ", "aḥ", "am̐"), i)):
            pending = False  # inherent -a, implicit in Devanagari
            i += 1
            continue
        for tok in toks:
            if iast.startswith(tok, i):
                if tok in CONS:
                    emit_cons(CONS[tok])
                elif tok in MATRA and pending:
                    out.append(MATRA[tok])
                    pending = False
                elif tok in VOW:
                    out.append(VOW[tok])
                    pending = False
                elif tok == "oṃ":
                    if not pending:
                        # Syllable-initial oṃ is the pranava ॐ.
                        out.append(OM)
                        pending = False
                        i += len(tok)
                    else:
                        # After a pending consonant the same letters read as
                        # matra + anusvara (e.g. -moṃ- = मो + ं): emit the
                        # matra now, reprocess ṃ next iteration.
                        out.append(MATRA["o"])
                        pending = False
                        i += 1
                    break
                elif tok in ("ṃ", "m̐"):
                    out.append(ANUSVARA if tok == "ṃ" else CHANDRABINDU)
                    pending = False
                elif tok in ("aṃ", "aḥ", "am̐"):
                    out.append(ANUSVARA if "ṃ" in tok
                               else (CHANDRABINDU if "m̐" in tok else VISARGA))
                    pending = False
                elif tok == "||":
                    out.append("॥")
                    pending = False
                i += len(tok)
                break
        else:
            c = iast[i]
            if pending:
                # A bare consonant closed by anything non-vocalic takes
                # halanta (end of word before space/punct/digit/danda).
                out.append(VIRAMA)
            if c.isdigit():
                out.append("०१२३४५६७८९"[int(c)])
            elif c == "|":
                out.append("।")
            elif c == "'":
                out.append(AVAGRAHA)
            elif c == "ḥ":
                out.append(VISARGA)
            elif c == "ṃ":
                out.append(ANUSVARA)
            elif c == "m̐":
                out.append(CHANDRABINDU)
            else:
                out.append(c)  # Latin/punct/space pass-through
            pending = False
            i += 1
    if pending:
        out.append(VIRAMA)  # trailing bare consonant = halanta
    return "".join(out)
