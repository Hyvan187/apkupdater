"""Normalize the official 37.0 platform metadata for AGP's integer compileSdk 37.

The framework android.jar is deliberately left unchanged.
Run after sdkmanager 'platforms;android-37.0'.
"""
import os
from pathlib import Path
import xml.etree.ElementTree as ET

sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
platform = sdk / "platforms/android-37.0"
if not platform.exists():
    platform = sdk / "platforms/android-37"
if not platform.exists():
    raise SystemExit("Install the official Android 37.0 platform first")
properties = platform / "source.properties"
properties.write_text(properties.read_text().replace("AndroidVersion.ApiLevel=37.0", "AndroidVersion.ApiLevel=37"))
metadata = platform / "package.xml"
if metadata.exists():
    tree = ET.parse(metadata)
    for item in tree.iter():
        if item.tag.rsplit("}", 1)[-1] == "api-level" and item.text == "37.0":
            item.text = "37"
    tree.write(metadata, encoding="utf-8", xml_declaration=True)
