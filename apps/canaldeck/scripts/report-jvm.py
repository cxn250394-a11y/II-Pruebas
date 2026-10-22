"""Summarize the JUnit XML emitted by the exact CI build; never synthesize test results."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
paths=sorted((root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
assert paths, "No JVM JUnit evidence"
suites=[]
for p in paths:
    r=ET.parse(p).getroot()
    suites.append({k:r.attrib.get(k,"") for k in ("name","tests","failures","errors","skipped")})
totals={k:sum(int(s[k] or 0) for s in suites) for k in ("tests","failures","errors","skipped")}
assert totals["tests"]>0 and totals["failures"]==0 and totals["errors"]==0, totals
out={"schema_version":1,"runtime":"Gradle Android JVM/Robolectric","totals":totals,"suites":suites}
(root/"evidence").mkdir(exist_ok=True)
(root/"evidence/jvm-results.json").write_text(json.dumps(out,indent=2)+"\n")
print("JVM_EVIDENCE="+json.dumps(totals,sort_keys=True))
