"""Install pinned local Android CLI and Gradle; never change machine settings."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)

def install(kind, url, destination, digest_url=None):
    archive = TOOLS / (kind + '.zip')
    if destination.exists():
        print(kind + ': already installed', flush=True)
        return
    print('Downloading ' + kind, flush=True)
    if not archive.exists():
        temp = archive.with_suffix('.partial')
        urllib.request.urlretrieve(url, temp)
        temp.replace(archive)
    if digest_url:
        expected = urllib.request.urlopen(digest_url).read().decode().strip()
        assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected
    staging = TOOLS / (kind + '-extract')
    staging.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        z.extractall(staging)
    destination.parent.mkdir(parents=True, exist_ok=True)
    inner = next(staging.iterdir())
    shutil.move(str(inner), str(destination))
    print(kind + ': installed', flush=True)

with ThreadPoolExecutor(max_workers=2) as pool:
    jobs = [
        pool.submit(install, 'gradle', 'https://services.gradle.org/distributions/gradle-8.13-bin.zip', TOOLS / 'gradle-8.13', 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256'),
        pool.submit(install, 'android-cli', 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip', TOOLS / 'android-sdk/cmdline-tools/latest'),
    ]
    for job in jobs:
        job.result()
