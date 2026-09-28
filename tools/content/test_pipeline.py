"""Pipeline unit tests (stdlib unittest — no third-party deps).

Run: python3 -m unittest test_pipeline -v  (from tools/content/)
Covers: IAST transliteration + round-trip, marker parsing, NFC, chapter table.
"""

import sys
import unittest

sys.path.insert(0, ".")
from iast import transliterate, detransliterate  # noqa: E402
from gita import parse_verses  # noqa: E402
from chapters import CHAPTERS, EXPECTED_TOTAL  # noqa: E402


class TestIast(unittest.TestCase):
    KNOWN = {
        "धर्मक्षेत्रे": "dharmakṣetre",
        "श्रीमद्भगवद्गीता": "śrīmadbhagavadgītā",
        "अर्जुन": "arjuna",
        "कृष्ण": "kṛṣṇa",
        "योगः": "yogaḥ",
        "कर्म": "karma",
        "सञ्जय": "sañjaya",
        "॥१-१॥": "||1-1||",
    }

    def test_known_words(self):
        for dev, want in self.KNOWN.items():
            self.assertEqual(transliterate(dev), want, dev)

    def test_round_trip_corpus(self):
        words = list(self.KNOWN) + [
            "मामकाः", "पाण्डवाश्चैव", "नैनं", "ज्ञानाग्निः", "ऐश्वर्यम्",
            "दुःखम्", "भगवानुवाच", "सर्वकर्माणि", "बुधा", "भावसमन्विताः",
            "क्षेत्रज्ञम्", "हृषीकेश", "धनञ्जय", "परन्तप", "गुडाकेश",
            # Regression corpus from real-chapter failures (each broke the
            # walker once): ṛ-after-consonant, oṃ-ambiguity, jña+matra.
            "स्पृहा", "विगतस्पृहः", "पवित्रमोंकार", "यज्ञैरिष्ट्वा",
            "ऋक्साम", "ॐ",
        ]
        for dev in words:
            with self.subTest(dev=dev):
                self.assertEqual(detransliterate(transliterate(dev)), dev)


class TestParse(unittest.TestCase):
    POEM = """ॐ श्रीपरमात्मने नमः
धृतराष्ट्र उवाच
धर्मक्षेत्रे कुरुक्षेत्रे समवेता युयुत्सवः ।
मामकाः पाण्डवाश्चैव किमकुर्वत संजय  ॥१-१॥
सञ्जय उवाच
दृष्ट्वा तु पाण्डवानीकं व्यूढं दुर्योधनस्तदा ।
आचार्यमुपसंगम्य राजा वचनमब्रवीत् ॥१-२॥"""

    def test_split_and_speakers(self):
        recs = parse_verses(self.POEM, 1)
        self.assertEqual(len(recs), 2)
        self.assertEqual(recs[0]["speaker"], "धृतराष्ट्र")
        self.assertIn("धर्मक्षेत्रे", recs[0]["devanagari"])
        self.assertNotIn("॥", recs[0]["devanagari"])
        self.assertEqual(recs[1]["speaker"], "सञ्जय")

    def test_nfc(self):
        import unicodedata
        recs = parse_verses(self.POEM, 1)
        for r in recs:
            self.assertEqual(unicodedata.normalize("NFC", r["devanagari"]), r["devanagari"])


class TestChapters(unittest.TestCase):
    def test_total_is_700(self):
        self.assertEqual(sum(c for _, c, _ in CHAPTERS.values()), EXPECTED_TOTAL)

    def test_all_18_present(self):
        self.assertEqual(sorted(CHAPTERS), list(range(1, 19)))


class TestConcur(unittest.TestCase):
    NV = None

    @classmethod
    def setUpClass(cls):
        from concur import build_name_vocab
        cls.NV = build_name_vocab([
            "The blind king asks Sanjaya what happened.",
            "Help arrives when grief is deepest.",
        ])

    def _rec(self, meaning, telang, arnold="Arnold paraphrase here"):
        from concur import concur_verse
        return concur_verse(meaning, telang, arnold, {}, self.NV)

    def test_consensus(self):
        r = self._rec("Grief overwhelms him and he spoke in despair.",
                      "Grief overwhelmed him; he spoke in despair and sorrow.",
                      "O'ercome by grief, he spake in deep despair.")
        self.assertEqual(r["status"], "CONSENSUS")

    def test_single_source_telang(self):
        r = self._rec("Grief overwhelms him and he spoke in despair.",
                      "Grief overwhelmed him; he spoke in despair and sorrow.",
                      "The lotus blooms at dawn in spring.")
        self.assertEqual(r["status"], "SINGLE-SOURCE")
        self.assertIn("Telang covers", r["detail"])

    def test_orphan(self):
        r = self._rec("Quantum entanglement collapses the waveform.",
                      "Grief overwhelmed him; he spoke in despair.",
                      "The lotus blooms at dawn in spring.")
        self.assertEqual(r["status"], "ORPHAN")

    def test_arnold_absent(self):
        from concur import concur_verse
        r = concur_verse("Grief overwhelms him and despair follows after.",
                         "Grief overwhelmed him in despair.", None, {},
                         self.NV)
        self.assertEqual(r["status"], "ARNOLD-ABSENT")

    def test_names_stripped(self):
        from concur import our_lemmas
        # Dhritarashtra/Sanjaya are names; asks/blind/king are claims.
        got = our_lemmas("The blind king Dhritarashtra asks Sanjaya.",
                         self.NV)
        self.assertNotIn("dhritarashtra", got)
        self.assertNotIn("sanjaya", got)
        self.assertIn("say", got)  # asks -> say family
        self.assertIn("blind", got)

    def test_archaic_bridge(self):
        from concur import lemmas, expanded
        # assembled (Telang) must cover our gathered via ARCHAIC map.
        self.assertIn("gather", expanded(lemmas("they assembled together"),
                                        {}))

    def test_stem_regressions(self):
        from concur import stem
        self.assertEqual(stem("duties"), "duty")
        self.assertEqual(stem("senses"), "sense")
        self.assertEqual(stem("nothing"), "nothing")
        self.assertEqual(stem("bows"), "bow")


class TestIast2(unittest.TestCase):
    # Differential engine: every case asserts iast == iast2 exactly,
    # including quirk-compatibility (bare-ḷ, nukta pass-through, oṃ).
    CASES_DEVA = [
        "धर्मक्षेत्रे कुरुक्षेत्रे",
        "यावदेतान्निरीक्षेऽहं योद्धुकामानवस्थितान् ।",
        "श्रद्धावाँल्लभते ज्ञानं",
        "पश्यञ्शृण्वन्स्पृशञ्जिघ्रन्",
        "ॐ तत्सदिति",
        "कृष्ण",
        "ज्ञानी तु",
        "संशयः",
    ]
    CASES_IAST = [
        "dharmakṣetre kurukṣetre",
        "śraddhāvām̐llabhate jñānaṃ",
        "oṃ tatsaditi",
        "kṛṣṇa",
        "jñaḥ",
        "kḷptam",  # bare-ḷ quirk: both engines render ṛ-matra
        "qalam",  # nukta q: both pass through (documented approx)
    ]

    def test_transliterate_agrees(self):
        from iast import transliterate
        from iast2 import transliterate2
        for dev in self.CASES_DEVA:
            self.assertEqual(transliterate2(dev), transliterate(dev), dev)

    def test_detransliterate_agrees(self):
        from iast import detransliterate
        from iast2 import detransliterate2
        for txt in self.CASES_IAST:
            self.assertEqual(detransliterate2(txt), detransliterate(txt),
                             txt)

    def test_round_trip2(self):
        import unicodedata
        from iast2 import transliterate2, detransliterate2
        for dev in self.CASES_DEVA:
            rt = detransliterate2(transliterate2(dev))
            self.assertEqual(rt, unicodedata.normalize("NFC", dev), dev)


if __name__ == "__main__":
    unittest.main()
