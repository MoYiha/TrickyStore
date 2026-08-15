from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


# Centralize the weighted choice so every full identity refresh is coherent.
rel = "service/src/main/java/cleveres/tricky/cleverestech/util/RandomUtils.kt"
text = read(rel)
text = replace_once(
    text,
    '''    private val threadLocalRandom = ThreadLocal.withInitial { SecureRandom() }\n\n    fun generateLuhn(''',
    '''    private val threadLocalRandom = ThreadLocal.withInitial { SecureRandom() }\n    private val visibleSimCounts = listOf("0", "1", "1", "1", "1", "2", "2")\n    private val activeVisibleSimCounts = listOf("1", "1", "1", "1", "2", "2")\n\n    fun generateVisibleSimCount(allowZero: Boolean): String =\n        choose(if (allowZero) visibleSimCounts else activeVisibleSimCounts) ?: "1"\n\n    fun generateLuhn(''',
    "RandomUtils visible SIM helper",
)
write(rel, text)

# Single-field randomization may intentionally choose 0. Group/all randomization
# generates SIM identifiers too, so it must expose at least one SIM.
rel = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"
text = read(rel)
text = replace_once(
    text,
    '''            "visible_sim_count" -> RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1"''',
    '''            "visible_sim_count" -> RandomUtils.generateVisibleSimCount(allowZero = true)''',
    "single SIM random helper",
)
text = replace_once(
    text,
    '''                    "visible_camera_count",\n                )\n            }''',
    '''                    "visible_camera_count",\n                )\n                json.put("visible_sim_count", RandomUtils.generateVisibleSimCount(allowZero = false))\n            }''',
    "Random All active SIM count",
)
text = replace_once(
    text,
    '''                    "visible_sim_count",\n                )\n            "device" -> putFields("serial")''',
    '''                    "visible_sim_count",\n                )\n                .also { json.put("visible_sim_count", RandomUtils.generateVisibleSimCount(allowZero = false)) }\n            "device" -> putFields("serial")''',
    "Telephony active SIM count",
)
# Environment reset also generates actual SIM identity values.
text = text.replace(
    '''"VISIBLE_SIM_COUNT" to\n                                (RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1")''',
    '''"VISIBLE_SIM_COUNT" to RandomUtils.generateVisibleSimCount(allowZero = false)''',
)
write(rel, text)

# Boot identity refresh generates both slots as well; keep count non-zero.
rel = "service/src/main/java/cleveres/tricky/cleverestech/Config.kt"
text = read(rel)
old = '''"VISIBLE_SIM_COUNT" to\n                        (RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1")'''
count = text.count(old)
if count != 1:
    raise RuntimeError(f"boot active SIM count: expected one anchor, found {count}")
text = text.replace(old, '''"VISIBLE_SIM_COUNT" to RandomUtils.generateVisibleSimCount(allowZero = false)''', 1)
write(rel, text)

# Tighten service regressions: All + Telephony cannot report 0 active SIMs.
rel = "service/src/test/java/cleveres/tricky/cleverestech/WebServerIdentityTest.kt"
text = read(rel)
text = replace_once(
    text,
    '''        assertTrue(json.getInt("visible_sim_count") in 0..2)\n        assertTrue(json.getInt("visible_camera_count") in 1..4)''',
    '''        assertTrue(json.getInt("visible_sim_count") in 1..2)\n        assertTrue(json.getInt("visible_camera_count") in 1..4)''',
    "Random All SIM coherence assertion",
)
text = replace_once(
    text,
    '''        assertTrue(json.getInt("visible_sim_count") in 0..2)\n        assertFalse(json.has("serial"))''',
    '''        assertTrue(json.getInt("visible_sim_count") in 1..2)\n        assertFalse(json.has("serial"))''',
    "Telephony SIM coherence assertion",
)
write(rel, text)
