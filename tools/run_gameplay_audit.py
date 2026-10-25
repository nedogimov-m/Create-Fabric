#!/usr/bin/env python3
"""Execute the frozen native Minecraft command prepared by gameplay_audit.init.gradle."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("launch", type=Path, help="Prepared launch.json")
    args = parser.parse_args()
    spec_path = args.launch.resolve()
    spec = json.loads(spec_path.read_text())
    jvm_args = list(spec["jvmArgs"])
    if not any(argument.startswith("-Xmx") for argument in jvm_args):
        jvm_args.append("-Xmx3G")
    command = [spec["java"], *jvm_args, "-cp", os.pathsep.join(spec["classpath"]),
               spec["mainClass"], *spec["args"]]
    print("Starting actual frozen Minecraft JVM:", spec_path.parent.name, flush=True)
    result = subprocess.run(command, cwd=spec["workingDirectory"])
    print("Native Minecraft exit code:", result.returncode, flush=True)
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
