"""Build-time isolation checks for the v0.4 offline relay lane."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
code = root / "app/src/main/java/mx/charly/canal"
gradle = (root / "build.gradle").read_text()
fuses = re.findall(r"buildConfigField\s+'boolean',\s*'PICK_RELAY_REAL_ENABLED',\s*'([^']+)'", gradle)
assert fuses == ["false"], "Real relay must be compiled OFF, not a Gradle/user override"
gate = (code / "RelayRealGate.kt").read_text()
assert "BuildConfig.PICK_RELAY_REAL_ENABLED && userEnabled" in gate
production = list(code.glob("*.kt"))
assert sum(p.read_text().count("TdlClient.create()") for p in production) == 1, "Use the existing single client"
coordinator = (code / "RelayCoordinator.kt").read_text()
# The live pipeline can prepare, but must have no outgoing SDK/activation call site.
for forbidden in ["client.sendMessage(", "client.forwardMessages(", ".deliverAfterSeparateActivation(", ".deliverSynthetic("]:
    assert forbidden not in coordinator, forbidden
assert ".prepare(" in coordinator
assert sum(p.read_text().count(".deliverAfterSeparateActivation(") for p in production) == 0
fake = (code / "FakeRelayTdlib.kt").read_text()
assert "import dev.g000sha256" not in fake and "TdlClient.create" not in fake
adapter = (code / "TdlRelayAdapter.kt").read_text()
assert "requireCurrentConsent(config)" in adapter
assert "sendCopy = false" in adapter and "removeCaption = false" in adapter
assert "paidMessageStarCount = 0" in adapter and "allowPaidBroadcast = false" in adapter
assert "downloadFile(" not in adapter and "InputMessagePhoto(" not in adapter
database = (code / "RelayDatabase.kt").read_text()
for ddl in re.findall(r'CREATE TABLE [^"]+', database):
    assert not re.search(r'\b(body|caption|permalink|photo|message_text|media_bytes)\b', ddl), ddl
assert "metric != RelayMetric.REAL_CONFIRMED" in database
print("PASS: compiled OFF; one existing client; live preparation has no output call site; Fake has no SDK; no protected-media copy; private metadata-only ledger")
