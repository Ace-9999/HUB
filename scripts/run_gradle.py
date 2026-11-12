"""Run the checked-in wrapper with local SDK discovery, retaining exact exit status."""
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
if os.name == 'nt' and 'JAVA_HOME' not in env:
    candidate = Path('C:/Program Files/Java/jdk-21')
    if candidate.exists():
        env['JAVA_HOME'] = str(candidate)
env.pop('DEBUG', None)
if (root / '.tools/android-sdk').exists():
    env.setdefault('ANDROID_HOME', str(root / '.tools/android-sdk'))
wrapper = str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
sys.exit(subprocess.call([wrapper, '--console=plain', *sys.argv[1:]], cwd=root, env=env))
