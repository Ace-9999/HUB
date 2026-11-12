"""Reject forbidden module edges and dynamic dependency versions."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
errors = []
for file in root.rglob('build.gradle.kts'):
    if any(x in file.parts for x in ('.tools', '.gradle', 'build')):
        continue
    module = ':'.join(file.parent.relative_to(root).parts)
    text = file.read_text()
    for dep in re.findall(r'project\("(:[^"\n]+)"\)', text):
        if module.startswith('feature:') and dep.startswith(':feature:'):
            errors.append(f'{module}: forbidden feature dependency {dep}')
        if module.endswith(':api') and any(x in dep for x in (':data', ':feature', ':integration')):
            errors.append(f'{module}: API depends on implementation {dep}')
    if re.search(r'["\'][^"\']*:\d[^"\']*\+["\']', text):
        errors.append(f'{module}: dynamic dependency')
if errors:
    raise SystemExit('\n'.join(errors))
print('PASS: module boundaries and pinned dependency declarations')
