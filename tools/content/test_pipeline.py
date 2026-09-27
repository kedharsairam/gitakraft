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


if __name__ == "__main__":
    unittest.main()
