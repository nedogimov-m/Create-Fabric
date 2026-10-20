#!/usr/bin/env python3
"""Check the built development jar's target, entrypoints and platform references."""

from pathlib import Path
import json
import hashlib
from io import BytesIO
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
FORBIDDEN = (b"net/neoforged/", b"dev/engine_room/flywheel/", b"net/caffeinemc/mods/sodium/",
             b"me/jellysquid/mods/sodium/", b"org/lwjgl/opengl/", b"com/mojang/blaze3d/opengl/")

# Exact reviewed NightConfig3.8.3 payloads; Loom may add only Fabric metadata.
# Hash sorted uncompressed names+contents, so ZIP timestamps do not affect trust.
APPROVED_LIBRARIES = {'core-3.8.3.jar': '09ed36c164d67f7fa7f86e69ccc4d52bc1c1d1026a7772cc90d4eea29106dd7a', 'toml-3.8.3.jar': '5484b91715dfd4f1fa02581571cf564382236847bce40dd10f18e81bcaac66d7'}


def verify_bundled_libraries(jar, metadata):
    declared = [entry["file"] for entry in metadata.get("jars", [])]
    expected = {"META-INF/jars/" + name for name in APPROVED_LIBRARIES}
    assert len(declared) == len(expected) and set(declared) == expected, "Unreviewed bundled libraries"
    actual = {name for name in jar.namelist() if name.startswith("META-INF/jars/") and name.endswith(".jar")}
    assert actual == expected, "Undeclared bundled libraries"
    for name in sorted(expected):
        with zipfile.ZipFile(BytesIO(jar.read(name))) as library:
            entries = library.namelist()
            assert len(entries) == len(set(entries)), "Duplicate library entries"
            digest = hashlib.sha256()
            for entry in sorted(entries):
                if entry.endswith("/") or entry == "fabric.mod.json":
                    continue
                data = library.read(entry)
                encoded = entry.encode()
                digest.update(len(encoded).to_bytes(4, "big")); digest.update(encoded)
                digest.update(len(data).to_bytes(8, "big")); digest.update(data)
                if entry.endswith(".class"):
                    assert not any(value in data for value in FORBIDDEN), "Forbidden platform dependency in " + name
            assert digest.hexdigest() == APPROVED_LIBRARIES[Path(name).name], "Bundled library differs from reviewed payload"
            if "fabric.mod.json" in entries:
                generated = json.loads(library.read("fabric.mod.json"))
                assert generated.get("version") == "3.8.3"
                assert not generated.get("entrypoints") and not generated.get("mixins") and not generated.get("jars")


def walk(data):
    if isinstance(data, dict):
        yield data
        for value in data.values():
            yield from walk(value)
    elif isinstance(data, list):
        for value in data:
            yield from walk(value)


def main() -> None:
    jars = [p for p in (PROJECT / "build/libs").glob("*.jar") if not p.name.endswith("-sources.jar")]
    if len(jars) != 1:
        raise RuntimeError(f"Expected one mod jar, found {jars}")
    with zipfile.ZipFile(jars[0]) as jar:
        metadata = json.loads(jar.read("fabric.mod.json"))
        assert metadata["id"] == "create"
        assert metadata["depends"]["minecraft"] == "26.3"
        assert metadata["depends"]["java"] == ">=25"
        assert "sodium" not in metadata.get("depends", {})
        verify_bundled_libraries(jar, metadata)
        assert len(jar.namelist()) == len(set(jar.namelist())), "Duplicate mod entries"
        for entries in metadata["entrypoints"].values():
            for name in entries:
                assert name.replace(".", "/") + ".class" in jar.namelist(), f"Missing entrypoint: {name}"
        classes = [name for name in jar.namelist() if name.endswith(".class")]
        for name in classes:
            bytecode = jar.read(name)
            assert int.from_bytes(bytecode[6:8]) == 69, f"Expected Java 25 bytecode: {name}"
            for forbidden in FORBIDDEN:
                assert forbidden not in bytecode, f"{name} references {forbidden.decode()}"
        assert not any("test/" in name or "KineticGraphTest" in name for name in classes), "Test code leaked into the mod"
        assert "LICENSE" in jar.namelist()
        assert not any(name.lower().endswith((".md", ".markdown")) for name in jar.namelist()), "Local documentation leaked into the mod"
        resources = 0
        for name in jar.namelist():
            if not name.endswith(".json") or not name.startswith(("assets/", "data/")):
                continue
            resources += 1
            data = json.loads(jar.read(name))
            for obj in walk(data):
                assert "loader" not in obj or not str(obj["loader"]).startswith("neoforge:"), f"Legacy model loader: {name}"
                if "/loot_table/" in name:
                    assert not ({"conditions", "functions", "function"} & obj.keys()), f"Legacy loot fields: {name}"
                    assert obj.get("type") != "minecraft:block_state_property", f"Legacy block predicate: {name}"
                if "/recipe/" in name:
                    assert not str(obj.get("type", "")).startswith("neoforge:"), f"Legacy recipe ingredient: {name}"
            assert "/worldgen/configured_feature/" not in name, f"Legacy worldgen registry: {name}"
        print(f"Verified {jars[0].name}: {len(classes)} Java 25 classes, {resources} JSON resources; Fabric 26.3; no Sodium/NeoForge/Flywheel/OpenGL references or legacy loot/worldgen schemas.")


if __name__ == "__main__":
    main()
