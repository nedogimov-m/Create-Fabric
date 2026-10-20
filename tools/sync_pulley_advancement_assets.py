"""Exact source pulley criterion and its existing native advancement parent closure."""
import json

def sync(g):
    pending = ['pulley_maxed']
    seen = set()
    while pending:
        name = pending.pop()
        if name in seen:
            continue
        seen.add(name)
        relative = 'data/create/advancement/' + name + '.json'
        source = g['find'](relative)
        data = json.loads(source.read_text())
        if name == 'pulley_maxed' or not (g['OUTPUT'] / relative).is_file():
            g['write'](relative, source.read_bytes())
        parent = data.get('parent', '')
        if parent.startswith('create:'):
            pending.append(parent.removeprefix('create:'))
    return {'sourceAdvancement': 'pulley_maxed', 'ancestors': sorted(seen), 'runtime': 'UNRUN'}
