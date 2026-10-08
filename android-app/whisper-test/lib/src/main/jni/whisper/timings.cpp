#include "whisper.h"
#include <cstdio>
#include <memory>
extern "C" void robotai_whisper_timings(whisper_context *ctx, char *out, size_t size) {
    std::unique_ptr<whisper_timings> t(whisper_get_timings(ctx));
    if (!t) { std::snprintf(out, size, "Chưa có số đo"); return; }
    std::snprintf(out, size, "encoder %.0f ms · decoder/token %.1f ms · prompt/token %.1f ms", t->encode_ms, t->decode_ms + t->batchd_ms, t->prompt_ms);
}
