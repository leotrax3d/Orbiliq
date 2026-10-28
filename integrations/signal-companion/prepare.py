#!/usr/bin/env python3
"""Create a separate, pinned AGPL Signal companion source checkout. Never patches Orbiliq.

The generated tree keeps upstream Git history, license, source and dependency verification.
It is a research build, not an audited release or a complete messaging integration.
"""
import argparse
import hashlib
import json
import re
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
SECURITY_FILES = (
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/xml/authenticator.xml",
    "app/src/main/res/xml/syncadapter.xml",
    "feature/registration/src/main/java/org/signal/registration/RegistrationActivity.kt",
    "feature/registration/src/main/java/org/signal/registration/RegistrationViewModel.kt",
    "feature/registration/src/main/java/org/signal/registration/CompanionRegistrationPolicy.kt",
    "lib/network/src/main/java/org/signal/network/api/RegistrationApiV2.kt",
    "lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/registration/RegistrationApi.kt",
    "app/src/main/java/org/orbiliq/signal/bridge/BridgeMessaging.kt",
    "app/src/main/java/org/orbiliq/signal/bridge/BridgeAccess.kt",
    "app/src/main/java/org/orbiliq/signal/bridge/BridgeProvider.kt",
    "app/src/main/java/org/orbiliq/signal/bridge/BridgeConsentActivity.kt",
    "app/src/main/java/org/orbiliq/core/provider/signal/SignalMessaging.kt",
    "app/src/main/java/org/thoughtcrime/securesms/database/MessageTable.kt",
)


def security_policy_digest():
    paths = [HERE / "prepare.py", *sorted((HERE / "overlay").rglob("*.kt")),
             ROOT / "core/provider-signal/src/main/kotlin/org/orbiliq/core/provider/signal/SignalCompanion.kt",
             ROOT / "core/provider-signal/src/main/kotlin/org/orbiliq/core/provider/signal/SignalMessaging.kt"]
    digest = hashlib.sha256()
    for path in paths:
        digest.update(str(path.relative_to(ROOT)).replace("\\", "/").encode())
        digest.update(b"\0")
        digest.update(path.read_text(encoding="utf-8").encode("utf-8"))
        digest.update(b"\0")
    return digest.hexdigest()


def verify_security_manifest(checkout: Path):
    """Reject stale/partially patched source, including old linking guards.

    This is a consistency check, not a substitute for code review or APK signing.
    """
    manifest = json.loads((checkout / "ORBILIQ-SECURITY.json").read_text())
    if manifest.get("version") != 2 or manifest.get("revision") != REVISION:
        raise RuntimeError("Regenerate companion source: obsolete security generation")
    if manifest.get("policyDigest") != security_policy_digest():
        raise RuntimeError("Regenerate companion source: generator policy changed")
    expected = {name: hashlib.sha256((checkout / name).read_bytes()).hexdigest() for name in SECURITY_FILES}
    if manifest.get("files") != expected:
        raise RuntimeError("Regenerate companion source: security files changed")


def run(*args, cwd=None):
    return subprocess.check_output(args, cwd=cwd, text=True).strip()


def block_primary_registration(checkout: Path):
    """Remove primary-registration HTTP operations, including legacy entry points."""
    targets = [
        ("lib/network/src/main/java/org/signal/network/api/RegistrationApiV2.kt", "RequestResult",
         ["createVerificationSession", "requestVerificationCode", "submitVerificationCode", "registerAccount"]),
        ("lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/registration/RegistrationApi.kt", "NetworkResult",
         ["createRegistrationSession", "requestSmsVerificationCode", "verifyAccount", "registerAccount"]),
    ]
    for relative, result, methods in targets:
        path = checkout / relative
        text = path.read_text()
        for method in methods:
            pattern = r"(  (?:suspend )?fun " + method + r"\([\s\S]*?\): [^{]+\{)[\s\S]*?\n  }"
            replacement = r'\1\n    return ' + result + '.ApplicationError(UnsupportedOperationException("Companion supports linked devices only"))\n  }'
            text, count = re.subn(pattern, replacement, text)
            if count != 1:
                raise RuntimeError("Upstream primary-registration boundary changed: " + method)
        path.write_text(text)

    activity = checkout / "feature/registration/src/main/java/org/signal/registration/RegistrationActivity.kt"
    text = activity.read_text()
    old = "val startDestination = IntentCompat.getParcelableExtra(intent, START_DESTINATION_EXTRA, RegistrationRoute::class.java)"
    if text.count(old) != 1:
        raise RuntimeError("Upstream registration entry changed")
    activity.write_text(text.replace(old, "val startDestination = RegistrationRoute.LinkAccount(showCreateAccount = false) // Orbiliq: every launcher enters linking."))

    viewmodel = checkout / "feature/registration/src/main/java/org/signal/registration/RegistrationViewModel.kt"
    text = viewmodel.read_text()
    old = "  init {\n    if (startDestination != null) {"
    new = """  init {
    if (_state.value.backStack.isEmpty() || _state.value.backStack.any { !CompanionRegistrationPolicy.allows(it) } || _state.value.sessionMetadata != null) {
      _state.value = RegistrationFlowState(backStack = listOf(RegistrationRoute.LinkAccount(showCreateAccount = false)), isRestoringNavigationState = false)
    }
    if (startDestination != null) {"""
    if text.count(old) != 1:
        raise RuntimeError("Upstream registration restoration changed")
    text = text.replace(old, new)
    old = "  private fun applyNavigationToScreenEvent(inputState: RegistrationFlowState, event: RegistrationFlowEvent.NavigateToScreen): RegistrationFlowState {\n"
    if text.count(old) != 1:
        raise RuntimeError("Upstream registration navigation changed")
    text = text.replace(old, old + "    if (!CompanionRegistrationPolicy.allows(event.route)) return inputState\n")
    old = "  private fun applyNavigateBackToScreenEvent(inputState: RegistrationFlowState, event: RegistrationFlowEvent.NavigateBackToScreen): RegistrationFlowState {\n"
    if text.count(old) != 1:
        raise RuntimeError("Upstream registration back navigation changed")
    text = text.replace(old, old + "    if (!CompanionRegistrationPolicy.allows(event.route)) return inputState\n")
    # Persisted flow state is loaded asynchronously after init. Guard it BEFORE
    # validateRestoredState can issue any legacy verification-session request.
    old = "  private suspend fun validateRestoredState(state: RegistrationFlowState): RegistrationFlowState {\n"
    if text.count(old) != 1:
        raise RuntimeError("Upstream registration restoration validation changed")
    # Replace the complete legacy verifier: linked provisioning has no primary
    # verification session. Keeping its now-unreachable code breaks Kotlin's
    # smart-cast/type inference and could reintroduce primary network validation.
    pattern = re.escape(old) + r"[\s\S]*?\n  }"
    replacement = old + """    return if (state.backStack.isEmpty() || state.backStack.any { !CompanionRegistrationPolicy.allows(it) } || state.sessionMetadata != null) {
      RegistrationFlowState(backStack = listOf(RegistrationRoute.LinkAccount(showCreateAccount = false)), isRestoringNavigationState = false)
    } else {
      state
    }
  }"""
    text, count = re.subn(pattern, lambda _: replacement, text)
    if count != 1:
        raise RuntimeError("Upstream registration restoration body changed")
    old = "          _state.value = RegistrationFlowState(\n            preExistingRegistrationData = repository.getPreExistingRegistrationData(),"
    if text.count(old) != 1:
        raise RuntimeError("Upstream fresh registration state changed")
    text = text.replace(old, "          _state.value = RegistrationFlowState(\n            backStack = listOf(RegistrationRoute.LinkAccount(showCreateAccount = false)),\n            preExistingRegistrationData = repository.getPreExistingRegistrationData(),")
    text = text.replace("is RegistrationFlowEvent.ResetState -> RegistrationFlowState(isRestoringNavigationState = false)",
                        "is RegistrationFlowEvent.ResetState -> RegistrationFlowState(backStack = listOf(RegistrationRoute.LinkAccount(showCreateAccount = false)), isRestoringNavigationState = false)")
    viewmodel.write_text(text)


def configure(checkout: Path):
    if run("git", "rev-parse", "HEAD", cwd=checkout) != REVISION:
        raise RuntimeError("Unexpected upstream revision")
    block_primary_registration(checkout)
    message_table = checkout / "app/src/main/java/org/thoughtcrime/securesms/database/MessageTable.kt"
    table_text = message_table.read_text()
    anchor = "  fun getConversation(threadId: Long): Cursor {"
    if table_text.count(anchor) != 1:
        raise RuntimeError("Upstream message pagination boundary changed")
    page_method = '''  // Orbiliq: stable keyset pagination; no mutable OFFSET across Binder calls.
  fun getOrbiliqConversationPage(threadId: Long, beforeId: Long, pageSize: Int): Cursor {
    require(threadId > 0 && beforeId > 0 && pageSize in 1..50)
    return readableDatabase
      .select(*MMS_PROJECTION)
      .from(TABLE_NAME)
      .where("$THREAD_ID = ? AND $ID < ? AND $STORY_TYPE = 0 AND $PARENT_STORY_ID <= 0 AND $SCHEDULED_DATE = -1 AND $LATEST_REVISION_ID IS NULL", threadId, beforeId)
      .orderBy("$ID DESC")
      .limit(pageSize)
      .run()
  }

'''
    message_table.write_text(table_text.replace(anchor, page_method + anchor))
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
    shutil.copytree(HERE / "overlay/registration", checkout / "feature/registration/src/main/java", dirs_exist_ok=True)
    shutil.copytree(HERE / "overlay/network-tests", checkout / "lib/network/src/test/java", dirs_exist_ok=True)
    common = ROOT / "core/provider-signal/src/main/kotlin/org/orbiliq/core/provider/signal/SignalCompanion.kt"
    dest = java / "org/orbiliq/core/provider/signal/SignalCompanion.kt"
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(common, dest)
    shutil.copyfile(common.with_name("SignalMessaging.kt"), dest.with_name("SignalMessaging.kt"))
    # These types use only Kotlin + Flow, already part of the upstream client.
    (checkout / "ORBILIQ-COMPANION.md").write_text(
        f"# Orbiliq Signal Companion prototype\n\nUpstream: {REPOSITORY}\nRevision: {REVISION}\n\n"
        "Modified Signal client retains upstream AGPL licensing; independent Orbiliq additions are MIT.\n"
        "Shared independently authored contract: MIT, see ORBILIQ-MIT-LICENSE.\n"
        "Keep upstream LICENSE and all source/notice obligations.\n"
        "Status/linking only; no Orbiliq message transport. Not account-tested.\n"
    )
    shutil.copyfile(ROOT / "LICENSE", checkout / "ORBILIQ-MIT-LICENSE")
    (checkout / "ORBILIQ-SECURITY.json").write_text(json.dumps({
        "version": 2, "revision": REVISION, "policyDigest": security_policy_digest(),
        "files": {name: hashlib.sha256((checkout / name).read_bytes()).hexdigest() for name in SECURITY_FILES},
    }, indent=2) + "\n")


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
    # Upstream screenshot fixture paths exceed MAX_PATH on Windows. Keep the
    # setting local to this invocation, without changing the user's Git config.
    run("git", "-c", "core.longpaths=true", "checkout", "--detach", REVISION, cwd=dest)
    configure(dest)
    print(f"Prepared separate AGPL companion source: {dest}")
    print("Set sdk.dir in its local.properties, then use JDK 21:")
    print("./gradlew --max-workers=1 :Signal-Android:assembleGithubProdSpinner")


if __name__ == "__main__":
    main()
