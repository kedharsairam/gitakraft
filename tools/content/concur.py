"""Stage 5: English two-source consensus (concur).

For each verse, our meaning_simple is checked INDEPENDENTLY against
Telang's prose translation and Arnold's poetic translation:

  CONSENSUS      both sources cover our meaning's content lemmas
  SINGLE-SOURCE  only one source covers it (our line follows one
                 translation's reading -> flagged for read-through)
  ORPHAN         neither source covers it (drift candidate or heavy
                 OCR noise in Telang -> flagged, never failed)
  NAME-LIST      meaning is names only; carried by the anchor chain
  ARNOLD-ABSENT  Arnold omits the verse entirely -> quarantined by
                 construction, never a failure

Doctrine mirrors the Sanskrit witness engine: normalization is
conservative by design, so misses surface as FLAGS, never as silent
passes. The report lists uncovered lemmas per flag, making each one
actionable in the review packs. Proper names are excluded from scoring
(people/places ride the anchor chain + Sanskrit engine; Telang's OCR
mangles them beyond repair: Pfiwflfavas).
"""
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "data" / "content" / "work"

STOPWORDS = set("""a an the and or but of to in on at for with by from as is are
    was were be been being it its this that these those he him his she her
    they them their we us our you your i my me mine he she it thou thee thy
    thine ye hath doth dost ere unto upon into out up down so such no
    not nor yet still even ever never always o oh ah alas lo behold saith
    shall will would should could may might must can do does did done have
    has had having what which who whom whose where when why how there here
    then than too very much many more most own same all both each every any
    some few one two first thus hence therefore wherefore nay yea
    neither nor either without whoever whoso whatever whichever only merely
    solely whether alike thus plus non
    """.split())

# Proper-noun aliases -> canonical lemma. Both translations rename
# freely (Arnold: Driver for Krishna, Scourge of Foes for Parantapa).
ALIASES = {
    "sanjaya": "sanjaya", "sawaya": "sanjaya", "sanjay": "sanjaya",
    "dhritarashtra": "dhritarashtra", "dhritarashtr": "dhritarashtra",
    "krishna": "krishna", "madhusudan": "krishna", "driver": "krishna",
    "madhusudana": "krishna", "janardana": "krishna", "keshava": "krishna",
    "kesava": "krishna", "govinda": "krishna", "hrishikesha": "krishna",
    "arjuna": "arjuna", "partha": "arjuna", "parantapa": "arjuna",
    "scourge": "arjuna", "dhananjaya": "arjuna", "kaunteya": "arjuna",
    "gudakesha": "arjuna", "bharata": "arjuna",
    "bhishma": "bhishma", "drona": "drona",
}

# Words the stemmer must not touch.
KEEP = {"nothing", "something", "everything", "anything"}


def stem(word: str) -> str:
    """Conservative suffix strip. Short words untouched."""
    if word in KEEP:
        return word
    if len(word) <= 1:
        return ""
    if word.endswith("ies") and len(word) - 3 >= 3:
        return word[:-3] + "y"  # duties -> duty (not duti/dutie)
    if word.endswith("ied") and len(word) - 3 >= 3:
        return word[:-3] + "y"
    if word.endswith("ses") and len(word) - 3 >= 4:
        return word[:-1]  # senses -> sense (not sens)
    if word.endswith("s") and len(word) - 1 >= 3 and not word.endswith("ss"):
        return word[:-1]  # short plurals: bows -> bow (stopwords like
    # this/thus/as/is never reach the stemmer; was/has/his have bases < 3)
    if len(word) <= 4:
        return word
    if word.endswith("eth") and len(word) - 3 >= 4:
        return word[:-3]  # archaic cometh/goeth/saith
    if word.endswith("e") and len(word) >= 6:
        return word[:-1]  # discipline/disciplined share a stem
    for suf in ("ingly", "edly", "ing", "ed", "es", "ly"):
        if word.endswith(suf) and len(word) - len(suf) >= 4:
            return word[: -len(suf)]
    return word


# Closed-class verb/word families -> canonical lemma, applied pre-stem
# so said/says/asking/spake collapse regardless of suffix rules.
VERBS = {}
for _canon, _forms in {
    "say": {"say", "says", "said", "saying", "ask", "asks", "asked",
            "asking", "tell", "tells", "told", "telling", "speak", "speaks",
            "spoke", "spoken", "speaking", "spake", "address", "addresses",
            "addressed", "addressing", "answer", "answers", "answered",
            "reply", "replies", "replied", "utter", "utters", "uttered",
            "declare", "declares", "declared"},
    "see": {"see", "sees", "saw", "seen", "seeing", "behold", "beholds",
            "beheld", "perceive", "perceives", "perceived", "look",
            "looks", "looked", "looking", "comprehend", "comprehends",
            "comprehended", "comprehending", "find", "finds", "found",
            "grasp", "grasps", "grasped"},
    "slay": {"slay", "slays", "slew", "slain", "kill", "kills", "killed",
             "killing", "smite", "smites", "smote", "smitten"},
    "fight": {"fight", "fights", "fought", "fighting", "battle",
              "battles", "war"},
    "know": {"know", "knows", "knew", "known", "knowing",
             "understand", "understands", "understood", "understanding"},
    "do": {"do", "does", "did", "done", "doing", "wrought"},
    "go": {"go", "goes", "went", "gone", "going"},
    "come": {"come", "comes", "came", "coming"},
    "think": {"think", "thinks", "thought", "thinking"},
    "feel": {"feel", "feels", "felt", "feeling"},
    "give": {"give", "gives", "gave", "given", "giving",
             "renounce", "renounces", "renounced", "renouncing",
             "renunciation", "bestow", "bestows", "bestowed"},
    "take": {"take", "takes", "took", "taken", "taking", "seek",
             "seeks", "sought", "seeking", "cast", "casts", "casting",
             "arise", "arises", "arose", "arisen", "result", "results",
             "resulted", "receive", "receives", "received", "accept",
             "accepts", "accepted", "seize", "seizes", "seized"},
    "make": {"make", "makes", "made", "making"},
    "do": {"do", "does", "did", "done", "doing", "wrought", "perform",
           "performs", "performed", "performing"},
    "die": {"die", "dies", "died", "dying", "dead", "death", "mortal",
            "perish", "perishes", "perished"},
    "live": {"live", "lives", "lived", "living", "alive"},
    "grieve": {"grieve", "grieves", "grieved", "grieving", "mourn",
               "mourns", "mourned", "lament", "laments", "lamented",
               "sorrow", "sorrows", "weep", "weeps", "wept"},
    "win": {"win", "wins", "won", "winning", "victory", "triumph",
            "conquer", "conquers", "conquered", "conquering", "conquest",
            "prevail", "prevails", "prevailed"},
    "hear": {"hear", "hears", "heard", "hearing", "listen", "listens",
             "listened", "hark"},
    "want": {"want", "wants", "wanted", "wanting", "desire", "desires",
             "desired", "desiring", "wish", "wishes", "wished",
             "wishing", "crave", "craves", "craved", "craving", "covet",
             "covets", "coveted", "long", "longs", "longed", "longing"},
    "leave": {"leave", "leaves", "left", "leaving", "abandon", "abandons",
              "abandoned", "abandoning", "forsake", "forsakes", "forsook",
              "forsaken", "relinquish", "relinquished", "renounce",
              "quit", "drop", "drops", "dropped", "dropping", "desert",
              "deserted", "resign", "resigns", "resigned", "submit",
              "submits", "submitted", "yield", "yields", "yielded"},
    "begin": {"begin", "begins", "began", "begun", "beginning",
              "commence", "commences", "commenced", "commencing",
              "start", "starts", "started", "starting"},
}.items():
    for _f in _forms:
        VERBS[_f] = _canon

# Concept synonym sets. Matched on STEMMED forms (precomputed below)
# so discipline/disciplined/devotion all meet. Applied as SOURCE-side
# expansion only: our lemmas must be found in the source.
CONCEPTS = {
    "pity": {"pity", "compassion", "compassionate", "merciful", "mercy"},
    "grief": {"grief", "sorrow", "woe", "sad", "mourn"},
    "duty": {"duty", "duties"},
    "battle": {"battle", "war", "fight", "combat", "warfare"},
    "warrior": {"warrior", "brave", "hero", "soldier", "fighter"},
    "fear": {"fear", "afraid", "dread", "terror", "fright"},
    "anger": {"anger", "wrath", "rage", "angry", "fury"},
    "desire": {"desire", "lust", "craving"},
    "mind": {"mind", "heart", "thought", "intellect"},
    "soul": {"soul", "self", "spirit"},
    "wise": {"wise", "wisdom", "sage", "learned", "sensible"},
    "foolish": {"foolish", "folly", "ignorant", "deluded", "delusion"},
    "pleasure": {"pleasure", "joy", "delight", "happy", "bliss"},
    "pain": {"pain", "suffering", "sorrow", "misery"},
    "eyes": {"eye", "eyes", "vision", "sight"},
    "tears": {"tear", "tears", "weep", "cry"},
    "bow": {"bow", "shaft", "arrow", "archer", "archery", "bowman"},
    "chariot": {"chariot", "car"},
    "king": {"king", "prince", "monarch", "ruler", "sovereign"},
    "heaven": {"heaven", "paradise", "celestial"},
    "action": {"action", "act", "deed", "work"},
    "knowledge": {"knowledge", "wisdom", "learning", "lore"},
    "faith": {"faith", "believe", "trust", "devotion", "devoted"},
    "god": {"god", "lord", "divine", "supreme"},
    "world": {"world", "earth", "universe", "creation"},
    "body": {"body", "bodies", "frame", "corporeal", "embodied"},
    "senses": {"sense", "senses"},
    "peace": {"peace", "tranquillity", "calm", "rest", "serene"},
    "truth": {"truth", "true", "real", "reality", "actual"},
    "discipline": {"discipline", "disciplined", "devotion", "devoted",
                   "application", "perseverance", "practice", "practise",
                   "austerity", "austere", "penance"},
    "wrong": {"wrong", "evil", "sinful", "wicked", "unrighteous",
              "astray", "corrupt", "corrupted", "depraved", "improper",
              "unbecoming"},
    "ultimate": {"ultimate", "supreme", "highest", "absolute",
                 "transcendent", "paramount"},
    "being": {"being", "beings", "creature", "creatures", "living",
              "existence", "mortal", "mortals"},
    "person": {"person", "man", "men"},
    "steady": {"steady", "steadfast", "firm", "fixed", "resolute",
               "stable", "constant", "unshaken", "unshakable"},
    "beyond": {"beyond", "above", "transcend", "transcending", "across"},
    "free": {"free", "liberated", "delivered", "release", "released",
             "emancipate", "unfettered"},
    "stand": {"stand", "arise", "rise", "arisen", "abide", "abides",
              "abiding", "dwell", "dwells", "reside"},
    "back": {"back", "return", "revert", "restore", "restored"},
    "hold": {"hold", "restrain", "restrained", "curb", "check", "uphold"},
    "doer": {"doer", "agent"},
    "rule": {"rule", "govern", "reign", "sovereignty", "kingdom"},
    "hard": {"hard", "difficult", "arduous"},
    "chain": {"chain", "bond", "bonds", "fetter", "fetters", "tie"},
    "turn": {"turn", "resort", "betake"},
    "goal": {"goal", "aim", "object", "purpose"},
    "form": {"form", "shape", "figure", "embodiment"},
    "confuse": {"confusion", "confused", "confound", "confounded",
                "delusion", "deluded", "bewilder", "bewildered",
                "perplex", "perplexed"},
    "mood": {"mood", "moods", "quality", "qualities", "state", "humour"},
    "rite": {"rite", "rites", "ritual", "sacrifice", "sacrifices",
             "worship", "ceremony", "observance"},
    "measure": {"measure", "measureless", "numberless", "countless",
                "unlimited", "infinite", "small", "little", "limited"},
    "clear": {"clear", "pure", "clean", "lucid", "stainless", "spotless"},
    "reach": {"reach", "reaches", "attain", "attains", "attained",
              "obtain", "arrive", "gain"},
    "resolve": {"resolve", "determination", "determined", "resolution",
                "decide", "decided", "decisive"},
    "teacher": {"teacher", "preceptor", "master", "tutor", "instructor",
                "guru"},
    "army": {"army", "host", "hosts", "rank", "ranks", "force", "troops",
             "array"},
    "imperishable": {"imperishable", "indestructible", "undying",
                     "deathless", "immutable", "immortal", "immortality"},
    "indivisible": {"undivided", "indivisible", "unseparated", "impartite"},
    "deathless": {"deathless", "deathlessness", "immortal", "immortality"},
    "path": {"path", "way", "course", "road"},
    "nature": {"nature", "essence", "character", "disposition"},
    "wrong": {"wrong", "evil", "sinful", "wicked", "unrighteous"},
    "born": {"born", "birth", "begotten"},
    "surrender": {"surrender", "yield", "resign", "submit", "devote"},
    "ego": {"ego", "egoism", "selfhood"},
    "observe": {"observe", "behold", "watch", "witness"},
    "knower": {"knower", "knowers", "knowledgeable"},
    "useful": {"useful", "use", "uses", "utility", "avail"},
    "tank": {"tank", "reservoir", "cistern", "pond"},
    "attach": {"attach", "attached", "attachment", "cling", "adhere"},
    "practitioner": {"practitioner", "devotee", "yogin", "ascetic",
                     "votary"},
    "wind": {"wind", "air", "breeze", "gale"},
    "serve": {"serve", "worship", "worshipper", "adore", "minister"},
    "glory": {"glory", "greatness", "majesty", "splendour", "splendor",
              "grandeur"},
    "sattva": {"clarity", "clear", "goodness", "luminous", "illumination"},
    "neglect": {"neglect", "heedless", "heedlessness", "indolence",
                "indolent", "negligent", "careless"},
    "priest": {"priest", "priests", "twice-born", "brahmana", "brahmanas",
               "brahmin"},
    "mix": {"mix", "mixing", "mixture", "intermixture", "intermingle"},
    "abide": {"abide", "abides", "abiding", "dwell", "dwells", "reside"},
    "weapon": {"weapon", "weapons", "missile", "missiles", "arms",
               "armament"},
    "carry": {"bear", "bears", "bearing", "carry", "carries", "carried"},
    "understand": {"know", "knowledge", "understand", "understanding",
                   "devotion", "devoted", "wisdom", "intellect", "mind",
                   "discernment"},
    "duty": {"duty", "duties", "rite", "rites", "ritual", "caste"},
    "push": {"push", "prompt", "urge", "impel"},
    "teach": {"teach", "teaches", "instruct", "impart", "preach",
              "enlighten"},
    "shake": {"shake", "agitate", "agitated", "disturb", "perturb",
              "tremble"},
    "drive": {"drive", "station", "stationed", "place", "post", "lead",
              "guide"},
    "suit": {"suit", "suitable", "fit", "proper", "worthy", "becoming"},
    "detach": {"detach", "detached", "unattached", "disinterested"},
    "restless": {"restless", "fickle", "wavering", "unsteady"},
    "love": {"love", "affection", "fondness", "beloved", "dear"},
    "selfish": {"selfish", "self-seeking", "egoistic"},
    "freedom": {"freedom", "emancipation", "deliverance", "liberation"},
    "inertia": {"inertia", "inert", "sloth", "torpor"},
    "clarity": {"clarity", "clear", "elucidate", "lucidity", "perspicuous"},
    "truth": {"truth", "true", "real", "reality", "actual", "brahma",
              "brahman"},
    "person": {"person", "man", "men", "people", "peoples"},
    "naught": {"nothing", "naught", "nought"},
    "whole": {"everything", "whole", "entire", "total"},
    "like": {"like", "equal", "equals", "alike", "similar", "same",
             "resemble"},
    "great": {"great", "vast", "mighty", "eminent", "grand"},
    "hero": {"hero", "heroes", "chief", "chiefs", "champion", "captain",
             "leader", "leaders"},
    "warrior": {"warrior", "warriors", "fighter", "soldier", "champion"},
    "drawn": {"drawn", "arrayed", "ranked", "marshalled", "embattled"},
}

# Archaic/poetic diction -> plain equivalent (SOURCE-side expansion).
ARCHAIC = {
    "assembled": "gather", "assemble": "gather",
    "desirous": "eager", "rended": "tear", "rent": "torn",
    "resound": "echo", "resounded": "echo", "behest": "command",
    "vanquish": "defeat", "vanquished": "defeat",
    "verily": "truly", "perchance": "perhaps",
    "whence": "where", "thither": "there", "hither": "here",
    "betwixt": "between", "amongst": "among", "amidst": "amid",
    "ere": "before", "oft": "often",
    "turbid": "troubled", "dejected": "despair",
    "overcome": "overwhelm", "kinsmen": "kin",
    "lawlessness": "lawless", "unrighteousness": "lawless",
    "steadfast": "steady", "discern": "know",
    "felicity": "happy", "blissful": "happy",
    "wretched": "miserable", "sordid": "miserable",
    "eschew": "avoid", "shun": "avoid",
    "fain": "gladly", "anon": "soon",
    "mantle": "cloak", "vesture": "garment", "raiment": "garment",
    "smite": "strike", "smitten": "strike",
    "wrought": "do", "dight": "adorn",
}


def _canon(word: str) -> str:
    """Alias -> verb-family -> stem, the single normalization path."""
    word = ALIASES.get(word, word)
    word = VERBS.get(word, word)
    return stem(word)


# Precomputed stemmed sets: lookups run on stemmed lemmas, so keys
# must be stemmed too (assembled->assembl was the silent killer).
_CONCEPT_SETS = []
for _concept, _members in CONCEPTS.items():
    _CONCEPT_SETS.append({stem(_m) for _m in _members})
_ARCHAIC_MAP = {stem(_k): _canon(_v) for _k, _v in ARCHAIC.items()}


def lemmas(text: str) -> set:
    """English text -> content-lemma set."""
    text = unicodedata.normalize("NFC", text.lower())
    text = text.replace("^", "")  # Telang OCR carets (Saw^aya)
    text = re.sub(r"[^a-z ]", " ", text)
    out = set()
    for word in text.split():
        if not word or word in STOPWORDS:
            continue
        word = _canon(word)
        if word:
            out.add(word)
    return out


def expanded(lemma_set: set, glossary: dict) -> set:
    """Grow a source lemma set with glossary + concept + archaic synonyms.

    Expansion runs on the SOURCE side only: our meaning's lemmas must
    each be found (or synonym-found) in the source. Growing our side
    instead would manufacture coverage.
    """
    grown = set(lemma_set)
    for lemma in lemma_set:
        for members in _CONCEPT_SETS:
            if lemma in members:
                grown |= members
        if lemma in _ARCHAIC_MAP:
            grown.add(_ARCHAIC_MAP[lemma])
    for term, plain in glossary.items():
        t, p = stem(term.lower()), stem(plain.lower())
        if t in lemma_set:
            grown.add(p)
        if p in lemma_set:
            grown.add(t)
    return grown


def build_name_vocab(meanings: list) -> set:
    """Lowercase words ever used as ordinary vocabulary.

    A capitalized token in our meanings is a proper NAME iff its
    lowercase form never appears lowercase anywhere in the corpus.
    Data-driven: no name list to maintain, OCR-proof on our side.
    """
    vocab = set()
    for text in meanings:
        for word in re.sub(r"[^A-Za-z ]", " ", text).split():
            if word and word[0].islower():
                vocab.add(word.lower())
    return vocab


def our_lemmas(meaning: str, name_vocab: set) -> set:
    """Our meaning -> content lemmas with proper names stripped.

    Names (people/places) ride the anchor/provenance chain and the
    Sanskrit witness engine; concur guards CLAIMS (actions, relations,
    doctrine). Telang's OCR mangles names beyond lemma repair
    (Pfiwflfavas), so scoring them only manufactures flags.
    """
    kept = []
    for word in re.sub(r"[^A-Za-z ]", " ", meaning).split():
        if word and word[0].isupper() and word.lower() not in name_vocab:
            continue  # proper name
        kept.append(word)
    return lemmas(" ".join(kept))


def _lev(a: str, b: str, cap: int = 2) -> int:
    """Edit distance with early exit past cap. For OCR-fuzzy fallback."""
    if abs(len(a) - len(b)) > cap:
        return cap + 1
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        rowmin = i
        for j, cb in enumerate(b, 1):
            cost = 0 if ca == cb else 1
            val = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            cur.append(val)
            rowmin = min(rowmin, val)
        if rowmin > cap:
            return cap + 1
        prev = cur
    return prev[-1]


def coverage(ours: set, source_expanded: set) -> tuple:
    """Fraction of our lemmas covered; returns (fraction, uncovered set).

    Uncovered long lemmas get an OCR-fuzzy second chance (Telang's
    scan mangles inline words: knoAVledge): edit distance <= 2.
    """
    if not ours:
        return 1.0, set()
    covered = ours & source_expanded
    uncovered = ours - source_expanded
    still = set()
    long_src = [s for s in source_expanded if len(s) >= 7]
    for lemma in uncovered:
        if len(lemma) >= 7 and any(
                _lev(lemma, s) <= 2 for s in long_src):
            covered.add(lemma)
        else:
            still.add(lemma)
    total = len(ours)
    return (len(covered) / total, still) if total else (1.0, set())


def arnold_stanza(ch: int, span) -> str | None:
    """Span like 'B01' -> stanza text (1-based past CHAPTER header)."""
    if not span:
        return None
    m = re.fullmatch(r"[A-Z](\d+)", str(span).strip())
    if not m:
        return None
    tri = json.loads((WORK / f"triptych_ch{ch:02d}.json").read_text(
        encoding="utf-8"))
    stanzas = [s.strip() for s in tri["arnold"].split("\n\n") if s.strip()]
    idx = int(m.group(1))
    if idx >= len(stanzas):
        return None
    text = stanzas[idx]
    # Drop speaker labels (Sanjaya./Krishna./Arjuna.) — they are
    # metadata, and our meanings name speakers separately.
    text = re.sub(r"^(Sanjaya|Krishna|Arjuna|Dhritarashtra)\.\s*", "", text)
    return text


HI = 0.50  # coverage floor for "this source supports our meaning"
LO = 0.35  # below this, the source does not support it

# Flags are a read-through queue, not failures: concur always exits 0
# when the report writes cleanly. Discrimination validated 2026-09-28:
# true pairings outscore shuffled 3.0x (Telang) / 2.6x (Arnold).


def concur_verse(meaning: str, telang_ctx: str, arnold_text,
                 glossary: dict, name_vocab: set) -> dict:
    ours = our_lemmas(meaning, name_vocab)
    if len(ours) < 3:
        # Name-list verses (catalogues of warriors): nothing but
        # names to score. The anchor chain + Sanskrit engine carry them.
        return {"status": "NAME-LIST", "cov_telang": None,
                "cov_arnold": None, "uncovered": [],
                "detail": "meaning is names only; carried by anchors"}
    cov_t, unc_t = coverage(ours, expanded(lemmas(telang_ctx), glossary))
    if arnold_text is None:
        return {"status": "ARNOLD-ABSENT", "cov_telang": round(cov_t, 2),
                "cov_arnold": None, "uncovered": sorted(unc_t),
                "detail": "Arnold omits this verse; Telang-only by construction"}
    cov_a, unc_a = coverage(ours, expanded(lemmas(arnold_text), glossary))
    rec = {"status": "", "cov_telang": round(cov_t, 2),
           "cov_arnold": round(cov_a, 2)}
    if cov_t >= HI and cov_a >= HI:
        rec["status"] = "CONSENSUS"
        rec["detail"] = "both sources cover the meaning"
        rec["uncovered"] = []
    elif cov_t >= HI:
        rec["status"] = "SINGLE-SOURCE"
        rec["detail"] = (f"Telang covers ({cov_t:.2f}), "
                         f"Arnold does not ({cov_a:.2f})")
        rec["uncovered"] = sorted(unc_a)
    elif cov_a >= HI:
        rec["status"] = "SINGLE-SOURCE"
        rec["detail"] = (f"Arnold covers ({cov_a:.2f}), "
                         f"Telang does not ({cov_t:.2f})")
        rec["uncovered"] = sorted(unc_t)
    else:
        rec["status"] = "ORPHAN"
        rec["detail"] = (f"neither source covers "
                         f"(telang {cov_t:.2f}, arnold {cov_a:.2f})")
        rec["uncovered"] = sorted(unc_t & unc_a)
    return rec


def cmd_concur(args) -> int:
    """Run two-source consensus over draft meanings -> concur_report.json."""
    from drafting import load_glossary  # local import: same pattern as verify
    chapters = [args.chapter] if args.chapter else list(range(1, 19))
    glossary = load_glossary()
    meanings = []
    docs = {}
    for ch in chapters:
        doc = json.loads((WORK / f"draft_ch{ch:02d}.json").read_text(
            encoding="utf-8"))
        docs[ch] = doc
        meanings += [r["meaning_simple"] for r in doc["records"]
                     if r["status"] == "approved"]
    name_vocab = build_name_vocab(meanings)
    report = {"verses": {}, "summary": {}}
    counts: dict = {}
    for ch in chapters:
        for r in docs[ch]["records"]:
            if r["status"] != "approved":
                continue
            rec = concur_verse(r["meaning_simple"], r["telang_context"],
                               arnold_stanza(ch, r["arnold_span"]),
                               glossary, name_vocab)
            rec["meaning"] = r["meaning_simple"]
            report["verses"][r["id"]] = rec
            counts[rec["status"]] = counts.get(rec["status"], 0) + 1
    report["summary"] = counts
    (WORK / "concur_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    flags = sum(n for s, n in counts.items() if s != "CONSENSUS")
    print(f"concur: {counts} flags={flags} "
          f"-> data/content/work/concur_report.json")
    return 0
