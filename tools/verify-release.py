"""Verify the four release artifacts, using Android build-tools and Python only."""
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

project = Path(__file__).resolve().parents[1]
sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
tools = sdk / "build-tools/36.0.0"
output = Path(sys.argv[1]).resolve()
output.mkdir(parents=True, exist_ok=True)
reports = output / "reports"
reports.mkdir(exist_ok=True)
expected_certificate = "6d3055d0260d6afba37c6f8505b2e556e47d56d8363c0399fc43a5f0e11e28d6"
variants = {"arm64-v8a": {"arm64-v8a"}, "armeabi-v7a": {"armeabi-v7a"}, "x86_64": {"x86_64"}, "universal": {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}}
payload = [50,127,146,30,87,248,112,254,249,14,156,129,63,185,53,69,157,8,4,185,192,228,219,18,140,162,53,84,35,82,229]
mask = [93,17,203,46,119,164,38]
destination = bytes(v ^ mask[i % len(mask)] ^ ((i*19+7)&255) for i,v in enumerate(payload))
assert hashlib.sha256(destination).hexdigest() == "3587e5af0ebc1488728690188aad7958f87653162419d974d4d0dd34997c2a30"

def run(args):
    return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT).decode()

def elf_load_alignments(data):
    assert data[:4] == b"\x7fELF"
    order = "<" if data[5] == 1 else ">"
    if data[4] == 2:
        offset = struct.unpack_from(order+"Q", data, 32)[0]
        stride, count = struct.unpack_from(order+"HH", data, 54)
        align_position, align_format = 48, "Q"
    else:
        offset = struct.unpack_from(order+"I", data, 28)[0]
        stride, count = struct.unpack_from(order+"HH", data, 42)
        align_position, align_format = 28, "I"
    return [struct.unpack_from(order+align_format, data, offset+i*stride+align_position)[0]
            for i in range(count) if struct.unpack_from(order+"I", data, offset+i*stride)[0] == 1]

summary = []
checksums = []
for name, expected_abis in variants.items():
    source = project / f"app/build/outputs/apk/release/app-{name}-release.apk"
    target = output / f"{name}.apk"
    shutil.copy2(source, target)
    signature = run([tools/"apksigner", "verify", "--verbose", "--print-certs", target])
    assert expected_certificate in signature.lower(), f"Wrong signer: {name}"
    for scheme in (1,2,3):
        assert re.search(rf"Verified using v{scheme} scheme.*: true", signature), f"Missing signature v{scheme}: {name}"
    (reports/f"signature-{name}.txt").write_text(signature)
    badging = run([tools/"aapt", "dump", "badging", target])
    assert "name='org.uniapk.upgrader'" in badging
    assert "versionCode='1012'" in badging and "versionName='0.7.0-rc4'" in badging
    assert "sdkVersion:'23'" in badging and "targetSdkVersion:'37'" in badging
    assert "application-debuggable" not in badging
    assert "application-label:'Universal APK Upgrader'" in badging
    # Legacy aapt badging omits launcher aliases; verify their compiled nodes and label resource below.
    (reports/f"badging-{name}.txt").write_text(badging)
    alignment = run([tools/"zipalign", "-c", "-P", "16", "-v", "4", target])
    (reports/f"alignment-{name}.txt").write_text(alignment)
    manifest = run([tools/"aapt", "dump", "xmltree", target, "AndroidManifest.xml"])
    app_icon = re.search(r"android:icon\([^)]*\)=(@0x[0-9a-f]+)",manifest).group(1)
    resources = run([tools/"aapt", "dump", "--values", "resources", target])
    short = re.search(r'resource (0x[0-9a-f]+) [^\n]*:string/app_short_name:[^\n]*\n\s+\(string8\) "UniAPK"',resources)
    assert short, "Missing UniAPK launcher label resource"
    nodes = re.findall(r"^      E: (activity(?:-alias)?) [^\n]*\n(.*?)(?=^      E: |\Z)",manifest,re.M|re.S)
    aliases = {}
    main = None
    for kind,body in nodes:
        attrs = dict(re.findall(r"^        A: android:([A-Za-z]+)\([^)]*\)=(.*)$",body,re.M))
        if kind == "activity-alias":
            name = re.search(r'"([^"]+)"',attrs["name"]).group(1).split(".")[-1]
            assert "android.intent.action.MAIN" in body and "android.intent.category.LAUNCHER" in body
            assert attrs["label"] == "@"+short.group(1), name
            assert "org.uniapk.upgrader.ui.activity.MainActivity" in attrs["targetActivity"], name
            assert attrs["exported"].endswith("0xffffffff"), name
            aliases[name] = attrs
        elif "org.uniapk.upgrader.ui.activity.MainActivity" in attrs.get("name",""):
            main=attrs
    assert set(aliases)=={"Launcher"+v for v in ("Original","Black","White","Milk","Transparent")}
    assert main is not None and main["icon"]==app_icon and aliases["LauncherOriginal"]["icon"]==app_icon
    assert len({a["icon"] for a in aliases.values()})==5
    assert aliases["LauncherOriginal"]["enabled"].endswith("0xffffffff")
    assert all(a["enabled"].endswith("0x0") for n,a in aliases.items() if n!="LauncherOriginal")
    debug = re.search(r"android:debuggable\([^)]*\)=\(type 0x12\)0x([0-9a-f]+)",manifest)
    assert debug is None or int(debug[1],16) == 0
    (reports/f"manifest-{name}.txt").write_text(manifest)
    native = []
    with zipfile.ZipFile(target) as archive:
        libraries = [entry for entry in archive.infolist() if entry.filename.startswith("lib/") and entry.filename.endswith(".so")]
        abis = {entry.filename.split('/')[1] for entry in libraries}
        assert abis == expected_abis, (name, abis, expected_abis)
        for entry in libraries:
            alignments = elf_load_alignments(archive.read(entry))
            assert alignments and all(value >= 16384 for value in alignments), (name,entry.filename,alignments)
            native.append({"library":entry.filename,"loadAlignments":alignments})
        for entry in archive.infolist():
            if entry.filename.endswith((".dex", ".xml", ".arsc", ".json", ".txt")):
                assert destination not in archive.read(entry), f"Unobfuscated support destination in {entry.filename}"
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    checksums.append(f"{digest}  {target.name}")
    summary.append({"file":target.name,"size":target.stat().st_size,"sha256":digest,"abis":sorted(abis),"versionCode":1012,"versionName":"0.7.0-rc4","debuggable":False,"certificateSha256":expected_certificate,"signatureSchemes":[1,2,3],"zipAlignmentKiB":16,"native":native,"originalInstallerIconMatchesOriginalLauncher":True,"launcherVariants":5,"supportDestinationAbsentInPlaintext":True})

(output/"SHA256SUMS.txt").write_text("\n".join(checksums)+"\n")
(reports/"release-verification.json").write_text(json.dumps(summary,indent=2)+"\n")
print(json.dumps([{"file":s["file"],"size":s["size"],"sha256":s["sha256"],"abis":s["abis"]} for s in summary],indent=2))
