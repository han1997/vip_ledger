"""Explicit release version bumps; ordinary Gradle builds never change versions."""

from __future__ import annotations

import argparse
from datetime import date
from pathlib import Path
import re
import tempfile


MAX_VERSION_CODE = 2_100_000_000
VERSION_PATTERN = r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"


def read_version(text: str) -> tuple[str, int]:
    values = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key in values or key not in ("versionName", "versionCode"):
            raise ValueError("Expected unique versionName and versionCode properties")
        values[key] = value
    name = values.get("versionName", "")
    code = values.get("versionCode", "")
    if not re.fullmatch(VERSION_PATTERN, name):
        raise ValueError("versionName must be major.minor.patch without leading zeros")
    if not re.fullmatch(r"[1-9][0-9]*", code) or int(code) > MAX_VERSION_CODE:
        raise ValueError("versionCode must be in 1..2100000000")
    return name, int(code)


def replace_text(path: Path, content: str) -> None:
    """Stage in the destination directory so replacement stays on one filesystem."""
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", newline="\n", dir=path.parent, delete=False,
    ) as output:
        temporary = Path(output.name)
        try:
            output.write(content)
        except BaseException:
            output.close()
            temporary.unlink(missing_ok=True)
            raise
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def bump(root: Path, part: str, message: str) -> tuple[str, int]:
    message = message.strip()
    if not message or "\n" in message or "\r" in message:
        raise ValueError("Provide a non-empty, single-line update message")
    if part not in ("patch", "minor", "major"):
        raise ValueError("part must be patch, minor or major")
    version_file = root / "version.properties"
    changelog_file = root / "CHANGELOG.md"
    original_version = version_file.read_bytes().decode("utf-8")
    name, code = read_version(original_version)
    if code == MAX_VERSION_CODE:
        raise ValueError("versionCode has reached the Android limit")
    major, minor, patch = map(int, name.split("."))
    if part == "major":
        major, minor, patch = major + 1, 0, 0
    elif part == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    name, code = f"{major}.{minor}.{patch}", code + 1
    changelog = changelog_file.read_text(encoding="utf-8")
    heading, separator, history = changelog.partition("\n")
    if heading != "# 更新日志" or not separator:
        raise ValueError("CHANGELOG.md must start with '# 更新日志'")
    entry = f"## {name} ({code}) — {date.today().isoformat()}\n\n- {message}\n\n"
    updated_log = heading + "\n\n" + entry + history.lstrip("\n")
    replace_text(version_file, f"versionName={name}\nversionCode={code}\n")
    try:
        replace_text(changelog_file, updated_log)
    except OSError:
        replace_text(version_file, original_version)
        raise
    return name, code


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    command = commands.add_parser("bump", help="Bump once per delivered application change")
    command.add_argument("--part", choices=("patch", "minor", "major"), default="patch")
    command.add_argument("--message", required=True)
    args = parser.parse_args()
    try:
        name, code = bump(Path(__file__).resolve().parents[1], args.part, args.message)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Version update failed: {error}\n")
    print(f"Updated version to {name} ({code})")


if __name__ == "__main__":
    main()
