"""Fetch pinned official FP32 inference artifacts. No user audio is uploaded."""
import concurrent.futures,hashlib,json,subprocess,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/".work/vieneu-models"
JOBS=[("pnnbao-ump/VieNeu-TTS-v3-Turbo","61b85e3d937fbbacb387714180e8182823512523","onnx_update/"+n,"backbone/"+n) for n in ["config.json","tokenizer.json","vieneu_prefill.onnx","vieneu_decode_step.onnx","vieneu_acoustic_cached.onnx","vieneu_backbone_shared.data","vieneu_v3_heads.npz"]]+[("OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX","ceff0d0749bfb3fa2d61149794ec6feef0d1e1ae",n,"codec/"+n) for n in ["moss_audio_tokenizer_decode_full.onnx","moss_audio_tokenizer_decode_shared.data","moss_audio_tokenizer_decode_step.onnx","codec_browser_onnx_meta.json"]]
def fetch(job):
 repo,rev,name,dest=job;url=f"https://huggingface.co/{repo}/resolve/{rev}/{name}";p=ROOT/dest;p.parent.mkdir(parents=True,exist_ok=True)
 with urllib.request.urlopen(urllib.request.Request(url,method="HEAD"),timeout=30) as h:size=int(h.headers["Content-Length"])
 if not p.exists() or p.stat().st_size!=size:
  step=32*1024*1024;parts=p.parent/(p.name+".parts");parts.mkdir(exist_ok=True)
  def part(i):
   start=i*step;end=min(size,start+step)-1;q=parts/str(i)
   if q.exists() and q.stat().st_size==end-start+1:return
   subprocess.run(["curl","-sS","-L","--fail","--retry","3","--max-time","90","--range",f"{start}-{end}","-o",str(q),url+f"?robotai_part={i}"],check=True)
   if q.stat().st_size!=end-start+1:raise ValueError(f"Unexpected range size {name} {i}")
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(part,range((size+step-1)//step)))
  with p.open("wb") as w:
   for i in range((size+step-1)//step):w.write((parts/str(i)).read_bytes())
 digest=hashlib.file_digest(p.open("rb"),"sha256").hexdigest()
 print(f"Ready {dest}: {size/1e6:.1f} MB",flush=True)
 return dict(repo=repo,revision=rev,file=name,local=dest,bytes=size,sha256=digest)
if __name__=="__main__":
 ROOT.mkdir(parents=True,exist_ok=True)
 manifest=[fetch(j) for j in JOBS]
 (ROOT/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n")
