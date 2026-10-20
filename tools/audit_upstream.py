#!/usr/bin/env python3
"""Inventory the upstream migration surface without calling a file a completed port."""

from __future__ import annotations

from collections import Counter
import argparse
import json
from pathlib import Path
import re

PROJECT = Path(__file__).resolve().parents[1]
PREFIX = "com/simibubi/create/"
DEPENDENCIES = {
    "neoforge": "net.neoforged.",
    "flywheel": "dev.engine_room.flywheel.",
    "catnip_ponder": "net.createmod.",
    "registrate": "com.tterrag.registrate.",
    "lwjgl": "org.lwjgl.",
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, required=True, help='Checkout of the pinned upstream Create source')
    args = parser.parse_args()
    source = args.source_root.resolve() / 'src/main/java'
    if not source.is_dir():
        parser.error('--source-root must contain the upstream Java sources')
    files = []
    dependency_counts = Counter()
    subsystem_counts = Counter()
    total_lines = 0
    for path in sorted(source.rglob("*.java")):
        text = path.read_text()
        imports = re.findall(r"^import\s+(?:static\s+)?([^;]+);", text, re.MULTILINE)
        used = [name for name, prefix in DEPENDENCIES.items() if any(i.startswith(prefix) for i in imports)]
        relative = path.relative_to(source).as_posix()
        parts = relative.removeprefix(PREFIX).split("/")
        subsystem = "/".join(parts[:2]) if len(parts) > 2 else parts[0] if len(parts) > 1 else "root"
        lines = len(text.splitlines())
        total_lines += lines
        dependency_counts.update(used)
        subsystem_counts.update([subsystem])
        files.append({"path": relative, "lines": lines, "subsystem": subsystem, "dependencies": used})
    report = {
        "source_tag": "mc1.21.1-6.0.10",
        "source_commit": "ac0c444d9828da3453ae8cc65338e8de063286fb",
        "java_files": len(files),
        "source_lines": total_lines,
        "files_importing_dependency": dict(dependency_counts),
        "subsystems": dict(sorted(subsystem_counts.items())),
        "files": files,
    }
    output = PROJECT / "build/reports/porting/upstream-inventory.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Upstream: {len(files)} Java files / {total_lines} lines")
    for name, count in dependency_counts.items():
        print(f"  {name}: {count} files import this API")
    print(f"Inventory: {output.relative_to(PROJECT)}")


if __name__ == "__main__":
    main()
