"""Source wiring regression, complementary to JVM/device behavior tests (no TDLib login).

Optional app root permits checking the same contract against the frozen parent source.
"""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
code = root / "app/src/main/java/mx/charly/canal"
session = (code / "TelegramSession.kt").read_text()
service = (code / "ConnectionService.kt").read_text()
settings = (code / "RelaySettingsActivity.kt").read_text()

assert "private val singleRelay = CompiledRelayLane {" in session, "Session must gate BEFORE bridge factory"
assert "private val relay = CompiledRelayLane { RelayCoordinator(" in service, "Service must gate BEFORE coordinator factory"
lane = (code / "CompiledRelayLane.kt").read_text()
assert lane.index("if (!BuildConfig.PICK_RELAY_REAL_ENABLED || retired) return") < lane.index("factory()")
assert "private val factory: () -> T" in lane and "instance ?: factory()" in lane
assert "val active = instance" in lane and "active?.close()" in lane
assert "singleRelay.with(operation)" in session and "operation(singleRelay)" not in session
assert re.search(r"if \(BuildConfig.PICK_RELAY_REAL_ENABLED\)\s+scope.launch \{ while", session)
assert "withSingleRelay { it.close() }" not in session, "Teardown must not instantiate a lane"
assert session.count("singleRelay.close()") == 3
assert service.count("RelayCoordinator(") == 1 and session.count("ExistingClientRelayBridge(") == 1
assert "relay?." not in service
for name in ("ExistingClientRelayBridge.kt", "RelayCoordinator.kt"):
    source = (code / name).read_text()
    assert source.index('init { check(BuildConfig.PICK_RELAY_REAL_ENABLED)') < source.index('context.applicationContext'), name

render = settings[settings.index("private fun render()") : settings.index("private fun chooseDestination()")]
gate = render.index("if (!BuildConfig.PICK_RELAY_REAL_ENABLED)")
assert gate < render.index("val session = connected()") < render.index("RelayPreferences(")
assert "return" in render[gate:render.index("val session = connected()")]
assert "observed = if (BuildConfig.PICK_RELAY_REAL_ENABLED)" in settings
for name in ("chooseDestination()", "chooseSource()", "renderCoverage(preferences: RelayPreferences)"):
    assert re.search(r"private fun " + re.escape(name) + r" \{\s+if \(!BuildConfig.PICK_RELAY_REAL_ENABLED\) return", settings), name

# The relay fuse must not wrap the main collector, local notices, reads or login.
assert session.count("TdlClient.create()") == 1 and session.count("allUpdates.collect") == 1
assert "check(!BuildConfig.OFFLINE_REHEARSAL_ONLY)" in session
assert "if (ready) onMessage?.invoke(u.message)" in session
assert "relay.with { it.onLiveMessage(message) }; notice(message)" in service
assert "relay.with { it.onSessionChanged() }; updateLocalSession(); updateConnection()" in service
assert "LocalPickDispatcher(org).submit" in service and "liveRouting.sourcePolicy(m.chatId)" in service
print("PASS P2-11A wiring: OFF before both factories, timer and settings metadata; independent client/notices retained")
