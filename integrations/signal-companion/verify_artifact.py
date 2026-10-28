#!/usr/bin/env python3
"""Validate a CI corresponding-source archive before selecting/signing its APK.

Consistency check against this checkout's current generator policy, not a signing
certificate or a substitute for verifying the trusted CI producer and APK manifest.
Only reads a small whitelist in one archive pass; never extracts archive paths.
"""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile

from prepare import REVISION, SECURITY_FILES, security_policy_digest


def verify_archive(path: Path):
    wanted = set(SECURITY_FILES) | {"ORBILIQ-SECURITY.json"}
    found = {}
    with tarfile.open(path, "r|gz") as archive:
        for member in archive:
            name = member.name.removeprefix("./")
            if name not in wanted:
                continue
            if name in found or not member.isfile() or not 0 < member.size <= 8 * 1024 * 1024:
                raise ValueError("Invalid or duplicate security archive member")
            stream = archive.extractfile(member)
            if stream is None:
                raise ValueError("Missing security file contents")
            found[name] = stream.read(8 * 1024 * 1024 + 1)
    if found.keys() != wanted:
        raise ValueError("Corresponding source is incomplete")
    manifest = json.loads(found.pop("ORBILIQ-SECURITY.json"))
    if manifest.get("revision") != REVISION or manifest.get("version") != 2:
        raise ValueError("Unexpected native revision or generation")
    if manifest.get("policyDigest") != security_policy_digest():
        raise ValueError("Obsolete companion policy; do not install this artifact")
    actual = {name: hashlib.sha256(data).hexdigest() for name, data in found.items()}
    if manifest.get("files") != actual:
        raise ValueError("Corresponding security source hashes differ")
    return manifest["policyDigest"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        verify_archive(args.archive)
    except (OSError, ValueError, tarfile.TarError):
        parser.error("Companion source verification failed; use a fresh matching build")
    print("Corresponding security source matches the current generator policy.")
