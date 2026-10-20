#!/usr/bin/env python3
"""Merge exact pinned Create Ponder translations without changing other native language keys."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

CREATE_COMMIT = 'ac0c444d9828da3453ae8cc65338e8de063286fb'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def synchronize(source_root, fabric_root):
    listing = subprocess.run(
        ['git', '-C', str(source_root), 'ls-tree', '-r', '--name-only', CREATE_COMMIT, '--',
         'src/main/resources/assets/create/lang', 'src/generated/resources/assets/create/lang'],
        check=True, capture_output=True, text=True).stdout.splitlines()
    records = {}
    for name in sorted(listing):
        path = Path(name)
        if path.suffix != '.json' or path.parent.name != 'lang':
            continue
        data = subprocess.run(['git', '-C', str(source_root), 'show', CREATE_COMMIT + ':' + name],
                              check=True, capture_output=True).stdout
        entries = {key: value for key, value in json.loads(data).items() if key.startswith('create.ponder.')}
        if not entries:
            continue
        target = fabric_root / 'src/main/resources/assets/create/lang' / path.name
        existing = json.loads(target.read_text()) if target.exists() else {}
        before_other = {k: v for k, v in existing.items() if not k.startswith('create.ponder.')}
        existing.update(entries)
        assert before_other == {k: v for k, v in existing.items() if not k.startswith('create.ponder.')}
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(existing, indent=2, ensure_ascii=False) + '\n')
        records[str(target.relative_to(fabric_root))] = {
            'source': name, 'sourceSHA256': digest(data), 'copiedPrefix': 'create.ponder.',
            'copiedKeys': len(entries),
            'keysSHA256': digest(json.dumps(entries, sort_keys=True, ensure_ascii=False).encode()),
            'nonPonderKeysPreserved': True, 'candidateSHA256': digest(target.read_bytes())}
    manifest = {'createCommit': CREATE_COMMIT,
                'source': 'Exact pinned Create locale source JSON; no generated scene callbacks',
                'languageFiles': records}
    (fabric_root / 'create-ponder-lang-source.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, required=True, help='Checkout of the pinned upstream Create source')
    parser.add_argument('--fabric-root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    result = synchronize(args.source_root.resolve(), args.fabric_root.resolve())
    print(json.dumps({'files': len(result['languageFiles']),
                      'keys': sum(v['copiedKeys'] for v in result['languageFiles'].values())}))
