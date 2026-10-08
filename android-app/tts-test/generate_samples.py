"""Generate robot voice comparisons entirely offline after artifact download."""
import json,time,resource
from pathlib import Path
import numpy as np
import soundfile as sf
from vieneu._v3_turbo_engine.onnx_runtime_lite import OnnxV3LiteEngine
from vieneu_utils.phonemize_text import normalize_to_chunks_v3_with_gaps,phonemize_text_with_emotions
from vieneu_utils.core_utils import strip_encoder_pad_frame,join_audio_chunks,gaps_to_silence
ROOT=Path(__file__).resolve().parents[2]
MODELS=ROOT/"android-app/.work/vieneu-models"
OUT=ROOT/"samples/tts/vieneu-v3-turbo";OUT.mkdir(parents=True,exist_ok=True)
VOICES=json.loads((ROOT/"android-app/.work/vieneu-tts/src/vieneu/assets/voices_v3_turbo.json").read_text())["presets"]
TESTS=[("hai-dang","Hải Đăng"),("truc-ly","Trúc Ly"),("adam","Adam"),("thuc-doan","Thục Đoan"),("quang-son","Quang Sơn"),("ngoc-tran","Ngọc Trân")]
TEXT="Xin chào, mình là robot đồng hành của bạn. Hôm nay bạn muốn trò chuyện hay cùng mình khám phá điều gì?"
t0=time.perf_counter()
engine=OnnxV3LiteEngine(checkpoint_path=str(MODELS),onnx_dir=str(MODELS/"backbone"),codec_dir=str(MODELS/"codec"),threads=4)
load=time.perf_counter()-t0
print(f"Loaded FP32 in {load:.2f} seconds",flush=True)
schema={}
for name in ["sess_pre","sess_dec","sess_ac","sess_codec_dec","sess_codec_step"]:
 session=getattr(engine,name,None)
 if session is not None:schema[name]={"inputs":[dict(name=x.name,type=x.type,shape=x.shape) for x in session.get_inputs()],"outputs":[dict(name=x.name,type=x.type,shape=x.shape) for x in session.get_outputs()]}
(OUT/"onnx-io-schema.json").write_text(json.dumps(schema,indent=2)+"\n")
rows=[]
def synth(slug,voice,text):
 v=VOICES[voice];emb=np.asarray(v["speaker_emb"],dtype=np.float32);codes=strip_encoder_pad_frame(np.asarray(v["codes"],dtype=np.int64))
 chunks,gaps=normalize_to_chunks_v3_with_gaps(text,max_chars=256)
 phonemes=[phonemize_text_with_emotions(c) for c in chunks]
 np.random.seed(42)
 t=time.perf_counter();wavs=[engine.infer(phonemes=p,speaker_emb=emb,ref_codes=codes,temperature=.8) for p in phonemes]
 wav=join_audio_chunks(wavs,48000,silence_ps=gaps_to_silence(gaps));elapsed=time.perf_counter()-t
 if not len(wav) or not np.isfinite(wav).all():raise RuntimeError("Invalid audio")
 peak=float(np.max(np.abs(wav))); gain=min(1.0,0.98/max(peak,1e-9)); wav=wav*gain
 name=slug+".wav";sf.write(OUT/name,wav,48000,subtype="PCM_16")
 row=dict(gain=gain,raw_peak=peak,file=name,voice=voice,description=v["description"],text=text,normalized_chunks=chunks,phonemes=phonemes,seconds=len(wav)/48000,synthesis_seconds=elapsed,rtf=elapsed/(len(wav)/48000),peak=float(np.max(np.abs(wav))),clipped_fraction=float(np.mean(np.abs(wav)>1)))
 rows.append(row);print(json.dumps({k:row[k] for k in ["file","seconds","synthesis_seconds","rtf"]}),flush=True)
 (OUT/"metrics.json").write_text(json.dumps(dict(model="VieNeu-TTS-v3-Turbo",precision="fp32",threads=4,load_seconds=load,platform="macOS ARM CPU",max_rss_bytes=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,samples=rows),ensure_ascii=False,indent=2)+"\n")
for slug,voice in TESTS:synth(slug,voice,TEXT)
synth("hai-dang-technical","Hải Đăng","Đây là dung lượng lưu trữ, không phải RAM. Khi chạy, RAM còn dành cho bộ nhớ đệm tính toán.")
synth("hai-dang-numbers","Hải Đăng","Pin còn 85 phần trăm. Mình sẽ nhắc bạn lúc 8 giờ 30 phút sáng. Nhiệt độ hôm nay là 28 độ C.")
# Measure first audible streaming chunk, avoiding a new model load.
v=VOICES["Hải Đăng"];np.random.seed(42);t=time.perf_counter();first=None;count=0
for chunk in engine.infer_stream(text="Mình đang nghe đây. Bạn muốn mình giúp điều gì?",speaker_emb=np.asarray(v["speaker_emb"],dtype=np.float32),ref_codes=strip_encoder_pad_frame(np.asarray(v["codes"],dtype=np.int64))):
 if first is None and len(chunk):first=time.perf_counter()-t
 count+=len(chunk)
data=json.loads((OUT/"metrics.json").read_text());data["streaming"]={"first_chunk_seconds":first,"total_seconds":time.perf_counter()-t,"audio_seconds":count/48000};(OUT/"metrics.json").write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n");print("Streaming",data["streaming"],flush=True)
