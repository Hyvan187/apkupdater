"""Normalize the official 37.0 platform metadata for AGP's integer compileSdk 37.

The framework android.jar is deliberately left unchanged.
Run after sdkmanager 'platforms;android-37.0'.
"""
import os
from pathlib import Path
import re

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
    # Preserve prefixes referenced inside xsi:type values; ElementTree does not
    # track those QName values and can otherwise drop their namespace bindings.
    text = metadata.read_text()
    text = re.sub(r"(<(?:[A-Za-z0-9_]+:)?api-level>)37\.0(</(?:[A-Za-z0-9_]+:)?api-level>)", r"\g<1>37\g<2>", text)
    metadata.write_text(text)
