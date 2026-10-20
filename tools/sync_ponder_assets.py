#!/usr/bin/env python3
"""Copy exact pinned Ponder/Create scene resources; never includes dependency runtime classes."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
import zipfile

PONDER_SOURCE_SHA256 = "4a89eeddecc9488be50831d51a5dafbe6000ca39d0a369a7cc94ad701f8b3af0"
CREATE_COMMIT = "ac0c444d9828da3453ae8cc65338e8de063286fb"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def synchronize(source_root, fabric_root, ponder_source):
    archive_bytes = ponder_source.read_bytes()
    if sha(archive_bytes) != PONDER_SOURCE_SHA256:
        raise ValueError("Expected the exact pinned Ponder 1.0.82+mc1.21.1 source archive")
    destination = fabric_root / "src/main/resources"
    inputs = {}
    pinned = subprocess.run(
        ["git", "-C", str(source_root), "ls-tree", "-r", "-z", CREATE_COMMIT,
         "--", "src/main/resources/assets/create/ponder"],
        check=True, capture_output=True).stdout
    git_inputs = {}
    for record in pinned.split(b"\0"):
        if not record:
            continue
        info, path = record.split(b"\t", 1)
        mode, kind, blob = info.decode().split()
        if kind != "blob" or mode != "100644":
            raise ValueError("Unexpected pinned Create asset type")
        git_inputs[path.decode()] = blob
    if len(git_inputs) != 178:
        raise ValueError("Pinned Create commit must contain exactly 178 actual Ponder structures")
    for source in sorted((source_root / "src/main/resources/assets/create/ponder").rglob("*.nbt")):
        relative = source.relative_to(source_root / "src/main/resources")
        data = source.read_bytes()
        git_path = str(source.relative_to(source_root))
        blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        if git_inputs.pop(git_path, None) != blob:
            raise ValueError("Create scene resource differs from pinned source: " + git_path)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        inputs[str(relative)] = {"source": "Create " + CREATE_COMMIT, "sha256": sha(data)}
    if git_inputs or len(inputs) != 178:
        raise ValueError("Pinned Create source must contain exactly 178 actual Ponder structure assets")
    with zipfile.ZipFile(ponder_source) as archive:
        for name in sorted(archive.namelist()):
            if not name.startswith("assets/ponder/") or name.endswith("/"):
                continue
            data = archive.read(name)
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            inputs[name] = {"source": "Ponder 1.0.82+mc1.21.1 sources", "sha256": sha(data)}
    manifest = {
        "createCommit": CREATE_COMMIT,
        "ponderArtifact": "net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1:sources",
        "ponderSourceSHA256": PONDER_SOURCE_SHA256,
        "license": "MIT; licenses/Ponder-LICENSE (Ponder) and project Create license (Create scenes)",
        "resources": inputs,
    }
    report = fabric_root / "build/reports/porting/ponder-assets-source.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(manifest, indent=2) + "\n")
    return len(inputs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True, help="Checkout of the pinned upstream Create source")
    parser.add_argument("--fabric-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--ponder-source", type=Path)
    args = parser.parse_args()
    source = args.ponder_source
    if source is None:
        cache = Path.home() / ".gradle/caches/modules-2/files-2.1/net.createmod.ponder/ponder-neoforge/1.0.82+mc1.21.1"
        candidates = list(cache.rglob("ponder-neoforge-1.0.82+mc1.21.1-sources.jar"))
        if len(candidates) != 1:
            parser.error("Provide --ponder-source with the exact pinned source archive")
        source = candidates[0]
    print(synchronize(args.source_root.resolve(), args.fabric_root.resolve(), source.resolve()))
