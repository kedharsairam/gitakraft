"""Chapter registry: Wikisource subpage + expected verse count (vulgate).

Expected counts are the standard Gita Press numbering (total 700).
Validation FAILS LOUDLY on mismatch — counts are investigated, never
auto-adjusted. Ch13 is 34 here; some editions number it 35 (extra
division of 13.1 in the Kshetra/Kshetrajna split) — if the fetched text
shows 35, that is a finding to record, not a bug to squash.
"""

CHAPTERS = {
    1: ("अर्जुनविषादयोगः", 47, "Arjuna's Despair"),
    2: ("साङ्ख्ययोगः", 72, "The Yoga of Knowledge"),
    3: ("कर्मयोगः", 43, "The Yoga of Action"),
    4: ("ज्ञानकर्मसंन्यासयोगः", 42, "The Yoga of Knowledge and Renunciation"),
    5: ("कर्मसंन्यासयोगः", 29, "The Yoga of Renunciation"),
    6: ("आत्मसंयमयोगः", 47, "The Yoga of Self-Control"),
    7: ("ज्ञानविज्ञानयोगः", 30, "The Yoga of Knowledge and Wisdom"),
    8: ("अक्षरब्रह्मयोगः", 28, "The Yoga of the Imperishable"),
    9: ("राजविद्याराजगुह्ययोगः", 34, "The Yoga of Royal Knowledge"),
    10: ("विभूतियोगः", 42, "The Yoga of Divine Manifestations"),
    11: ("विश्वरूपदर्शनयोगः", 55, "The Vision of the Cosmic Form"),
    12: ("भक्तियोगः", 20, "The Yoga of Devotion"),
    13: ("क्षेत्रक्षेत्रज्ञविभागयोगः", 34, "Field and Knower of the Field"),
    14: ("गुणत्रयविभागयोगः", 27, "The Three Qualities"),
    15: ("पुरुषोत्तमयोगः", 20, "The Supreme Person"),
    16: ("दैवासुरसम्पद्विभागयोगः", 24, "Divine and Demonic Natures"),
    17: ("श्रद्धात्रयविभागयोगः", 28, "Three Kinds of Faith"),
    18: ("मोक्षसंन्यासयोगः", 78, "Liberation and Renunciation"),
}

EXPECTED_TOTAL = 700

# Plain-English display titles for the app library (approved by Kedhar
# 2026-09-27; he drafts nothing, vetoes only). Traditional names stay in
# CHAPTERS above; these are what readers see.
DISPLAY_TITLES = {
    1: "Arjuna's Despair",
    2: "Wisdom of the Soul",
    3: "Selfless Action",
    4: "Wisdom in Action",
    5: "The Art of Letting Go",
    6: "The Disciplined Mind",
    7: "Knowing the Whole",
    8: "Death and Beyond",
    9: "The Kingly Secret",
    10: "Everywhere the Divine",
    11: "The Universe in One",
    12: "Love Is the Way",
    13: "The Field and the Witness",
    14: "Three Moods of Nature",
    15: "Roots Above",
    16: "Two Natures",
    17: "Faith, Food, and Ritual",
    18: "Freedom and Surrender",
}

WIKISOURCE_API = "https://sa.wikisource.org/w/api.php"
EN_WIKISOURCE_API = "https://en.wikisource.org/w/api.php"
TELANG_INDEX = "Sacred Books of the East - Volume VIII.djvu"
# Gita text spans these DjVu pages (from the volume transclusion tag).
TELANG_PAGE_FROM = 43
TELANG_PAGE_TO = 137
