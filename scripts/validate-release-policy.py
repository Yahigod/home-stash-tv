#!/usr/bin/env python3
"""Fail-closed checks for Home Stash TV release metadata and automation."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SEMVER = re.compile(
    r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?\Z",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        require(bool(separator), f"Invalid property line: {raw_line}")
        result[key.strip()] = value.strip()
    return result


def validate(tag: str | None) -> None:
    release_properties = properties(ROOT / "gradle.properties")
    version_name = release_properties.get("homeStashTvVersionName", "")
    version_code = release_properties.get("homeStashTvVersionCode", "")

    require(bool(SEMVER.fullmatch(version_name)), "Release version name is not semantic.")
    require(version_code.isdecimal() and int(version_code) > 0, "Release version code is invalid.")
    if tag is not None:
        require(tag == f"v{version_name}", "Tag does not match the committed release version.")

    build = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    for name in (
        "HOME_STASH_TV_RELEASE_KEYSTORE_FILE",
        "HOME_STASH_TV_RELEASE_KEYSTORE_PASSWORD",
        "HOME_STASH_TV_RELEASE_KEY_ALIAS",
        "HOME_STASH_TV_RELEASE_KEY_PASSWORD",
    ):
        require(name in build, f"Build is missing production input {name}.")
    require('create("production")' in build, "Production signing config is missing.")
    require(
        'signingConfig = signingConfigs.getByName("production")' in build,
        "Release build is not bound to production signing.",
    )
    require(
        'signingConfig = signingConfigs.getByName("development")' in build,
        "Debug build is not bound to development signing.",
    )

    release_workflow = (ROOT / ".github" / "workflows" / "release.yml").read_text(
        encoding="utf-8",
    )
    for marker in (
        "scripts/validate-release-policy.py --tag",
        'git cat-file -t "refs/tags/$GITHUB_REF_NAME"',
        "git merge-base --is-ancestor",
        "HOME_STASH_TV_RELEASE_KEYSTORE_B64",
        "HOME_STASH_TV_RELEASE_KEYSTORE_PASSWORD",
        "HOME_STASH_TV_RELEASE_KEY_ALIAS",
        "HOME_STASH_TV_RELEASE_KEY_PASSWORD",
        "apksigner",
        "sha256sum",
        "gh release create",
    ):
        require(marker in release_workflow, f"Release workflow is missing {marker}.")
    require(
        "HOME_STASH_TV_DEBUG_" not in release_workflow,
        "Release workflow must not consume development signing secrets.",
    )

    pull_request_workflow = (ROOT / ".github" / "workflows" / "android.yml").read_text(
        encoding="utf-8",
    )
    require("validate-release-policy.py" in pull_request_workflow, "CI omits release policy.")
    require("assembleRelease" in pull_request_workflow, "CI omits the release variant.")

    ignored = (ROOT / ".gitignore").read_text(encoding="utf-8")
    for marker in ("*.jks", "*.keystore", "*.p12", "keystore.properties"):
        require(marker in ignored, f"Signing file ignore rule is missing: {marker}")

    for path in (
        ROOT / "docs" / "RELEASES.md",
        ROOT / "docs" / "INSTALLATION.md",
        ROOT / "docs" / "DEVICE_COMPATIBILITY.md",
    ):
        require(path.is_file(), f"Required release document is missing: {path.name}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag")
    args = parser.parse_args()
    try:
        validate(args.tag)
    except (OSError, ValueError) as error:
        print(f"release_policy=invalid reason={error}", file=sys.stderr)
        return 1
    print("release_policy=valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
