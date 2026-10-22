"""Build-time isolation checks for the v0.4 offline relay lane."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
code = root / "app/src/main/java/mx/charly/canal"
gradle = (root / "build.gradle").read_text()
fuses = re.findall(r"buildConfigField\s+'boolean',\s*'PICK_RELAY_REAL_ENABLED',\s*'([^']+)'", gradle)
assert fuses == ["false"], "Real relay must be compiled OFF, not a Gradle/user override"
assert "applicationId 'mx.charly.canal.offline'" in gradle
assert "buildConfigField 'boolean', 'OFFLINE_REHEARSAL_ONLY', 'true'" in gradle
session = (code / "TelegramSession.kt").read_text()
assert session.index('check(!BuildConfig.OFFLINE_REHEARSAL_ONLY)') < session.index('TelegramSession(context.applicationContext)')
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
assert 'android.permission.INTERNET' not in manifest
for filename in ['AsyncRelayEngine.kt', 'FakeAsyncRelay.kt', 'AsyncOfflineDemo.kt', 'AsyncOfflineDemoActivity.kt']:
    source = (code / filename).read_text()
    assert 'dev.g000sha256' not in source and 'TelegramSession' not in source and 'TdlClient' not in source, filename
mapper = (code / 'TdlAsyncAckMapper.kt').read_text()
assert 'TdlClient' not in mapper and 'client.' not in mapper
gate = (code / "RelayRealGate.kt").read_text()
assert "BuildConfig.PICK_RELAY_REAL_ENABLED && userEnabled" in gate
production = list(code.glob("*.kt"))
assert sum(p.read_text().count("TdlClient.create()") for p in production) == 1, "Use the existing single client"
assert sum(p.read_text().count("allUpdates.collect") for p in production) == 1, "Use the existing single update stream"
coordinator = (code / "RelayCoordinator.kt").read_text()
# The live pipeline can prepare, but must have no outgoing SDK/activation call site.
for forbidden in ["client.sendMessage(", "client.forwardMessages(", ".deliverAfterSeparateActivation(", ".deliverSynthetic("]:
    assert forbidden not in coordinator, forbidden
assert ".prepare(" in coordinator
assert sum(p.read_text().count(".deliverAfterSeparateActivation(") for p in production) == 0
fake = (code / "FakeRelayTdlib.kt").read_text()
assert "import dev.g000sha256" not in fake and "TdlClient.create" not in fake
adapter = (code / "TdlRelayAdapter.kt").read_text()
assert "deliverAfterSeparateActivation" not in adapter, "Retire the obsolete synchronous delivery path"
assert "client.sendMessage(" not in adapter and "client.forwardMessages(" not in adapter
assert "downloadFile(" not in adapter and "InputMessagePhoto(" not in adapter
single = (code / 'TdlSingleRelayCalls.kt').read_text()
assert 'TdlClient.create' not in single and 'allUpdates' not in single
assert 'RelayRealGate.requireOpen(config.userEnabled)' in single and 'RelayRealGate.requireOpen(true)' in single
assert 'sendCopy = false' in single and 'removeCaption = false' in single
assert 'paidMessageStarCount = 0' in single and 'allowPaidBroadcast = false' in single
assert 'downloadFile(' not in single and 'InputMessagePhoto(' not in single
for name in ['SingleRelayController.kt', 'SingleRelayModels.kt', 'SingleRelayPlanning.kt']:
    pure = (code / name).read_text()
    assert 'dev.g000sha256' not in pure and 'TdlClient' not in pure and 'TelegramSession' not in pure, name
database = (code / "RelayDatabase.kt").read_text()
for ddl in re.findall(r'CREATE TABLE [^"]+', database):
    assert not re.search(r'\b(body|caption|permalink|photo|message_text|media_bytes)\b', ddl), ddl
assert "metric != RelayMetric.REAL_CONFIRMED" in database
print("PASS: compiled OFF; one existing client and update stream; legacy preparation only; guarded native singleton seam; Fake has no SDK; no protected-media copy; metadata-only ledger")
