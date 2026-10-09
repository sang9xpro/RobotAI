package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.dialogue.runtime.Persona;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/** Decides the requested song and search terms; Android owns YouTube lookup and playback. */
@Slf4j
@Component
public class YouTubeMusicPlayer implements ToolsGlobalRegistry.GlobalFunction {
    public static final String TOOL_NAME = "play_youtube";
    private final SessionManager sessions;
    private final DeviceMcpService deviceMcp;

    public YouTubeMusicPlayer(SessionManager sessions, DeviceMcpService deviceMcp) {
        this.sessions = sessions;
        this.deviceMcp = deviceMcp;
    }

    @Tool(name = TOOL_NAME, description = "Yêu cầu app Android tìm và phát bài hát trên YouTube. Backend chỉ xác định tên bài, ca sĩ và từ khóa; app tự tìm video, chọn kết quả và phát trong trình phát hiển thị cùng Ken.", returnDirect = true)
    public String playYoutube(@ToolParam(description = "Tên bài hát hoặc link YouTube watch/youtu.be") String songName,
            @ToolParam(description = "Từ khóa tìm YouTube do backend đề xuất; để trống nếu dùng tên bài", required = false) String searchQuery,
            @ToolParam(description = "Ca sĩ mong muốn, nếu người dùng nêu rõ", required = false) String artist,
            ToolContext context) {
        Object sessionId = context == null ? null : context.getContext().get(Persona.TOOL_CONTEXT_SESSION_ID_KEY);
        var session = sessionId instanceof String id ? sessions.getSession(id) : null;
        if (session == null || session.getDevice() == null) return "Chưa có kết nối âm thanh với robot.";
        if (session.getToolsSessionHolder() == null || session.getToolsSessionHolder().getFunction("self_music_search") == null)
            return "App chưa có MCP tìm nhạc YouTube. Hãy cập nhật app và kết nối lại.";
        if (songName == null || songName.isBlank() || songName.length() > 300) return "Hãy nói tên bài hát hoặc gửi link YouTube cụ thể.";
        try {
            String request = songName.strip();
            String directId = videoId(request);
            String requestedArtist = artist == null ? "" : artist.strip();
            String keyword = searchQuery == null || searchQuery.isBlank() ? request : searchQuery.strip();
            if (requestedArtist.length() > 300 || keyword.length() > 300) return "Tên ca sĩ hoặc từ khóa tìm kiếm quá dài.";
            var args = new java.util.LinkedHashMap<String, Object>();
            args.put("title", directId == null ? request : "Video YouTube");
            args.put("artist", requestedArtist);
            args.put("searchQuery", keyword);
            if (directId != null) args.put("videoUrl", "https://www.youtube.com/watch?v=" + directId);
            var result = deviceMcp.callDeviceTool(session.getDevice().getDeviceId(), "self.music.search", args);
            if (Boolean.TRUE.equals(result.get("isError"))) return "App từ chối tìm nhạc YouTube: " + result.get("content");
            return "Đã yêu cầu app tìm “" + request + "” trên YouTube. Ken sẽ phát kết quả trong app khi tìm thấy.";
        } catch (Exception e) {
            log.warn("Cannot request YouTube music for session {}: {}", sessionId, e.toString());
            return "Chưa gửi được yêu cầu tìm YouTube tới app. Hãy kiểm tra kết nối với robot.";
        }
    }

    public String playYoutube(String songName, ToolContext context) {
        return playYoutube(songName, null, null, context);
    }

    /** Only canonical YouTube hosts and 11-character video IDs may bypass search. */
    static String videoId(String link) {
        try {
            URI uri = URI.create(link);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) return null;
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String id = null;
            if (host.equals("youtu.be")) id = uri.getPath().replaceFirst("^/", "");
            else if (host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
                if (uri.getPath().equals("/watch") && uri.getRawQuery() != null) {
                    for (String part : uri.getRawQuery().split("&"))
                        if (part.startsWith("v=")) id = URLDecoder.decode(part.substring(2), StandardCharsets.UTF_8);
                } else if (uri.getPath().startsWith("/shorts/")) id = uri.getPath().substring(8);
            }
            return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : null;
        } catch (Exception ignored) { return null; }
    }

    @Override public ToolCallback getFunctionCallTool(ToolSession session) { return ToolCallbacks.from(this)[0]; }
    @Override public String getToolName() { return TOOL_NAME; }
    @Override public String getToolDescription() { return "Ra lệnh cho app tìm và phát nhạc YouTube"; }
}
