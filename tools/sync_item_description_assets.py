#!/usr/bin/env python3
"""Merge the genuine pinned tooltip translation closure without replacing native additions."""
from __future__ import annotations
import json
from pathlib import Path


def is_tooltip_key(key: str) -> bool:
    return key.startswith("create.tooltip.") or (
        key.startswith(("item.create.", "block.create.")) and ".tooltip." in key
    )


def generate(output: Path, upstream: Path) -> list[str]:
    sources: dict[str, Path] = {}
    for root in (upstream / "src/main/resources/assets/create/lang",
                 upstream / "src/generated/resources/assets/create/lang"):
        for source in sorted(root.glob("*.json")):
            sources.setdefault(source.name, source)
    changed = []
    for name, source in sources.items():
        values = {key: value for key, value in json.loads(source.read_text()).items() if is_tooltip_key(key)}
        if not values:
            continue
        relative = "assets/create/lang/" + name
        destination = output / relative
        current = json.loads(destination.read_text()) if destination.is_file() else {}
        merged = {**values, **current}
        if merged == current:
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n")
        changed.append(relative)
    return changed
