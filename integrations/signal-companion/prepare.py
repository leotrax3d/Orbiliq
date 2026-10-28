#!/usr/bin/env python3
"""Create a separate, pinned AGPL Signal companion source checkout. Never patches Orbiliq.

The generated tree keeps upstream Git history, license, source and dependency verification.
It is a research build, not an audited release or a complete messaging integration.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

REVISION = "6e9dddc01f1b26e608ac683807c51e139c141ed2"
REPOSITORY = "https://github.com/signalapp/Signal-Android.git"
ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"


def run(*args, cwd=None):
    return subprocess.check_output(args, cwd=cwd, text=True).strip()


def configure(checkout: Path):
    if run("git", "rev-parse", "HEAD", cwd=checkout) != REVISION:
        raise RuntimeError("Unexpected upstream revision")
    build = checkout / "app/build.gradle.kts"
    text = build.read_text()
    needle = "  defaultConfig {\n"
    if text.count(needle) != 1:
        raise RuntimeError("Upstream build schema changed")
    text = text.replace(needle, needle + '    applicationId = "org.orbiliq.signal.companion"\n')
    # GitHub distribution has no Signal self-updater; explicitly enable linking for this prototype.
    needle = '      buildConfigField("String", "BUILD_DISTRIBUTION_TYPE", "\\\"github\\\"")'
    if text.count(needle) != 1:
        raise RuntimeError("Upstream distribution schema changed")
    text = text.replace(needle, needle + '\n      buildConfigField("boolean", "LINK_DEVICE_UX_ENABLED", "true")')
    # Local debug builds share the SDK debug signer with the local Orbiliq build.
    # No upstream signing identity or production signing key is copied into this repository.
    text += '''
// Orbiliq companion research build: same local debug signer, independent application ID.
android.signingConfigs.getByName("debug") {
  storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
  storePassword = "android"
  keyAlias = "AndroidDebugKey"
  keyPassword = "android"
}

// This research APK does not need the JVM bytecode optimizer. Keep protocol libraries
// and release variants unchanged; reduce the peak of the large debug app compiler.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  if (name == "compileGithubProdSpinnerKotlin") {
    compilerOptions.freeCompilerArgs.add("-Xno-optimize")
  }
}
'''
    build.write_text(text)

    # Prototype diagnostics must not copy raw upstream messages/exceptions into persistent logs.
    application = checkout / "app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java"
    text = application.read_text()
    needle = "Log.initialize(RemoteConfig::internalUser, AndroidLogger.INSTANCE, PersistentLogger.getInstance(this));"
    if text.count(needle) != 1:
        raise RuntimeError("Upstream logging initialization changed")
    application.write_text(text.replace(needle, "Log.initialize(RemoteConfig::internalUser); // Orbiliq: no raw diagnostic sinks."))
    handler = checkout / "app/src/main/java/org/thoughtcrime/securesms/util/SignalUncaughtExceptionHandler.java"
    text = handler.read_text()
    replacements = {
        "exceptionName, e.getMessage(), ExceptionUtil.convertThrowableToString(e)": 'exceptionName, "redacted", "redacted"',
        "originalHandler.uncaughtException(t, ExceptionUtil.joinStackTraceAndMessage(e));":
            'originalHandler.uncaughtException(t, new RuntimeException("Signal companion failure; details redacted"));',
    }
    for old, new in replacements.items():
        if text.count(old) != 1:
            raise RuntimeError("Upstream crash diagnostics changed")
        text = text.replace(old, new)
    handler.write_text(text)

    manifest = checkout / "app/src/main/AndroidManifest.xml"
    text = manifest.read_text()
    replacements = {
        'android:label="@string/app_name"': 'android:label="Orbiliq Signal Companion (experimentell)"',
        'android:allowBackup="true"': 'android:allowBackup="false"',
        '        android:backupAgent=".absbackup.SignalBackupAgent"\n': '',
        '<uses-permission android:name="org.thoughtcrime.securesms.ACCESS_SECRETS" />':
            '<uses-permission android:name="${applicationId}.ACCESS_SECRETS" />',
        '    <application\n': '''    <permission android:name="org.orbiliq.signal.companion.BIND" android:protectionLevel="signature" />
    <queries><package android:name="org.orbiliq.app" /></queries>
    <application
''',
        '    </application>': '''        <provider android:name="org.orbiliq.signal.bridge.BridgeProvider"
            android:authorities="${applicationId}.bridge" android:exported="true"
            android:permission="org.orbiliq.signal.companion.BIND" android:grantUriPermissions="false" />
        <activity android:name="org.orbiliq.signal.bridge.BridgeConsentActivity"
            android:exported="true" android:permission="org.orbiliq.signal.companion.BIND"
            android:excludeFromRecents="true" />
    </application>''',
    }
    for old, new in replacements.items():
        if text.count(old) != 1:
            raise RuntimeError("Upstream manifest schema changed")
        text = text.replace(old, new)
    ET.fromstring(text)  # Validate XML while preserving upstream formatting and comments.
    manifest.write_text(text)
    # Application ID alone does not isolate Android AccountManager authenticators.
    # Runtime account creation already uses BuildConfig.APPLICATION_ID; XML must match.
    for name in ("authenticator.xml", "syncadapter.xml"):
        path = checkout / "app/src/main/res/xml" / name
        text = path.read_text()
        old = 'android:accountType="org.thoughtcrime.securesms"'
        if text.count(old) != 1:
            raise RuntimeError("Upstream Android account schema changed")
        path.write_text(text.replace(old, 'android:accountType="org.orbiliq.signal.companion"'))
    # Keep system contact actions separate from the installed primary client as well.
    contact_files = (
        "app/src/main/AndroidManifest.xml",
        "app/src/main/res/xml/contactsformat.xml",
        "app/src/main/java/org/thoughtcrime/securesms/jobs/SyncSystemContactLinksJob.kt",
        "app/src/main/java/org/thoughtcrime/securesms/webrtc/VoiceCallShare.java",
    )
    for name in contact_files:
        path = checkout / name
        text = path.read_text()
        old = "vnd.org.thoughtcrime.securesms."
        if old not in text:
            raise RuntimeError("Upstream system contact MIME schema changed")
        path.write_text(text.replace(old, "vnd.org.orbiliq.signal.companion."))
    java = checkout / "app/src/main/java"
    shutil.copytree(HERE / "overlay/java", java, dirs_exist_ok=True)
    common = ROOT / "core/provider-signal/src/main/kotlin/org/orbiliq/core/provider/signal/SignalCompanion.kt"
    dest = java / "org/orbiliq/core/provider/signal/SignalCompanion.kt"
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(common, dest)
    # These types use only Kotlin + Flow, already part of the upstream client.
    (checkout / "ORBILIQ-COMPANION.md").write_text(
        f"# Orbiliq Signal Companion prototype\n\nUpstream: {REPOSITORY}\nRevision: {REVISION}\n\n"
        "Modified Signal client retains upstream AGPL licensing; independent Orbiliq additions are MIT.\n"
        "Shared independently authored contract: MIT, see ORBILIQ-MIT-LICENSE.\n"
        "Keep upstream LICENSE and all source/notice obligations.\n"
        "Status/linking only; no Orbiliq message transport. Not account-tested.\n"
    )
    shutil.copyfile(ROOT / "LICENSE", checkout / "ORBILIQ-MIT-LICENSE")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "build/signal-companion/source")
    parser.add_argument("--source-cache", type=Path, help="Optional local Git clone; exact revision is still enforced")
    args = parser.parse_args()
    dest = args.output.resolve()
    if dest.exists():
        parser.error("Output already exists; select a new empty destination")
    dest.parent.mkdir(parents=True, exist_ok=True)
    if args.source_cache:
        run("git", "clone", "--no-hardlinks", "--no-checkout", str(args.source_cache.resolve()), str(dest))
    else:
        run("git", "init", str(dest))
        run("git", "remote", "add", "origin", REPOSITORY, cwd=dest)
        run("git", "fetch", "--depth=1", "origin", REVISION, cwd=dest)
    run("git", "checkout", "--detach", REVISION, cwd=dest)
    configure(dest)
    print(f"Prepared separate AGPL companion source: {dest}")
    print("Set sdk.dir in its local.properties, then use JDK 21:")
    print("./gradlew --max-workers=1 :Signal-Android:assembleGithubProdSpinner")


if __name__ == "__main__":
    main()
