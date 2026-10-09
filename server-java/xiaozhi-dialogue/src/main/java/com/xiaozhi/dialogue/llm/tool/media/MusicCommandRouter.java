package com.xiaozhi.dialogue.llm.tool.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import org.springframework.ai.chat.model.ToolContext;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** Explicit music commands also work with providers that cannot call functions. */
@Slf4j
public final class MusicCommandRouter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WHO = "(?:tôi|mình|em|anh|chị|bạn)";
    private static final String MUSIC = "(?:bài hát|bản nhạc|bài nhạc|ca khúc|nhạc)";
    // PhoWhisper may prepend punctuation or transcribe the wake name as “kèn người”.
    private static final Pattern PREFIX = Pattern.compile("(?iu)^(?:(?:ken(?:a)?|kèn)(?:\\s+(?:ơi|người))?[,!]?\\s+)?(?:hãy\\s+)?");
    private static final Pattern TAIL = Pattern.compile("(?iu)(?:\\s+(?:cho(?: " + WHO + ")? nghe|nhé|nha|đi|giúp tôi))+$");
    private static final Pattern ANY = Pattern.compile("(?iu)^(?:(?:phát|mở|bật)(?: cho " + WHO + ")?\\s+(?:(?:một|1)\\s+)?" + MUSIC
            + "|cho " + WHO + " nghe(?:\\s+(?:(?:một|1)\\s+)?" + MUSIC + ")?|" + WHO + " muốn nghe\\s+(?:(?:một|1)\\s+)?" + MUSIC + ")(?: bất kỳ| nào đó)?$");
    private static final Pattern PLAY = Pattern.compile("(?iu)^(?:(?:phát|mở|bật)(?: cho " + WHO + ")?\\s+(?:bài hát|bản nhạc|bài nhạc|bài|ca khúc|nhạc)|cho " + WHO + " nghe(?: bài hát| bài| ca khúc)?)\\s+(.+)$");
    private static final Pattern YOUTUBE = Pattern.compile("(?iu)\\s+(?:trên|ở|từ)\\s+youtube$");
    private static final Pattern LIST = Pattern.compile("(?iu)^(?:có những bài hát nào|có bài hát nào|danh sách (?:bài hát|nhạc)|liệt kê (?:bài hát|nhạc)|cho (?:tôi|mình) xem danh sách (?:bài hát|nhạc))$");
    private static final Pattern SEARCH = Pattern.compile("(?iu)^tìm (?:bài hát|bài|ca khúc)\\s+(.+)$");
    private static final Pattern CAPABILITY = Pattern.compile("(?iu)^(?:(?:bạn|kena?)\\s+)?(?:có\\s+)?(?:thể\\s+)?(?:nghe|phát|mở|bật)(?: được)?\\s+nhạc(?: mp3)?(?: được)?\\s+(?:không|chưa)$");
    private MusicCommandRouter() {}

    public static Optional<String> route(String text, ToolsSessionHolder holder, ToolContext context) {
        if (text == null) return Optional.empty();
        String clean = text.strip().replaceAll("^[\\s.!?,…]+|[\\s.!?,…]+$", "");
        String command = TAIL.matcher(PREFIX.matcher(clean).replaceFirst("")).replaceFirst("").strip();
        String tool;
        Map<String, String> arguments;
        var play = PLAY.matcher(command);
        var search = SEARCH.matcher(command);
        boolean capability = CAPABILITY.matcher(command).matches();
        if (capability) {
            if (holder == null || holder.getFunction("self_music_play") == null)
                return Optional.of("App chưa kết nối MCP nghe nhạc. Hãy mở app Android và kết nối lại để tôi phát MP3.");
            tool = "get_playlist";
            arguments = Map.of();
        } else if (ANY.matcher(command).matches()) {
            tool = "play_music";
            arguments = Map.of("songName", "");
        } else if (play.matches()) {
            String song = play.group(1).replaceAll("(?iu)\\s+(?:nhé|đi|giúp tôi)$", "").strip();
            var youtube = YOUTUBE.matcher(song);
            tool = youtube.find() ? "play_youtube" : "play_music";
            arguments = Map.of("songName", youtube.replaceFirst("").strip());
        } else if (LIST.matcher(command).matches()) {
            tool = "get_playlist";
            arguments = Map.of();
        } else if (search.matches()) {
            tool = "get_playlist";
            arguments = Map.of("query", search.group(1));
        } else return Optional.empty();
        var callback = holder == null ? null : holder.getFunction(tool);
        if (callback == null) return Optional.of("Công cụ nghe nhạc đang bị tắt cho phiên này. Hãy bật công cụ trong cấu hình robot.");
        try {
            log.info("Routing explicit music command to {}", tool);
            String result = callback.call(JSON.writeValueAsString(arguments), context);
            try {
                var node = JSON.readTree(result);
                if (node.isTextual()) result = node.asText();
            } catch (Exception ignored) { /* Plain text tool results are valid too. */ }
            return Optional.of(capability
                    ? "Tôi có thể phát MP3 có trong thư viện trên loa của app Android. " + result
                    : result);
        } catch (Exception e) {
            log.warn("Music command failed: {}", tool, e);
            return Optional.of("Không thực hiện được yêu cầu nghe nhạc. Hãy kiểm tra thư viện MP3 trên backend.");
        }
    }
}
