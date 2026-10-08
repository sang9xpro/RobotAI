"""Download the pinned original PhoWhisper-large checkpoint; resume and verify SHA256."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib, subprocess
r = Path(__file__).resolve().parents[2] / 'android-app/.work/phowhisper-large'
r.mkdir(parents=True, exist_ok=True)
revision = 'b9136a44b5f2ca664bd0b8f74baecf1715f6eeeb'
base = f'https://huggingface.co/vinai/PhoWhisper-large/resolve/{revision}/'
for name in ['config.json', 'vocab.json', 'added_tokens.json']:
    subprocess.run(['curl','-fsSL','--retry','3','--max-time','60','-o',str(r/name),base+name],check=True)
size = 6173655608; step = 16 * 1024 * 1024
parts = r/'parts'; parts.mkdir(exist_ok=True)
def fetch(i):
    start = i * step; end = min(size, start + step) - 1; p = parts/str(i)
    if p.exists() and p.stat().st_size == end-start+1: return
    subprocess.run(['curl','-fsSL','--retry','3','--max-time','90','--range',f'{start}-{end}',
                    '-o',str(p),base+f'pytorch_model.bin?part={i}&size=16m'],check=True)
    assert p.stat().st_size == end-start+1
with ThreadPoolExecutor(max_workers=4) as pool:
    tasks = [pool.submit(fetch,i) for i in range((size+step-1)//step)]
    for n, task in enumerate(as_completed(tasks),1):
        task.result()
        if n % 10 == 0: print(f'{n}/{len(tasks)} parts',flush=True)
sha = hashlib.sha256()
with (r/'pytorch_model.bin').open('wb') as out:
    for i in range((size+step-1)//step):
        with (parts/str(i)).open('rb') as part:
            while data := part.read(1024*1024): out.write(data); sha.update(data)
assert sha.hexdigest() == '9e0077352544f497673395b313a9ef6d7cd2967598197c4d691d8042333060b2'
print('SHA256 verified',flush=True)
