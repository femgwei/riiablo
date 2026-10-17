"""Source dependency inventory (including inlined constants), not a Java parser.

Compilation is the authority for module isolation. This report additionally
resolves imports, qualified names and same-package type names to expose cycles.
Run from any directory: python tools/architecture/dependencies.py --output DIR
"""
import argparse
import collections
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
LEXEMES = re.compile(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)


def inventory(revision=None):
    sources = {}
    def add(path, text):
        relative = Path(path)
        parts = relative.parts
        marker = next((i for i in range(len(parts) - 2)
                       if parts[i:i + 3] in [('src', 'main', 'java'), ('gen', 'main', 'java')]), None)
        if marker is None:
            return
        module = ':' + ':'.join(parts[:marker])
        text = LEXEMES.sub(' ', text)
        package = re.search(r'\bpackage\s+([\w.]+)\s*;', text)
        if package and relative.stem != 'package-info':
            name = package[1] + '.' + relative.stem
            sources[name] = (module, relative.as_posix(), text)

    if revision:
        paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', revision], cwd=ROOT).decode('utf-8').splitlines()
        paths = [path for path in paths if path.endswith('.java') and
                 ('/src/main/java/' in path or '/gen/main/java/' in path)]
        refs = ''.join(f'{revision}:{path}\n' for path in paths)
        data = subprocess.check_output(['git', 'cat-file', '--batch'], input=refs.encode('utf-8'), cwd=ROOT)
        offset = 0
        for path in paths:
            end = data.index(b'\n', offset)
            size = int(data[offset:end].split()[-1])
            offset = end + 1
            add(path, data[offset:offset + size].decode('utf-8'))
            offset += size + 1
        return sources

    for current, children, _ in os.walk(ROOT):
        children[:] = [name for name in children if name not in {'build', '.gradle', '.git', 'node_modules'}]
        directory = Path(current)
        if directory.parts[-3:] not in [('src', 'main', 'java'), ('gen', 'main', 'java')]:
            continue
        children[:] = []
        for path in directory.rglob('*.java'):
            add(path.relative_to(ROOT), path.read_text(encoding='utf-8'))
    return sources


def graph(sources):
    packages = collections.defaultdict(dict)
    for name in sources:
        package, _, simple = name.rpartition('.')
        packages[package][simple] = name
    edges = {name: set() for name in sources}
    for name, (_, _, text) in sources.items():
        # Explicit imports shadow same-package candidates. In particular the
        # wire D2GS class must not become a false edge to the D2GS application.
        imports = {match[1].rpartition('.')[2]: match[1]
                   for match in re.finditer(r'\bimport\s+([\w.]+)\s*;', text)}
        unqualified = re.sub(r'\b(?:package|import)\s+[^;]+;', ' ', text)
        unqualified = re.sub(r'\bcom\.(?:riiablo|d2moo)(?:\.\w+)+', ' ', unqualified)
        tokens = set(re.findall(r'\b\w+\b', unqualified))
        for simple, target in packages[name.rpartition('.')[0]].items():
            if simple in tokens and target != name and imports.get(simple, target) == target:
                edges[name].add(target)
        for match in re.finditer(r'\bcom\.(?:riiablo|d2moo)(?:\.\w+)+', text):
            target = match[0]
            while target and target not in sources:
                target = target.rpartition('.')[0]
            if target in sources and target != name:
                edges[name].add(target)
        for match in re.finditer(r'import\s+([\w.]+)\.\*', text):
            for simple, target in packages[match[1]].items():
                if simple in tokens and target != name and imports.get(simple, target) == target:
                    edges[name].add(target)
    return edges


def components(edges):
    sys.setrecursionlimit(max(10000, len(edges) * 3))
    index, low, stack, active, result = {}, {}, [], set(), []

    def visit(node):
        index[node] = low[node] = len(index)
        stack.append(node)
        active.add(node)
        for target in sorted(edges[node]):
            if target not in index:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in active:
                low[node] = min(low[node], index[target])
        if low[node] == index[node]:
            group = []
            while True:
                target = stack.pop()
                active.remove(target)
                group.append(target)
                if target == node:
                    break
            result.append(sorted(group))

    for node in sorted(edges):
        if node not in index:
            visit(node)
    return sorted(result, key=lambda group: (-len(group), group))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--revision', help='Read a Git revision without checking it out (e.g. HEAD).')
    args = parser.parse_args()
    sources = inventory(args.revision)
    edges = graph(sources)
    groups = [group for group in components(edges) if len(group) > 1]
    modules = collections.defaultdict(set)
    for source, targets in edges.items():
        owner = sources[source][0]
        modules[owner].update(sources[target][0] for target in targets if sources[target][0] != owner)
    report = {
        'method': 'Conservative source inventory; imports, qualified and same-package names. Compilation verifies module boundaries.',
        'sources': {name: {'module': owner, 'path': path, 'dependencies': sorted(edges[name])}
                    for name, (owner, path, _) in sorted(sources.items())},
        'moduleEdges': {owner: sorted(targets) for owner, targets in sorted(modules.items())},
        'cycles': groups,
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'dependencies.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    lines = ['digraph modules {', '  rankdir=LR;']
    for owner, targets in sorted(modules.items()):
        for target in sorted(targets):
            lines.append('  ' + json.dumps(owner) + ' -> ' + json.dumps(target) + ';')
    (args.output / 'modules.dot').write_text('\n'.join(lines + ['}']) + '\n', encoding='utf-8')
    print(f'{len(sources)} sources; {len(groups)} source cycles; largest: {len(groups[0]) if groups else 0}')


if __name__ == '__main__':
    main()
