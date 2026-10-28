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
    "orbiliq/orbiliq-provider-signal/src/main/java/org/orbiliq/core/provider/signal/SignalMessaging.kt",
    "orbiliq/orbiliq-host/src/main/AndroidManifest.xml",
    "app/src/main/java/org/orbiliq/signal/bridge/InProcessHost.kt",
    "app/src/main/java/org/thoughtcrime/securesms/database/MessageTable.kt",
    "lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/messages/SignalServiceMessageLimits.kt",
    "feature/registration/src/main/java/org/signal/registration/screens/linkaccount/LinkAccountScreen.kt",
)


def security_policy_digest():
    paths = [HERE / "prepare.py", *sorted((HERE / "overlay").rglob("*.kt")),
             ROOT / "core/provider-signal/src/main/kotlin/org/orbiliq/core/provider/signal/SignalCompanion.kt",
             ROOT / "core/provider-signal/src/main/kotlin/org/orbiliq/core/provider/signal/SignalMessaging.kt",
             *sorted(p for p in (HERE / "host").rglob("*") if p.is_file()),
             HERE / "verification-additions.xml",
             *sorted(p for p in (HERE / "host-tests").rglob("*") if p.is_file()),
             *(ROOT / "app/src/main/kotlin/org/orbiliq/app" / name for name in ("signal/AndroidSignalMessaging.kt",
               "signal/SignalMessengerRepository.kt", "storage/AndroidLocalStorage.kt"))]
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


def validate_wire_limit(checkout: Path):
    path = checkout / "lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/messages/SignalServiceMessageLimits.kt"
    native = re.search(r"MAX_INLINE_BODY_SIZE_BYTES: Int = (\d+)\.kibiBytes", path.read_text())
    shared = ROOT / "core/provider-signal/src/main/kotlin/org/orbiliq/core/provider/signal/SignalMessaging.kt"
    declared = re.search(r"const val MAX_TEXT_BYTES = (\d+)", shared.read_text())
    if not native or not declared or int(native[1]) * 1024 != int(declared[1]):
        raise RuntimeError("Upstream inline text wire limit changed; review provider constraints")


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


LINK_ACCOUNT = "feature/registration/src/main/java/org/signal/registration/screens/linkaccount/"
SHARE_LINK_COMPOSABLE = """

// Orbiliq: one-phone linking. Shares ONLY the current, short-lived provisioning QR on an explicit tap.
// The link carries an ephemeral public key; the private key and all account keys never leave this app.
@Composable
private fun OrbiliqShareLink(state: LinkAccountScreenState) {
  val url = state.orbiliqLinkUrl
  val qr = (state.qrCodeState as? QrState.Loaded)?.qrCodeData
  if (url == null || qr == null || !url.startsWith("sgnl://linkdevice?")) return
  val context = androidx.compose.ui.platform.LocalContext.current
  var confirm by remember { mutableStateOf(false) }
  val dir = java.io.File(context.externalCacheDir ?: return, "orbiliq-link") // Only path covered by the FileProvider.
  androidx.compose.runtime.DisposableEffect(url) { onDispose { dir.deleteRecursively() } }
  androidx.compose.material3.OutlinedButton(onClick = { confirm = true }) {
    androidx.compose.material3.Text("Kein zweites Handy? QR-Code teilen")
  }
  if (confirm) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { confirm = false },
      title = { androidx.compose.material3.Text("QR-Code auf anderem Bildschirm zeigen") },
      text = {
        androidx.compose.material3.Text(
          "Sende den Code nur an dich selbst (z. B. Laptop, Tablet) und scanne ihn dort mit Signal auf deinem Handy: " +
            "Einstellungen \\u2192 Verkn\\u00fcpfte Ger\\u00e4te \\u2192 Ger\\u00e4t verkn\\u00fcpfen. " +
            "Er gilt nur, solange dieser Bildschirm offen ist. Scanne niemals Codes, die dir jemand anderes schickt."
        )
      },
      confirmButton = {
        androidx.compose.material3.TextButton(onClick = {
          confirm = false
          dir.deleteRecursively(); dir.mkdirs()
          val scale = 16; val quiet = 4
          val size = (maxOf(qr.width, qr.height) + 2 * quiet) * scale
          val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
          bitmap.eraseColor(android.graphics.Color.WHITE)
          for (y in 0 until qr.height) for (x in 0 until qr.width) if (qr.get(androidx.compose.ui.unit.IntOffset(x, y))) {
            for (dy in 0 until scale) for (dx in 0 until scale)
              bitmap.setPixel((x + quiet) * scale + dx, (y + quiet) * scale + dy, android.graphics.Color.BLACK)
          }
          val file = java.io.File(dir, "signal-verknuepfung.png")
          file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
          bitmap.recycle()
          val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
          val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, url)
            clipData = android.content.ClipData.newRawUri(null, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          context.startActivity(android.content.Intent.createChooser(send, "QR-Code teilen"))
        }) { androidx.compose.material3.Text("Teilen") }
      },
      dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { androidx.compose.material3.Text("Abbrechen") } }
    )
  }
}
"""


def add_link_sharing(checkout: Path):
    edits = (
        ("LinkAccountScreenState.kt", "  val showCreateAccount: Boolean = true\n)",
         "  val showCreateAccount: Boolean = true,\n  val orbiliqLinkUrl: String? = null\n)"),
        ("LinkAccountViewModel.kt",
         "it.copy(qrCodeState = QrState.Loaded(qrCodeData = QrCodeData.forData(data = event.url, supportIconOverlay = false)))",
         "it.copy(qrCodeState = QrState.Loaded(qrCodeData = QrCodeData.forData(data = event.url, supportIconOverlay = false)), orbiliqLinkUrl = event.url)"),
        ("LinkAccountScreen.kt",
         "        QrCodeContent(state = state, onEvent = onEvent, isPhone = isPhone, expandButtonVisible = expandButtonVisible)\n",
         "        QrCodeContent(state = state, onEvent = onEvent, isPhone = isPhone, expandButtonVisible = expandButtonVisible)\n\n        OrbiliqShareLink(state)\n"),
    )
    for name, old, new in edits:
        path = checkout / LINK_ACCOUNT / name
        text = path.read_text()
        if text.count(old) != 1:
            raise RuntimeError("Upstream link-account screen changed")
        path.write_text(text.replace(old, new))
    screen = checkout / LINK_ACCOUNT / "LinkAccountScreen.kt"
    screen.write_text(screen.read_text() + SHARE_LINK_COMPOSABLE)
    gradle = checkout / "feature/registration/build.gradle.kts"
    text = gradle.read_text()
    anchor = '  implementation(project(":core:ui"))\n'
    if text.count(anchor) != 1:
        raise RuntimeError("Upstream registration dependencies changed")
    gradle.write_text(text.replace(anchor, anchor + "  implementation(libs.androidx.core.ktx) // Orbiliq: FileProvider for QR sharing\n"))



APP_ID = "org.orbiliq.app"
# Orbiliq modules compiled inside the Signal build (one APK, one process, AGPL-3.0-or-later).
ORBILIQ_MODULES = {
    "orbiliq-model": ("core/model", "jvm", []),
    "orbiliq-domain": ("core/domain", "jvm", ['api(project(":orbiliq-model"))', "api(libs.kotlinx.coroutines.core)"]),
    "orbiliq-provider-api": ("core/provider-api", "jvm", ['api(project(":orbiliq-model"))', "api(libs.kotlinx.coroutines.core)"]),
    "orbiliq-data": ("core/data", "jvm", ['implementation(project(":orbiliq-domain"))', 'api(project(":orbiliq-provider-api"))']),
    "orbiliq-provider-signal": ("core/provider-signal", "jvm", ['api(project(":orbiliq-provider-api"))']),
    "orbiliq-designsystem": ("core/designsystem", "android", []),
    "orbiliq-inbox": ("feature/inbox", "android", ['implementation(project(":orbiliq-designsystem"))', 'implementation(project(":orbiliq-domain"))',
                                                    'implementation(project(":orbiliq-model"))']),
}
HOST_APP_FILES = ("signal/AndroidSignalMessaging.kt", "signal/SignalMessengerRepository.kt", "storage/AndroidLocalStorage.kt")
JVM_BUILD = """// Generated by integrations/signal-companion/prepare.py (Orbiliq, AGPL-3.0-or-later)
plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}
java {
  sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
}
kotlin { jvmToolchain { languageVersion = JavaLanguageVersion.of(libs.versions.kotlinJvmTarget.get()) } }
dependencies {
%s
}
"""
ANDROID_BUILD = """// Generated by integrations/signal-companion/prepare.py (Orbiliq, AGPL-3.0-or-later)
plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}
android {
  namespace = "%s"
  buildFeatures { compose = true }
}
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // Material 3 Expressive APIs are public from 1.5.0; checksums: verification-additions.xml.
  implementation("androidx.compose.material3:material3:1.5.0-beta02")
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
%s
}
"""


TEST_BUILD = """// Generated by integrations/signal-companion/prepare.py (Orbiliq, AGPL-3.0-or-later)
// Opt-in device tests running inside the combined app process (instrumentation of org.orbiliq.app).
plugins { id("com.android.test") }
android {
  namespace = "org.orbiliq.combined.tests"
  compileSdk = libs.versions.compileSdk.get().toInt()
  targetProjectPath = ":Signal-Android"
  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    missingDimensionStrategy("distribution", "github")
    missingDimensionStrategy("environment", "prod")
  }
  buildTypes { create("spinner") { signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += listOf("debug") } }
  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }
}
dependencies {
  implementation(project(":orbiliq-host"))
  implementation(testLibs.androidx.test.runner)
  implementation(testLibs.junit.junit)
  implementation(libs.kotlinx.coroutines.core)
}
"""


def add_orbiliq_app(checkout: Path):
    """Builds the Orbiliq UI and providers into the Signal client as one app."""
    settings = checkout / "settings.gradle.kts"
    text = settings.read_text()
    anchor = 'include(":app")\n'
    if text.count(anchor) != 1:
        raise RuntimeError("Upstream settings schema changed")
    lines = [anchor]
    for name in [*ORBILIQ_MODULES, "orbiliq-host", "orbiliq-tests"]:
        lines.append(f'include(":{name}")\nproject(":{name}").projectDir = file("orbiliq/{name}")\n')
    settings.write_text(text.replace(anchor, "".join(lines)))
    for name, (source, kind, deps) in ORBILIQ_MODULES.items():
        module = checkout / "orbiliq" / name
        src = ROOT / source / "src/main"
        shutil.copytree(src / "kotlin", module / "src/main/java")
        body = "\n".join("  " + d for d in deps)
        if kind == "jvm":
            (module / "build.gradle.kts").write_text(JVM_BUILD % body)
        else:
            namespace = "org.orbiliq." + source.replace("/", ".").replace("core.", "core.").replace("feature.", "feature.")
            (module / "build.gradle.kts").write_text(ANDROID_BUILD % (namespace, body))
            if (src / "res").exists():
                shutil.copytree(src / "res", module / "src/main/res")
            manifest = src / "AndroidManifest.xml"
            if manifest.exists():
                shutil.copyfile(manifest, module / "src/main/AndroidManifest.xml")
    host = checkout / "orbiliq/orbiliq-host"
    shutil.copytree(HERE / "host/kotlin", host / "src/main/java")
    shutil.copytree(HERE / "host/res", host / "src/main/res")
    shutil.copyfile(HERE / "host/AndroidManifest.xml", host / "src/main/AndroidManifest.xml")
    for name in HOST_APP_FILES:
        target = host / "src/main/java/org/orbiliq/app" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / "app/src/main/kotlin/org/orbiliq/app" / name, target)
    tests = checkout / "orbiliq/orbiliq-tests"
    target = tests / "src/main/java/org/orbiliq/app"
    target.mkdir(parents=True)
    for name in ("SignalMessagingIntegrationTest.kt", "SignalTransportDiagnosticTest.kt"):
        shutil.copyfile(ROOT / "app/src/androidTest/kotlin/org/orbiliq/app" / name, target / name)
    shutil.copyfile(HERE / "host-tests/TestHost.kt", target / "TestHost.kt")
    (tests / "build.gradle.kts").write_text(TEST_BUILD)
    host_deps = [f'api(project(":{n}"))' for n in ORBILIQ_MODULES]
    (host / "build.gradle.kts").write_text(ANDROID_BUILD % ("org.orbiliq.host", "\n".join("  " + d for d in host_deps)))
    catalog = checkout / "gradle/libs.versions.toml"
    text = catalog.read_text()
    if text.count('minSdk = "23"') != 1:
        raise RuntimeError("Upstream minSdk changed")
    catalog.write_text(text.replace('minSdk = "23"', 'minSdk = "26"'))  # Orbiliq's own minimum (Android 8).
    verification = checkout / "gradle/verification-metadata.xml"
    text = verification.read_text()
    if text.count("   </components>") != 1:
        raise RuntimeError("Upstream dependency verification schema changed")
    additions = (HERE / "verification-additions.xml").read_text()
    verification.write_text(text.replace("   </components>", additions.split("-->\n", 1)[1] + "   </components>"))
    app_build = checkout / "app/build.gradle.kts"
    text = app_build.read_text()
    anchor = "dependencies {\n"
    if text.count(anchor) < 1:
        raise RuntimeError("Upstream app dependencies changed")
    app_build.write_text(text.replace(anchor, anchor + '  implementation(project(":orbiliq-host"))\n', 1))
    application = checkout / "app/src/main/java/org/thoughtcrime/securesms/ApplicationContext.java"
    text = application.read_text()
    anchor = "    PartAuthorityUris.init(BuildConfig.APPLICATION_ID);\n"
    if text.count(anchor) != 1:
        raise RuntimeError("Upstream application start changed")
    application.write_text(text.replace(anchor, anchor + "    org.orbiliq.signal.bridge.InProcessHost.install(this);\n"))


def configure(checkout: Path):
    if run("git", "rev-parse", "HEAD", cwd=checkout) != REVISION:
        raise RuntimeError("Unexpected upstream revision")
    validate_wire_limit(checkout)
    block_primary_registration(checkout)
    add_link_sharing(checkout)
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
    text = text.replace(needle, needle + f'    applicationId = "{APP_ID}"\n')
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
        'android:label="@string/app_name"': 'android:label="Orbiliq"',
        'android:icon="@mipmap/ic_launcher"': 'android:icon="@mipmap/orbiliq_launcher"',  # No Signal branding.
        'android:allowBackup="true"': 'android:allowBackup="false"',
        '        android:backupAgent=".absbackup.SignalBackupAgent"\n': '',
        '<uses-permission android:name="org.thoughtcrime.securesms.ACCESS_SECRETS" />':
            '<uses-permission android:name="${applicationId}.ACCESS_SECRETS" />',
            }
    for old, new in replacements.items():
        if text.count(old) != 1:
            raise RuntimeError("Upstream manifest schema changed")
        text = text.replace(old, new)
    # One app: Orbiliq is the only launcher; Signal screens stay reachable internally (linking, fallback).
    launcher = '<category android:name="android.intent.category.LAUNCHER" />'
    if launcher not in text:
        raise RuntimeError("Upstream launcher schema changed")
    text = text.replace(launcher, '<category android:name="android.intent.category.DEFAULT" />')
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
        path.write_text(text.replace(old, f'android:accountType="{APP_ID}"'))
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
        path.write_text(text.replace(old, f"vnd.{APP_ID}."))
    java = checkout / "app/src/main/java"
    shutil.copytree(HERE / "overlay/java", java, dirs_exist_ok=True)
    shutil.copytree(HERE / "overlay/registration", checkout / "feature/registration/src/main/java", dirs_exist_ok=True)
    shutil.copytree(HERE / "overlay/network-tests", checkout / "lib/network/src/test/java", dirs_exist_ok=True)
    add_orbiliq_app(checkout)
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
