"""Restore the genuine relocation hint/messages without replacing other native language keys."""
import json
from pathlib import Path


def generate(output, upstream):
    output, upstream = Path(output), Path(upstream)
    sources = {}
    for base in (upstream / 'src/main/resources/assets/create/lang',
                 upstream / 'src/generated/resources/assets/create/lang'):
        for path in sorted(base.glob('*.json')):
            sources[path.name] = path
    changed = 0
    for name, source in sources.items():
        original = json.loads(source.read_text())
        selected = {key: value for key, value in original.items()
                    if key.startswith('create.hint.derailed_train')
                    or key.startswith('create.train.relocate')
                    or key == 'create.train.cannot_relocate_moving'}
        if not selected:
            continue
        target = output / 'assets/create/lang' / name
        current = json.loads(target.read_text()) if target.is_file() else {}
        current.update(selected)
        data = json.dumps(current, indent=2, ensure_ascii=False) + '\n'
        if not target.is_file() or target.read_text() != data:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(data)
            changed += 1
    return changed
