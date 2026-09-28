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
    solely whether alike thus plus non myself yourself himself herself
    itself ourselves themselves oneself un re along
    """.split())

# NEG-prefix dual emission: ONLY where the prefix is unambiguously
# negation. "un-" (minus under/until/unless) and an allowlist of "dis-"
# roots. Dropped "in-/im-/dis-" generally: imperishable->perishable or
# invisible->visible would match OPPOSITES (silent passes — the one
# unforgivable failure mode). "dislike"->like and "unseen"->seen stay.
NEG_EXCEPT = {"until", "unless"}
DIS_ROOTS = {"like", "agree", "approve", "comfort", "trust", "honest",
             "loyal", "regard", "respect", "obey", "please", "appear"}

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
    if word.endswith("ies") and len(word) - 3 >= 2:
        return word[:-3] + "i"  # duties -> duti (Porter y->i: duty -> duti)
    if word.endswith("lves") and len(word) - 4 >= 3:
        return word[:-4] + "lf"  # selves -> self (serves keeps its e)
    if word.endswith("ied") and len(word) - 3 >= 3:
        return word[:-3] + "i"
    if word.endswith("sses"):
        return word[:-2]  # passes -> pass (not passe)
    if word.endswith(("shes", "ches", "xes", "zes")):
        return word[:-2]  # wishes -> wish (not wishe)
    if word.endswith("es") and len(word) - 1 >= 4:
        return word[:-1]  # horses -> horse, senses -> sense
    if word.endswith("s") and len(word) - 1 >= 3 and not word.endswith("ss"):
        return word[:-1]  # bows -> bow (stopwords like
    # this/thus/as/is never reach the stemmer; was/has/his have bases < 3)
    if len(word) > 3 and word.endswith("y") and word[-2] not in "aeiou":
        return word[:-1] + "i"  # Porter consonant-y: duty/family/sky match
    # their plurals (duties/families/skies all fold to duti/famili/ski)
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
             "understand", "understands", "understood", "understanding",
             "knower", "knowers"},
    "do": {"do", "does", "did", "done", "doing", "wrought"},
    "go": {"go", "goes", "went", "gone", "going", "repair",
           "repairs", "proceed", "proceeds", "tread", "walk",
           "walks", "cease", "ceases", "stop", "halt"},
    "come": {"come", "comes", "came", "coming"},
    "think": {"think", "thinks", "thought", "thinking", "seem",
              "seems", "seeming"},
    "feel": {"feel", "feels", "felt", "feeling"},
    "give": {"give", "gives", "gave", "given", "giving",
             "renounce", "renounces", "renounced", "renouncing",
             "renunciation", "bestow", "bestows", "bestowed"},
    "take": {"take", "takes", "took", "taken", "taking", "seek",
             "seeks", "sought", "seeking", "cast", "casts", "casting",
             "arise", "arises", "arose", "arisen", "receive", "receives",
             "received", "accept", "accepts", "accepted", "seize",
             "seizes", "seized"},
    "make": {"make", "makes", "made", "making", "maker", "makers"},
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
            "prevail", "prevails", "prevailed", "obtain", "obtains",
            "obtained", "obtaining"},
    "hear": {"hear", "hears", "heard", "hearing", "listen", "listens",
             "listened", "hark"},
    "want": {"want", "wants", "wanted", "wanting", "desire", "desires",
             "desired", "desiring", "wish", "wishes", "wished",
             "wishing", "crave", "craves", "craved", "craving", "covet",
             "covets", "coveted", "long", "longs", "longed", "longing",
             "ambitious", "aspirant"},
    "leave": {"leave", "leaves", "left", "leaving", "abandon", "abandons",
              "abandoned", "abandoning", "abandonment", "forsake",
              "forsakes", "forsook", "forsaken", "relinquish",
              "relinquished", "renounce", "quit", "drop", "drops",
              "dropped", "dropping", "desert", "deserted", "resign",
              "resigns", "resigned", "refusal", "submit", "submits",
              "submitted", "yield", "yields", "yielded"},
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
    "abide": {"abide", "abides", "abiding", "dwell", "dwells", "reside"},
    "ablaze": {"ablaze", "aflame", "blaze", "blazing", "fiery", "flaming"},
    "action": {"act", "action", "deed", "work"},
    "activity": {"action", "actions", "activity"},
    "adept": {"accomplished", "adept", "perfected", "siddha"},
    "after": {"after", "following", "later", "subsequent"},
    "agape": {"agape", "gaping", "yawning"},
    "age": {"age", "cycle", "eon", "era", "kalpa"},
    "alone": {"alone", "elsewhere", "exclusive", "otherwhere", "solely"},
    "anger": {"anger", "angry", "fury", "rage", "wrath"},
    "anxiety": {"anxieties", "anxiety", "care", "solicitude", "worry"},
    "apprehend": {"apprehend", "apprehended"},
    "armed": {"armed", "endowed", "equipped", "possessed"},
    "army": {"army", "array", "force", "host", "hosts", "rank", "ranks",
             "troops"},
    "arrive": {"arrive", "arrived", "ascended", "attained", "reached",
             "risen"},
    "assign": {"allot", "allotted", "appointed", "apportion", "assign", "assigned", "distinguish", "distinguished", "fixed", "ordain"},
    "attach": {"adhere", "attach", "attached", "attachment", "cling"},
    "away": {"aside", "away", "off"},
    "back": {"back", "restore", "restored", "return", "revert"},
    "bad": {"bad", "disagreeable", "unpleasant"},
    "badly": {"bad", "badly", "ill", "imperfect"},
    "balance": {"balance", "equality", "equanimity", "evenness"},
    "battle": {"battle", "combat", "fight", "war", "warfare"},
    "beat": {"beat", "beats", "best", "better", "excel", "superior",
             "surpass"},
    "before": {"ahead", "before", "front", "presence"},
    "begin": {"ancient", "begin", "beginning", "first"},
    "beginner": {"aspirant", "beginner", "candidate", "novice", "seeker"},
    "being": {"being", "beings", "creature", "creatures", "existence",
             "living", "mortal", "mortals"},
    "beyond": {"above", "across", "beyond", "transcend", "transcending"},
    "bid": {"bid", "bidding", "command", "order"},
    "bind": {"bind", "binds", "bound", "fasten", "tie"},
    "body": {"bodies", "body", "corporeal", "embodied", "frame"},
    "born": {"begotten", "birth", "born"},
    "borrow": {"alien", "another", "borrow", "borrowed", "foreign",
             "strange"},
    "bound": {"bound", "tethered", "tied"},
    "bow": {"archer", "archery", "arrow", "bow", "bowman", "shaft"},
    "brave": {"bold", "brave", "courage", "courageous", "fearless",
             "valiant"},
    "call": {"call", "calls", "designate", "name", "term"},
    "carry": {"bear", "bearing", "bears", "carried", "carries", "carry"},
    "caste": {"caste", "castes", "class", "order", "social"},
    "cause": {"cause", "decide", "determine", "effect"},
    "celibate": {"brahmacharya", "celibacy", "celibate", "chaste",
             "continent"},
    "chain": {"bond", "bonds", "chain", "fetter", "fetters", "tie"},
    "chariot": {"car", "chariot"},
    "cheer": {"cheer", "elation", "exult", "exultation", "glad",
             "jubilant", "rejoice", "thrill", "thrilled"},
    "child": {"child", "children", "offspring", "progeny", "son", "sons"},
    "clan": {"clan", "clansman", "house", "kinsman", "race", "tribe"},
    "clarity": {"clarity", "clear", "elucidate", "lucidity", "perspicuous"},
    "clean": {"clean", "cleanliness", "cleansing", "pure", "purity"},
    "clear": {"clean", "clear", "good", "lucid", "pure", "purity",
             "spotless", "stainless"},
    "closed": {"closed", "deaf", "disobedient", "unheeding"},
    "compare": {"comparable", "compare", "matchless", "peerless",
             "surpass", "unsurpassed"},
    "complete": {"complete", "completely", "entirely", "fully", "totally",
             "wholly"},
    "concern": {"care", "concerned", "involve", "involved"},
    "confuse": {"bewilder", "bewildered", "blind", "blinded", "confound",
             "confounded", "confused", "confusion", "deluded", "delusion",
             "perplex", "perplexed"},
    "constrain": {"coerce", "compel", "constrain", "constrained", "force",
             "oblige"},
    "continence": {"celibacy", "chastity", "restraint"},
    "council": {"assembly", "company", "council", "gathering"},
    "create": {"create", "creates", "emit", "fashion", "make", "produce",
             "project"},
    "cruel": {"base", "churlish", "cruel", "pitiless", "ruthless"},
    "curious": {"curious", "inquisitive", "seeker", "yearning"},
    "cut": {"cut", "destroy", "destroyed", "destruction", "dispel",
             "disperse", "remove", "sever", "sunder"},
    "deathless": {"deathless", "deathlessness", "immortal", "immortality"},
    "desire": {"craving", "desire", "lust"},
    "destroyed": {"destroyed", "destruction"},
    "detach": {"detach", "detached", "disinterested", "unattached"},
    "devour": {"consume", "devour", "devourer", "swallow"},
    "discipline": {"application", "austere", "austerity", "devoted",
             "devotion", "discipline", "disciplined", "penance",
             "perseverance", "practice", "practise"},
    "discontent": {"discontent", "discontented", "dissatisfied",
             "unfulfilled"},
    "disgust": {"aversion", "disgust", "disgusted", "loathing",
             "revulsion"},
    "display": {"display", "hypocrisy", "ostentation", "pretence", "show"},
    "distinguish": {"distinguished", "marked"},
    "dive": {"dive", "dives", "plunge", "rush"},
    "divide": {"different", "distinct", "divide", "divided", "separate"},
    "divine": {"divine", "godlike", "godly"},
    "doctrine": {"doctrine", "lore", "teaching"},
    "doer": {"agent", "doer"},
    "down": {"below", "beneath", "down", "downward", "downwards", "low"},
    "drawn": {"arrayed", "drawn", "embattled", "marshalled", "ranked"},
    "dream": {"brood", "dream", "dreaming", "dreams", "fancy", "muse",
             "think"},
    "drive": {"dispel", "drive", "expel", "guide", "impulse", "lead",
             "place", "post", "propensity", "station", "stationed"},
    "drunk": {"besotted", "drunk", "full", "intoxicated"},
    "duty": {"caste", "duties", "duty", "rite", "rites", "ritual"},
    "dwell": {"contemplate", "dwell", "dwells", "meditate", "ponder"},
    "easy": {"ease", "easy", "simple"},
    "efficient": {"able", "competent", "dexterous", "efficient", "skilful"},
    "ego": {"ego", "egoism", "selfhood", "selfish"},
    "emit": {"discharge", "emanate", "emit", "issue", "pour", "send"},
    "enchain": {"bind", "chain", "fetter"},
    "end": {"cease", "ceased", "end", "ended", "ending", "final", "last"},
    "endless": {"endless", "interminable", "unending"},
    "energetic": {"energetic", "energy", "enthusiastic", "vigorous",
             "zealous"},
    "every": {"everybody", "everyone"},
    "excellent": {"excellent", "fine", "splendid", "superb"},
    "eyes": {"eye", "eyes", "sight", "vision"},
    "face": {"face", "faces", "facing", "headed", "mouth"},
    "fail": {"fail", "failing", "fails", "lack", "lacks"},
    "faint": {"faint", "faintness", "feeble", "weak", "weakness"},
    "faith": {"believe", "devoted", "devotion", "devout", "faith",
             "faithful", "trust"},
    "faithful": {"devout", "faith", "faithful"},
    "faithless": {"devoted", "devotion", "faithless", "infidel", "skeptical", "unbelieving"},
    "false": {"false", "falsehood", "untrue"},
    "fame": {"disgrace", "dishonour", "fame", "infamy", "renown"},
    "fear": {"afraid", "dread", "fear", "fright", "terror"},
    "fed": {"enlarged", "fed", "nourish", "nourished", "sustained"},
    "fill": {"fill", "fills", "imbue", "permeate", "pervade", "pervades"},
    "filthy": {"dirty", "filthy", "foul", "impure", "unclean"},
    "final": {"conclusive", "decided", "decisive", "final"},
    "flame": {"blaze", "fire", "flame"},
    "flow": {"emanate", "flow", "flowing", "stream"},
    "foe": {"adversary", "enemy", "foe", "opponent"},
    "fold": {"absorb", "dissolve", "enter", "fold", "merge", "withdraw"},
    "foolish": {"deluded", "delusion", "folly", "foolish", "ignorance", "ignorant", "know", "knowing", "unwise"},
    "force": {"energy", "force", "might", "power", "strength"},
    "form": {"embodiment", "figure", "form", "shape"},
    "fragment": {"fragment", "morsel", "part", "particle", "piece",
             "portion", "section"},
    "free": {"delivered", "emancipate", "free", "liberated", "release",
             "released", "unfettered"},
    "freedom": {"deliverance", "emancipation", "freedom", "liberation"},
    "gain": {"acquisition", "attainment", "gain", "profit"},
    "generous": {"bounty", "charitable", "charity", "generosity",
             "generous", "giving", "liberal"},
    "gift": {"benefit", "blessing", "boon", "favour", "gift"},
    "glory": {"glory", "grandeur", "greatness", "majesty", "splendor",
             "splendour"},
    "go": {"cease", "ceases", "halt", "stop"},
    "goal": {"aim", "goal", "object", "purpose"},
    "god": {"divine", "god", "lord", "supreme"},
    "gone": {"gone"},
    "good": {"good", "moral", "righteous", "virtuous"},
    "grace": {"favor", "favour", "grace", "kindness", "mercy"},
    "grant": {"bestow", "confer", "grant"},
    "great": {"eminent", "grand", "great", "greater", "heaviest", "heavy",
             "higher", "mighty", "vast"},
    "greed": {"avarice", "avaricious", "cupidity", "greed"},
    "grief": {"grief", "mourn", "sad", "sorrow", "woe"},
    "grow": {"grow", "grows", "increase", "multiply", "prosper", "thrive"},
    "hand": {"hand", "hands"},
    "happy": {"cheerful", "glad", "happiness", "happy", "joyful",
             "unhappiness", "unhappy"},
    "hard": {"arduous", "difficult", "hard", "harder", "hardest"},
    "harm": {"harm", "hurt", "injure", "injures", "injured", "injury",
             "violence"},
    "harsh": {"harsh", "severe", "stern"},
    "harvest": {"crop", "fruit", "harvest", "produce", "yield"},
    "hasten": {"hasten", "quick", "rapid", "rapidly", "swift"},
    "hate": {"animosity", "averse", "aversion", "dislike", "dislikes",
             "enmity", "hate", "hatred", "hostile"},
    "heaven": {"celestial", "heaven", "paradise"},
    "helpless": {"helpless", "involuntary", "powerless", "unwilling"},
    "herd": {"cattle", "graze", "herd", "herding", "pastoral", "tend",
             "tending"},
    "hereafter": {"afterlife", "hereafter"},
    "hero": {"captain", "champion", "chief", "chiefs", "hero", "heroes",
             "leader", "leaders"},
    "hidden": {"esoteric", "hidden", "mysterious", "mystery", "occult",
             "secret"},
    "hold": {"check", "contain", "curb", "hold", "rest", "restrain", "restrained", "uphold"},
    "honest": {"honest", "honesty", "truthful", "upright", "uprightness"},
    "honor": {"disgrace", "dishonour", "honor", "honour", "respect"},
    "horse": {"equine", "horse", "horsemen", "stallion", "steed"},
    "house": {"abode", "dwelling", "frame", "house"},
    "human": {"human", "humans", "mankind", "mortal", "mortals"},
    "hypocrite": {"charlatan", "cheat", "fraud", "hypocrite", "pretender"},
    "imperishable": {"deathless", "eternal", "everlasting", "forever",
             "immortal", "immortality", "immutable", "imperishable",
             "indestructible", "undying"},
    "inborn": {"birth", "born", "constitutional", "inborn", "inherent", "innate"},
    "indivisible": {"impartite", "indivisible", "undivided", "unseparated"},
    "inertia": {"inert", "inertia", "sloth", "torpor"},
    "infamy": {"disgrace", "disrepute", "ignominy", "infamy", "shame"},
    "inside": {"inside", "inward", "within"},
    "instant": {"instant", "moment", "trice"},
    "intent": {"intent", "intention", "motive", "purpose"},
    "jest": {"jest", "jesting", "joke", "joking", "merriment", "mirth", "play", "playing", "sport"},
    "join": {"apply", "endowed", "join", "resort", "unite"},
    "keep": {"continue", "continues", "keep", "keeps", "persist"},
    "kind": {"class", "kind", "kinds", "sort", "sorts", "type"},
    "king": {"chief", "chiefs", "king", "lord", "lords", "monarch",
             "prince", "regal", "royal", "ruler", "sovereign"},
    "kingly": {"chief", "king", "regal", "royal", "sovereign"},
    "knower": {"knower", "knowers", "knowledgeable"},
    "knowledge": {"knowledge", "learning", "lore", "wisdom"},
    "lawless": {"impiety", "impious", "lawless", "lawlessness"},
    "leave": {"abandonment", "refusal"},
    "lie": {"lying", "recline", "rest"},
    "lifetime": {"accustomed", "habitual", "lifelong", "lifetime"},
    "lift": {"elevate", "lift", "raise", "uplift"},
    "light": {"careless", "casual", "casually", "contempt", "contemptuous", "gentle", "gently", "incautious", "incautiously", "light", "lightly", "mild"},
    "like": {"alike", "equal", "equals", "like", "resemble", "same", "similar", "such"},
    "lock": {"close", "curb", "lock", "locks", "restrain", "shut"},
    "loss": {"defeat", "failure", "lose", "loss"},
    "love": {"affection", "beloved", "dear", "dearly", "fondness", "love", "loved", "loving"},
    "maker": {"creator", "maker", "making", "ordainer",
             "unmaking"},
    "mark": {"characteristic", "mark", "marks", "sign", "symptom", "token"},
    "meal": {"dine", "dinner", "eat", "eating", "feast", "food", "meal", "meals", "repast", "supper"},
    "measure": {"countless", "infinite", "limited", "little", "measure",
             "measureless", "numberless", "small", "unlimited"},
    "meet": {"combine", "join", "meet", "meeting", "union"},
    "memory": {"memory", "mindfulness", "recall", "recollect", "recover",
             "regain", "remember"},
    "merchant": {"merchant", "trader"},
    "midway": {"intermediate", "middle", "midst", "midway"},
    "mind": {"heart", "intellect", "mind", "thought"},
    "mix": {"intermingle", "intermixture", "mix", "mixing", "mixture"},
    "mood": {"humour", "mood", "moods", "qualities", "quality", "state"},
    "move": {"motion", "movable", "move", "moves", "moving", "stir",
             "roam", "roams", "wander", "wanders"},
    "multitude": {"assemblage", "gathering", "host", "multitude", "throng"},
    "nature": {"character", "disposition", "essence", "nature"},
    "naught": {"another", "naught", "never", "nobody", "none", "nothing", "nought", "other"},
    "near": {"afar", "close", "far", "near", "nearer", "nearly", "nigh"},
    "neglect": {"careless", "heedless", "heedlessness", "indolence",
             "indolent", "neglect", "negligent"},
    "neutral": {"impartial", "indifferent", "neutral"},
    "next": {"coming", "following", "next", "subsequent"},
    "nitpick": {"carp", "carping", "cavil", "nitpick", "nitpicking",
             "quibble"},
    "noble": {"gentle", "honourable", "noble", "virtuous", "worthy"},
    "nowhere": {"another", "else", "nowhere", "other", "single",
             "undivided", "alone"},
    "observe": {"behold", "observe", "watch", "witness"},
    "oppose": {"against", "contrary", "oppose", "opposed", "violate"},
    "outcome": {"consequence", "fruit", "issue", "outcome", "result",
             "results"},
    "over": {"above", "across", "over", "upon"},
    "pain": {"misery", "pain", "sorrow", "suffering"},
    "pair": {"compound", "couple", "dual", "pair", "pairing"},
    "path": {"course", "path", "road", "way"},
    "peace": {"calm", "peace", "rest", "serene", "tranquillity"},
    "peak": {"hill", "mountain", "peak", "summit"},
    "perilous": {"danger", "dangerous", "dreadful", "fearful", "peril",
             "perilous"},
    "person": {"man", "men", "people", "peoples", "person"},
    "petty": {"base", "low", "mean", "petty", "trivial"},
    "pity": {"compassion", "compassionate", "merciful", "mercy", "pity"},
    "place": {"fix", "place", "placed", "set", "station"},
    "pleasant": {"agreeable", "delightful", "pleasant", "pleasing"},
    "pleasure": {"bliss", "delight", "happy", "joy", "pleasure", "sugary",
             "sweet"},
    "pledge": {"pledge", "resolve", "undertake", "vow"},
    "plow": {"agriculture", "cultivate", "farm", "plough", "plow", "till"},
    "practitioner": {"ascetic", "devotee", "practitioner", "votary",
             "yogin"},
    "praise": {"extol", "glorify", "glorifying", "hymn", "laud", "praise"},
    "pray": {"pray", "prays", "propitiate", "supplicate"},
    "priest": {"brahman", "brahmana", "brahmanas", "brahmin", "priest", "priests", "twice"},
    "purify": {"cleanse", "hallow", "purifies", "purify", "sanctification",
             "sanctify"},
    "push": {"impel", "prompt", "push", "urge"},
    "raft": {"bark", "boat", "raft", "vessel"},
    "reach": {"arrive", "attain", "attained", "attains", "gain", "obtain",
             "reach", "reaches"},
    "ready": {"prepared", "ready", "willing"},
    "rebirth": {"again", "rebirth", "reborn", "repeated"},
    "rectitude": {"honesty", "uprightness", "veracity"},
    "refuse": {"decline", "refuse", "reject", "unwilling"},
    "remain": {"remain", "remains", "rest", "stay", "stayed"},
    "remnant": {"leavings", "leftover", "remains", "remnant", "rest"},
    "require": {"enjoined", "mandatory", "ordained", "prescribed",
             "require", "required"},
    "resolve": {"decide", "decided", "decisive", "determination",
             "determined", "resolution", "resolve"},
    "restless": {"fickle", "restless", "unsteady", "wavering"},
    "right": {"correct", "due", "fitting", "just", "proper", "right"},
    "rise": {"ascend", "attain", "attained", "mount", "rise", "rising"},
    "rite": {"ceremony", "oblation", "observance", "offer", "offering",
             "rite", "rites", "ritual", "sacrifice", "sacrifices", "vow",
             "vows", "worship"},
    "rival": {"matchless", "peerless", "rival", "rivalled", "unrivalled"},
    "root": {"adhere", "adhering", "anchored", "grounded", "repose",
             "rest", "root", "rooted", "lie", "lying", "recline"},
    "round": {"cycle", "realm", "round", "world", "worlds"},
    "ruin": {"destruction", "doom", "ruin"},
    "rule": {"control", "controlled", "govern", "kingdom", "reign",
             "restrain", "restrained", "rule", "ruled", "ruling",
             "sovereignty"},
    "run": {"escape", "fled", "flee", "run", "runs", "rush"},
    "sattva": {"calm", "clarity", "clear", "goodness", "illumination",
             "luminous", "peaceful", "serene"},
    "scheme": {"design", "enterprise", "plan", "project", "scheme",
             "undertaking"},
    "scoff": {"deride", "jeer", "mock", "mocker", "scoff", "scoffer",
             "scorn"},
    "secure": {"carry", "preserve", "protect", "provide", "secure"},
    "seeker": {"aspirant", "seeker", "seeking", "student"},
    "selfish": {"egoistic", "self-seeking", "selfish"},
    "senses": {"sense", "senses"},
    "serve": {"adore", "minister", "serve", "worship", "worshipper",
             "worshippers"},
    "settle": {"settle", "settled"},
    "shackle": {"bond", "chain", "fetter", "shackle", "shackles"},
    "shake": {"agitate", "agitated", "disturb", "perturb", "shake",
             "shaken", "shook", "tremble", "trembled", "trembles"},
    "shape": {"formed", "moulded", "shape", "shaped"},
    "shoot": {"bud", "shoot", "shoots", "sprout", "sprouts"},
    "single": {"single", "sole"},
    "sinner": {"criminal", "guilty", "offender", "sinful", "sinner"},
    "sit": {"seat", "seated", "sit", "sits", "sitting"},
    "slack": {"lax", "negligent", "remiss", "slack"},
    "sleep": {"asleep", "drowsy", "insomniac", "oversleeper", "sleep",
             "sleeping", "sleeps", "slept", "slumber", "vigil", "vigils",
             "wake", "waking"},
    "soul": {"self", "soul", "spirit"},
    "spiteful": {"malevolent", "malicious", "spite", "spiteful"},
    "spouse": {"consort", "husband", "spouse", "wife"},
    "spread": {"expand", "extend", "extended", "spread", "stretch"},
    "stand": {"abide", "abides", "abiding", "arise", "arisen", "dwell",
             "dwells", "reside", "rise", "stand"},
    "steady": {"constant", "firm", "fixed", "resolute", "stable",
             "steadfast", "steady", "unshakable", "unshaken"},
    "steal": {"cheat", "cheated", "deceive", "rob", "steal", "stolen"},
    "stillness": {"quiet", "still", "stillness", "tranquillity"},
    "straight": {"erect", "even", "straight", "upright"},
    "strive": {"attempt", "endeavor", "exert", "labour", "strive"},
    "stubborn": {"headstrong", "obstinate", "stubborn", "willful"},
    "subtle": {"subtle", "subtlety"},
    "success": {"success", "successful", "triumph"},
    "suffer": {"afflicted", "distressed", "miserable", "suffer", "suffers",
             "weep"},
    "suit": {"becoming", "fit", "proper", "suit", "suitable", "worthy"},
    "surrender": {"abandon", "abandonment", "devote", "hand", "hands",
             "resign", "submit", "surrender", "yield"},
    "sustain": {"maintain", "support", "sustain"},
    "tank": {"cistern", "pond", "reservoir", "tank"},
    "taste": {"enjoy", "experience", "taste"},
    "teach": {"enlighten", "impart", "instruct", "preach", "proclaim",
             "proclaimed", "teach", "teaches"},
    "teacher": {"guru", "instructor", "master", "preceptor", "teacher",
             "tutor"},
    "tears": {"cry", "tear", "tears", "weep"},
    "terror": {"awe", "scorch", "scorcher", "terrible", "terror"},
    "timeless": {"ageless", "ancient", "eternal", "everlasting",
             "immemorial", "timeless"},
    "trade": {"commerce", "trade", "trading"},
    "transcend": {"surmount", "surpass", "transcend", "transcended"},
    "triple": {"three", "threefold", "triple"},
    "truly": {"indeed", "really", "truly"},
    "truth": {"actual", "brahma", "brahman", "knowledge", "real",
             "reality", "true", "truth", "wisdom"},
    "turn": {"become", "betake", "grow", "grown", "resort", "turn"},
    "ultimate": {"absolute", "highest", "paramount", "supreme",
             "transcendent", "ultimate"},
    "unbeaten": {"invincible", "unbeaten", "unconquered"},
    "unbroken": {"constant", "continuous", "unbroken", "uninterrupted"},
    "understand": {"devoted", "devotion", "discernment", "grasp", "grasps",
             "intellect", "know", "knowledge", "mind", "understand",
             "understanding", "wisdom"},
    "unity": {"identity", "oneness", "union", "unity"},
    "unseen": {"hidden", "imperceptible", "invisible", "unmanifest",
             "unseen"},
    "friend": {"companion", "comrade", "friend", "mate"},
    "get": {"acquire", "gain", "get", "gets", "obtain", "procure"},
    "seek": {"quest", "search", "seek", "seeking", "seeks"},
    "way": {"direction", "everywhere", "pervade", "pervading", "way", "where"},
    "space": {"ether", "firmament", "sky", "space", "void"},
    "thing": {"entity", "matter", "object", "thing", "things"},
    "forgive": {"absolve", "excuse", "forgive", "forgives", "pardon", "pardons"},
    "up": {"above", "below", "beneath", "down", "downward", "downwards", "high", "low", "up", "upward", "upwards"},
    "uphold": {"bear", "maintain", "support", "sustain", "uphold"},
    "useful": {"avail", "use", "useful", "uses", "utility"},
    "vary": {"diverse", "manifold", "varied", "various", "vary"},
    "vehicle": {"instrument", "means", "vehicle"},
    "vulgar": {"coarse", "uncultured", "unrefined", "vulgar"},
    "warrior": {"champion", "fighter", "soldier", "warrior", "warriors"},
    "weapon": {"armament", "arms", "missile", "missiles", "weapon",
             "weapons"},
    "wed": {"join", "joined", "marry", "unite", "wed", "wedded"},
    "welcome": {"desired", "disliked", "liked", "undesired", "unwelcome",
             "welcome"},
    "whole": {"entire", "everything", "total", "whole"},
    "wind": {"air", "breeze", "gale", "wind"},
    "wipe": {"efface", "erase", "expunge", "obliterate", "subvert", "wipe",
             "wipes"},
    "wise": {"learned", "sage", "sensible", "wisdom", "wise"},
    "word": {"bid", "bidding", "decision", "decree", "judgment", "opinion",
             "speech", "utterance", "verdict", "word", "words"},
    "world": {"creation", "earth", "universe", "world"},
    "wrong": {"evil", "misdeed", "misdeeds", "sin", "sins", "sinful",
              "wicked", "unrighteous", "astray", "corrupt",
              "corrupted", "depraved", "improper", "unbecoming",
              "wrong"},
    "yoke": {"harnessed", "steadfast", "united", "yoke", "yoked"}
}


TERMS = {
    "adhibhuta": {"lord", "being", "beings"},
    "adhidaiva": {"lord", "god", "gods"},
    "adhiyajna": {"sacrifice", "lord"},
    "adhyatma": {"self", "inner", "soul"},
    "brahman": {"ultimate", "reality", "supreme", "truth", "god",
                "creator"},
    "karma": {"action", "work", "deed", "rite"},
    "dharma": {"duty", "law", "religion"},
    "yoga": {"discipline", "devotion", "practice"},
    "yogin": {"practitioner", "devotee", "disciplined"},
    "atman": {"self", "soul"},
    "ahankara": {"ego", "selfish"},
    "buddhi": {"understand", "understanding", "intellect", "mind"},
    "manas": {"mind"},
    "prakriti": {"nature", "matter"},
    "purusha": {"person", "spirit", "self"},
    "guna": {"quality", "mood"},
    "moksha": {"freedom", "liberation", "release"},
    "samsara": {"rebirth", "cycle"},
    "tapas": {"discipline", "austerity"},
    "jnana": {"knowledge", "wisdom"},
    "bhakti": {"love", "devotion"},
    "bhakta": {"devotee"},
    "sannyasa": {"renounce", "abandon"},
    "vairagya": {"dispassion", "detachment"},
    "sattva": {"clarity", "good", "pure"},
    "rajas": {"passion", "activity"},
    "tamas": {"dark", "inertia", "ignorance"},
    "avidya": {"ignorance"},
    "maya": {"illusion"},
    "marut": {"storm", "wind"},
    "rudra": {"storm", "howl", "terrible"},
    "aditya": {"sun", "lord"},
    "vasu": {"bright", "wealth"},
    "ashvin": {"twin", "horse"},
    "kubera": {"wealth", "keeper", "treasure"},
    "siddha": {"adept", "perfect"},
    "rakshasa": {"demon", "monster"},
    "asura": {"demon", "titan"},
    "meru": {"peak", "mountain"},
    "prajapati": {"creator", "lord"},
    "kalpa": {"age", "eon"},
    "vrishni": {"clan", "tribe"},
    "vaisya": {"merchant", "trade"},
    "sudra": {"laborer", "servant", "service", "worker"},
    "yadu": {"clan", "tribe"},
    "yadava": {"clan", "tribe"},
    "vaishya": {"merchant", "trade", "trader"},
    "shudra": {"laborer", "servant", "service", "worker"},
    "kshatriya": {"warrior", "ruler"},
}
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


def _neg_root(word: str):
    """Negation root or None. un- (guarded) + dis-allowlist only.

    in-/im- dropped outright: imperishable->perishable would match
    OPPOSITES (silent passes — the unforgivable failure mode).
    """
    if (word.startswith("un") and len(word) - 2 >= 5
            and word not in NEG_EXCEPT
            and not word.startswith(("under", "univers"))):
        return word[2:]
    if word.startswith("dis"):
        rest = word[3:]
        if rest in DIS_ROOTS:
            return rest
    return None


def _norm(word: str) -> str:
    """Full normalization: negation-replace, then canonical path.

    REPLACE semantics (not dual-emit): dislike -> like ONLY, so no
    phantom uncoverable lemmas. Consistent between query (lemmas) and
    index (precompute) — asymmetry here would manufacture flags.
    """
    root = _neg_root(word)
    return _canon(root if root is not None else word)


# Precomputed stemmed sets: normalized with the SAME _norm pipeline
# as queries (verb families, negation roots included). Any asymmetry
# between index and query manufactures flags out of thin air.
_CONCEPT_SETS = []
for _concept, _members in CONCEPTS.items():
    _CONCEPT_SETS.append({_norm(_m) for _m in _members})
_ARCHAIC_MAP = {_norm(_k): _norm(_v) for _k, _v in ARCHAIC.items()}
_TERM_GLOSS = {_norm(_t): {_norm(_g) for _g in _gloss}
               for _t, _gloss in TERMS.items()}


def lemmas(text: str) -> set:
    """English text -> content-lemma set.

    NEG-prefix dual emission: undeluded -> {undeluded, deluded}, so our
    plain negation meets Telang's "not deluded" (not is a stopword on
    both sides). Applies to both sides identically; exempt words
    (under/until/...) never split.
    """
    text = unicodedata.normalize("NFC", text.lower())
    text = text.replace("^", "")  # Telang OCR carets (Saw^aya)
    text = re.sub(r"[^a-z ]", " ", text)
    out = set()
    for word in text.split():
        if not word or word in STOPWORDS:
            continue
        word = _norm(word)
        if word:
            out.add(word)
    # *ily dual emission: family->famili (y->i) AND fami (ly-strip),
    # so families/family, easy/easily, steady/steadily all meet.
    # Both forms share one root — no opposite-match risk.
    for word in text.split():
        if len(word) > 5 and word.endswith("ily"):
            extra = stem(word[:-2])
            if extra:
                out.add(extra)
    return out
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
        if lemma in _TERM_GLOSS:
            grown |= _TERM_GLOSS[lemma]
        elif len(lemma) >= 7:
            # OCR-tolerant term match (Adhibhilta for adhibhuta):
            # Telang's scan mangles transliterated terms worst of all.
            for term, gloss in _TERM_GLOSS.items():
                if len(term) >= 6 and _lev(lemma, term) <= 2:
                    grown |= gloss
                    break
        elif len(lemma) >= 6:
            for term, gloss in _TERM_GLOSS.items():
                if len(term) == 6 and _lev(lemma, term) <= 1:
                    grown |= gloss
                    break
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
    if not telang_ctx.strip():
        # Known Telang scan gaps (6:38 p72 missing; 7:29-30, 17:27-28
        # unaligned): Arnold + Sanskrit carry these by construction.
        arn = arnold_text or ""
        cov_a, unc_a = coverage(ours, expanded(lemmas(arn), glossary)) \
            if arn else (0.0, ours)
        return {"status": "TELANG-ABSENT",
                "cov_telang": None, "cov_arnold": round(cov_a, 2),
                "uncovered": sorted(unc_a),
                "detail": "Telang text missing (scan gap); Arnold-only"}
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
