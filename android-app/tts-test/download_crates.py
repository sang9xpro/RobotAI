"""Fetch Cargo.lock crates via canonical archive URLs; verify registry checksums."""
import concurrent.futures, hashlib, pathlib, subprocess, tomllib
root = pathlib.Path(__file__).resolve().parents[2]
lock = tomllib.loads((root / 'android-app/.work/sea-g2p/Cargo.lock').read_text())
cache = root / 'android-app/.work/cargo/registry/cache/index.crates.io-1949cf8c6b5b557f'
cache.mkdir(parents=True, exist_ok=True)
def fetch(p):
    name, version = p['name'], p['version']
    path = cache / f'{name}-{version}.crate'
    if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == p['checksum']: return
    url = f'https://repo.huaweicloud.com/rust/crates/{name}/{name}-{version}.crate'
    subprocess.run(['curl', '-fsSL', '--retry', '3', '--retry-all-errors', '--max-time', '120', '-o', str(path), url], check=True)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == p['checksum'], path
    print(name, version, flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    list(pool.map(fetch, [p for p in lock['package'] if 'checksum' in p]))
