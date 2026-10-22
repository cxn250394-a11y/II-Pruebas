"""Inspect four built APKs and their generated compile-time constants; no install or account."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME"))
parser.add_argument("--output", type=Path, default=root / "app/build/variant-audit")
args = parser.parse_args()
assert args.sdk, "ANDROID_HOME or --sdk required"
aapt = Path(args.sdk) / "build-tools/37.0.0/aapt2"
signer = Path(args.sdk) / "build-tools/37.0.0/apksigner"
out = args.output
out.mkdir(parents=True, exist_ok=True)
records = []
for flavor in ("normal", "offline"):
    for kind in ("debug", "release"):
        variant = flavor + kind.capitalize()
        suffix = "debug" if kind == "debug" else "release-unsigned"
        apk = root / f"app/build/outputs/apk/{flavor}/{kind}/app-{flavor}-{suffix}.apk"
        assert apk.is_file(), f"Missing APK: {apk}"
        badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
        tree = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
        package = re.search(r"package: name='([^']+)'", badging).group(1)
        permissions = re.findall(r"uses-permission[^:]*: name='([^']+)'", badging)
        expected_id = "mx.charly.canal" + (".offline" if flavor == "offline" else "")
        assert package == expected_id, (variant, package)
        assert ("android.permission.INTERNET" in permissions) == (flavor == "normal"), variant
        assert "android.permission.QUERY_ALL_PACKAGES" not in permissions
        assert "sharedUserId" not in tree, "No shared UID/data authority between variants"
        assert ('application-debuggable' in badging) == (kind == "debug"), variant
        assert ('E: queries' in tree) == (kind == "debug"), variant
        assert re.search(r':allowBackup[^\n]*=false', tree), variant
        assert ("AsyncOfflineDemoActivity" in tree) == (flavor == "offline"), variant
        expected_label = "CanalDeck Offline" if flavor == "offline" else "CanalDeck"
        assert f"application-label:'{expected_label}'" in badging, variant
        generated = root / f"app/build/generated/source/buildConfig/{flavor}/{kind}/mx/charly/canal/BuildConfig.java"
        source = generated.read_text()
        assert re.search(r'boolean PICK_RELAY_REAL_ENABLED\s*=\s*false;', source), variant
        expected_offline = 'true' if flavor == 'offline' else 'false'
        assert re.search(r'boolean OFFLINE_REHEARSAL_ONLY\s*=\s*'+expected_offline+r';', source), variant
        assert f'APPLICATION_ID = "{expected_id}"' in source, variant
        signature = subprocess.run([str(signer), "verify", "--verbose", "--print-certs", str(apk)],
                                   text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        assert (signature.returncode == 0) == (kind == "debug"), (variant, signature.stdout)
        if kind == "release":
            # An arbitrary verifier failure is insufficient to establish unsigned status.
            with zipfile.ZipFile(apk) as archive:
                assert not any(re.match(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)$', name, re.I)
                               for name in archive.namelist()), variant
            assert b'APK Sig Block 42' not in apk.read_bytes(), variant
            assert 'DOES NOT VERIFY' in signature.stdout, (variant, signature.stdout)
        (out / f"{variant}-manifest.txt").write_text(badging + "\n" + tree)
        (out / f"{variant}-BuildConfig.java").write_text(source)
        (out / f"{variant}-signature.txt").write_text(signature.stdout)
        records.append({"variant":variant,"application_id":package,"label":expected_label,
            "internet":flavor=="normal","offline_rehearsal_only":flavor=="offline",
            "real_output_enabled":False,"debuggable":kind=="debug","release_signed":False if kind=="release" else None,
            "apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),
            "identity_does_not_attest_installed_signing_compatibility":True})
assert len({r['application_id'] for r in records}) == 2
report={"schema_version":1,"runtime":"four built APK manifests and generated BuildConfig; release not executed",
        "variants":records,"inspection_invokes_no_account_or_send_api":True,
        "release_inspection_only":True,"no_owner_installation_requested":True}
(out / "variants.json").write_text(json.dumps(report,indent=2)+"\n")
print("VARIANT_APK_EVIDENCE="+json.dumps(records,sort_keys=True))
