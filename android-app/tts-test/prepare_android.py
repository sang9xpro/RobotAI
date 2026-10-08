"""Prepare pinned VieNeu FP32 assets and fixed Hai Dang conditioning."""
from pathlib import Path
import json,shutil,numpy as np
r=Path(__file__).resolve().parents[2];a=r/"android-app/whisper-test/app/src/main/assets/tts";src=r/"android-app/.work/vieneu-models"
a.mkdir(parents=True,exist_ok=True)
for folder,names in [("backbone",["config.json","tokenizer.json","vieneu_prefill.onnx","vieneu_decode_step.onnx","vieneu_acoustic_cached.onnx","vieneu_backbone_shared.data"]),("codec",["moss_audio_tokenizer_decode_full.onnx","moss_audio_tokenizer_decode_step.onnx","codec_browser_onnx_meta.json","moss_audio_tokenizer_decode_shared.data"])]:
 for name in names:
  (a/folder).mkdir(exist_ok=True);shutil.copyfile(src/folder/name,a/folder/name)
z=np.load(src/"backbone/vieneu_v3_heads.npz");shapes={}
for name in ["text_emb","audio_emb"]:
 data=z[name].astype("<f4");data.tofile(a/(name+".f32"));shapes[name]=list(data.shape)
v=json.loads((r/"android-app/.work/vieneu-tts/src/vieneu/assets/voices_v3_turbo.json").read_text())["presets"]["Hải Đăng"]
emb=np.asarray(v["speaker_emb"],dtype=np.float32);anchor=emb@z["xvec_w"].T+z["xvec_b"];anchor=(anchor-anchor.mean())/np.sqrt(anchor.var()+float(z["xvec_ln_eps"]));anchor=anchor*z["xvec_ln_w"]+z["xvec_ln_b"]
codes=np.asarray(v["codes"],dtype=np.int64)
if len(codes)>1 and codes[-1,0]==455:codes=codes[:-1]
(a/"voice.json").write_text(json.dumps(dict(name="Hải Đăng",anchor=anchor.tolist(),codes=codes.tolist(),shapes=shapes),ensure_ascii=False)+"\n")
shutil.copyfile(r/"android-app/.work/vieneu-tts/.venv/lib/python3.12/site-packages/sea_g2p/sea_g2p.bin",a/"sea_g2p.bin")
shutil.copyfile(r/"android-app/.work/vieneu-tts/LICENSE",a/"VIENEU_LICENSE.txt");shutil.copyfile(r/"android-app/.work/sea-g2p/LICENSE",a/"SEA_G2P_LICENSE.txt")
print("Prepared",a)
