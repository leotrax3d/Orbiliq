"""Credential-free generator integration tests against the exact upstream revision.

Set SIGNAL_UPSTREAM_SOURCE to an existing Git checkout containing REVISION.
Tests extract pristine tracked files, never modify that checkout or use accounts.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from prepare import REVISION, SECURITY_FILES, block_primary_registration, verify_security_manifest

SOURCE = os.environ.get("SIGNAL_UPSTREAM_SOURCE")
REGISTRATION = "feature/registration/src/main/java/org/signal/registration/"
INPUTS = (SECURITY_FILES[6], SECURITY_FILES[7], REGISTRATION + "RegistrationActivity.kt",
          REGISTRATION + "RegistrationViewModel.kt")


@unittest.skipUnless(SOURCE, "Set SIGNAL_UPSTREAM_SOURCE for pinned upstream integration tests")
class RegistrationGeneratorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in INPUTS:
            content = subprocess.check_output(["git", "show", f"{REVISION}:{name}"], cwd=SOURCE)
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)

    def test_primary_http_operations_are_removed_but_secondary_linking_survives(self):
        original = (self.root / INPUTS[1]).read_text()
        secondary = original[original.index("  fun registerAsSecondaryDevice("):]
        block_primary_registration(self.root)
        for name in INPUTS[:2]:
            text = (self.root / name).read_text()
            self.assertEqual(4, text.count("Companion supports linked devices only"))
        self.assertTrue((self.root / INPUTS[1]).read_text().endswith(secondary))

    def test_restoration_is_guarded_before_network_validation(self):
        block_primary_registration(self.root)
        text = (self.root / INPUTS[3]).read_text()
        validation = text[text.index("  private suspend fun validateRestoredState("):]
        self.assertIn("!CompanionRegistrationPolicy.allows(it)", validation)
        self.assertNotIn("repository.validateSession(", validation)
        self.assertIn("state.backStack.isEmpty()", validation)
        self.assertIn("state.sessionMetadata != null", validation)
        for method in ("applyNavigationToScreenEvent", "applyNavigateBackToScreenEvent"):
            body = text[text.index("  private fun " + method):]
            self.assertLess(body.index("!CompanionRegistrationPolicy.allows(event.route)"),
                            body.index("inputState.backStack"))
        self.assertIn("RegistrationFlowState(\n            backStack = listOf(RegistrationRoute.LinkAccount(showCreateAccount = false)),", text)

    def test_saved_state_and_reset_cannot_restore_primary_flow(self):
        block_primary_registration(self.root)
        text = (self.root / INPUTS[3]).read_text()
        initial = text[text.index("  init {"):text.index("    if (startDestination != null)")]
        self.assertIn("_state.value.backStack.isEmpty()", initial)
        self.assertIn("_state.value.sessionMetadata != null", initial)
        self.assertIn("RegistrationRoute.LinkAccount(showCreateAccount = false)", initial)
        self.assertIn("is RegistrationFlowEvent.ResetState -> RegistrationFlowState(backStack = listOf(RegistrationRoute.LinkAccount(showCreateAccount = false))", text)

    def test_upstream_drift_fails_closed(self):
        path = self.root / INPUTS[3]
        path.write_text(path.read_text().replace("private fun applyNavigateBackToScreenEvent(", "private fun changedUpstream("))
        with self.assertRaisesRegex(RuntimeError, "back navigation changed"):
            block_primary_registration(self.root)


class SourceManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in SECURITY_FILES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("synthetic security fixture")
        self.manifest = {"version": 2, "revision": REVISION, "files": {
            name: hashlib.sha256((self.root / name).read_bytes()).hexdigest() for name in SECURITY_FILES}}
        self.save()

    def save(self):
        (self.root / "ORBILIQ-SECURITY.json").write_text(json.dumps(self.manifest))

    def test_current_complete_source_is_accepted(self):
        verify_security_manifest(self.root)

    def test_changed_security_file_is_rejected(self):
        (self.root / SECURITY_FILES[4]).write_text("stale navigation")
        with self.assertRaises(RuntimeError):
            verify_security_manifest(self.root)

    def test_obsolete_generation_and_missing_entries_are_rejected(self):
        self.manifest["version"] = 1
        self.save()
        with self.assertRaises(RuntimeError):
            verify_security_manifest(self.root)
        self.manifest["version"] = 2
        self.manifest["files"].pop(SECURITY_FILES[0])
        self.save()
        with self.assertRaises(RuntimeError):
            verify_security_manifest(self.root)


if __name__ == "__main__":
    unittest.main()
