#!/usr/bin/env python3
"""Build the prepared companion with two CPUs, one worker and low scheduling priority."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

from prepare import REVISION, ROOT, verify_security_manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "build/signal-companion/source")
    args = parser.parse_args()
    source = args.source.resolve()
    if not (source / "ORBILIQ-COMPANION.md").is_file():
        parser.error("Run prepare.py first; companion marker missing")
    if (source / "ORBILIQ-PENDING-ISOLATION").exists():
        parser.error("Source regeneration is pending; do not install an earlier APK")
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    if revision != REVISION:
        parser.error("Prepared source has an unexpected upstream revision")
    try:
        verify_security_manifest(source)
    except (OSError, ValueError, RuntimeError):
        parser.error("Companion security generation is stale or modified; regenerate source")
    for name in ("authenticator.xml", "syncadapter.xml"):
        xml = ET.parse(source / "app/src/main/res/xml" / name).getroot()
        if xml.get("{http://schemas.android.com/apk/res/android}accountType") != "org.orbiliq.signal.companion":
            parser.error("Android AccountManager identity is not isolated; regenerate companion source")
    for relative in (
        "lib/network/src/main/java/org/signal/network/api/RegistrationApiV2.kt",
        "lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/registration/RegistrationApi.kt",
    ):
        if (source / relative).read_text().count("Companion supports linked devices only") != 4:
            parser.error("Primary registration is not blocked; generate a fresh companion source tree")
    if not (source / "feature/registration/src/main/java/org/signal/registration/CompanionRegistrationPolicy.kt").is_file():
        parser.error("Companion registration navigation guard missing; regenerate source")
    jdk = os.environ.get("JAVA_HOME")
    if not jdk:
        parser.error("Set JAVA_HOME to JDK 21")
    version = subprocess.run([str(Path(jdk) / "bin/java"), "-version"], capture_output=True, text=True, check=True)
    if not re.search(r'version "21(?:\.|"|-)', version.stdout + version.stderr):
        parser.error("JAVA_HOME must select JDK 21")
    if hasattr(os, "sched_getaffinity"):
        os.sched_setaffinity(0, set(sorted(os.sched_getaffinity(0))[:2]))
    if hasattr(os, "nice"):
        os.nice(15)
    os.chdir(source)
    # Preserve upstream configuration caching; do not start a second compiler daemon.
    os.execv("./gradlew", ["./gradlew", "--no-daemon", "--configuration-cache", "--max-workers=1",
        "-Dorg.gradle.jvmargs=-Xmx3584m -XX:ActiveProcessorCount=2 -XX:+UseG1GC",
        "-Pkotlin.compiler.execution.strategy=in-process", ":Signal-Android:assembleGithubProdSpinner"])


if __name__ == "__main__":
    main()
