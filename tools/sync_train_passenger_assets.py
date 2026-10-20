"""Pinned source train impact/travel advancements and their existing parent closure."""
import json

def sync(g):
    roots = {'train_roadkill', 'long_travel'}
    pending = list(roots)
    seen = set()
    while pending:
        name = pending.pop()
        if name in seen:
            continue
        seen.add(name)
        relative = 'data/create/advancement/' + name + '.json'
        source = g['find'](relative)
        data = json.loads(source.read_text())
        if name in roots or not (g['OUTPUT'] / relative).is_file():
            g['write'](relative, source.read_bytes())
        parent = data.get('parent', '')
        if parent.startswith('create:'):
            pending.append(parent.removeprefix('create:'))
    return {'sourceAdvancements': sorted(roots), 'ancestors': sorted(seen), 'runtime': 'UNRUN'}
