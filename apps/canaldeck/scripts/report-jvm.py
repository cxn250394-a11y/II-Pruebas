"""Summarize the JUnit XML emitted by the exact CI build; never synthesize test results."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
variants={}
for flavor in ("normal", "offline"):
    task="test"+flavor.capitalize()+"DebugUnitTest"
    paths=sorted((root/"app/build/test-results"/task).glob("TEST-*.xml"))
    assert paths, f"No JVM JUnit evidence for {flavor}"
    suites=[]
    for p in paths:
        r=ET.parse(p).getroot()
        suites.append({k:r.attrib.get(k,"") for k in ("name","tests","failures","errors","skipped")})
    totals={k:sum(int(s[k] or 0) for s in suites) for k in ("tests","failures","errors","skipped")}
    expected = {"normal":375, "offline":378}[flavor]
    assert totals["tests"]==expected and totals["failures"]==0 and totals["errors"]==0 and totals["skipped"]==0, (flavor, totals)
    variants[flavor]={"task":task,"totals":totals,"suites":suites}
    print("JVM_EVIDENCE_"+flavor.upper()+"="+json.dumps(totals,sort_keys=True))
totals={k:sum(v["totals"][k] for v in variants.values()) for k in ("tests","failures","errors","skipped")}
out={"schema_version":2,"runtime":"Gradle Android JVM/Robolectric","totals":totals,
     "counting":"variant executions; shared tests execute once per flavor", "variants":variants}
(root/"evidence").mkdir(exist_ok=True)
(root/"evidence/jvm-results.json").write_text(json.dumps(out,indent=2)+"\n")
print("JVM_VARIANT_EXECUTIONS="+json.dumps(totals,sort_keys=True))
