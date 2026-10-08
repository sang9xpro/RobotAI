from pathlib import Path
import json
from tokenizers import Tokenizer
from vieneu_utils.phonemize_text import phonemize_text
r = Path(__file__).resolve().parents[2]
tokenizer = Tokenizer.from_file(str(r/'android-app/.work/vieneu-models/backbone/tokenizer.json'))
tests = []
for text in ['Xin chào, mình là robot của bạn.', 'Bộ nhớ RAM 8 GB, giá 1.500.000 đồng.', 'Hôm nay là ngày 2 tháng 10 năm 2026.']:
    phones = phonemize_text(text)
    tests.append(dict(text=text, phones=phones, ids=tokenizer.encode(phones, add_special_tokens=False).ids))
p = r/'android-app/whisper-test/app/src/androidTest/assets/tts-fixtures.json'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(json.dumps(tests, ensure_ascii=False))
print(p)
