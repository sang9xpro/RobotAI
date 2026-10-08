"""Keep Sherpa ORT 1.28.2 and Java ORT 1.24.3 as separate ELF libraries.

Rename the Java runtime's SONAME, its JNI DT_NEEDED and GNU version-provider
filename without moving ELF offsets. All replacement strings are shorter.
The versioned OrtGetApiBase imports continue to bind to their matching runtime.
"""
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
LIBS = ROOT / 'android-app/whisper-test/app/libs'

def rename_runtime(data: bytes) -> bytes:
    assert data[:6] == b'\x7fELF\x02\x01', 'Expected little-endian ELF64'
    phoff = struct.unpack_from('<Q', data, 32)[0]
    phsize, phcount = struct.unpack_from('<HH', data, 54)
    headers = [struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize) for i in range(phcount)]
    dynamic = next(p for p in headers if p[0] == 2)
    entries = dict(struct.unpack_from('<qQ', data, off) for off in range(dynamic[2], dynamic[2] + dynamic[5], 16))
    address, size = entries[5], entries[10] # DT_STRTAB, DT_STRSZ
    load = next(p for p in headers if p[0] == 1 and p[3] <= address < p[3] + p[5])
    start = load[2] + address - load[3]
    old, new = b'libonnxruntime.so\0', b'libort_tts.so\0'
    strings = data[start:start + size]
    assert strings.count(old) == 1, 'Unexpected dynamic string table'
    replacement = new + b'\0' * (len(old) - len(new))
    patched = strings.replace(old, replacement)
    return data[:start] + patched + data[start + size:]

source = LIBS / 'onnxruntime-android-1.24.3.aar'
target = LIBS / 'onnxruntime-android-1.24.3-tts.aar'
with zipfile.ZipFile(source) as src, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as out:
    for info in src.infolist():
        name = info.filename
        if name.startswith('jni/') and not name.startswith('jni/arm64-v8a/'):
            continue
        data = src.read(name)
        if name == 'jni/arm64-v8a/libonnxruntime.so':
            name = 'jni/arm64-v8a/libort_tts.so'
            data = rename_runtime(data)
        elif name == 'jni/arm64-v8a/libonnxruntime4j_jni.so':
            data = rename_runtime(data)
        out.writestr(name, data)
print(target)
