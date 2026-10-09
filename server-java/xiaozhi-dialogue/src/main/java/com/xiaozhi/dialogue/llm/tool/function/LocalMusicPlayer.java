package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.dialogue.llm.tool.media.LocalMusicLibrary;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LocalMusicPlayer implements ToolsGlobalRegistry.GlobalFunction {
    public static final String TOOL_NAME = "play_music";
    private final SessionManager sessions;
    private final LocalMusicLibrary library;
    private final DeviceMcpService deviceMcp;
    public LocalMusicPlayer(SessionManager sessions, LocalMusicLibrary library, DeviceMcpService deviceMcp) {
        this.sessions = sessions;
        this.library = library;
        this.deviceMcp = deviceMcp;
    }

    @Tool(name = TOOL_NAME, description = "Tìm bài hát và link audio trong thư viện rồi gọi MCP self.music.play trên app Android. Backend không giải mã hoặc phát nhạc. Nếu không rõ tên, gọi get_playlist trước. Chỉ phát khi tìm được duy nhất một bài.", returnDirect = true)
    public String playMusic(@ToolParam(description = "Tên bài hoặc tên file MP3; để trống khi người dùng muốn nghe một bài bất kỳ", required = false) String songName, ToolContext context) {
        Object sessionId = context == null ? null : context.getContext().get(Persona.TOOL_CONTEXT_SESSION_ID_KEY);
        var session = sessionId instanceof String id ? sessions.getSession(id) : null;
        if (session == null || session.getDevice() == null) return "Chưa có kết nối âm thanh với robot.";
        if (session.getToolsSessionHolder() == null || session.getToolsSessionHolder().getFunction("self_music_play") == null)
            return "App chưa có MCP nghe nhạc hoặc công cụ đang bị tắt. Hãy cập nhật app và kết nối lại.";
        try {
            boolean anySong = songName == null || songName.isBlank();
            var matches = anySong ? library.search("").stream().limit(1).toList() : library.resolve(songName);
            if (matches.isEmpty()) {
                var available = library.search("").stream().limit(5).map(LocalMusicLibrary.Track::title).toList();
                return available.isEmpty()
                        ? "Thư viện chưa có bài MP3 nào. Hãy thêm MP3 hoặc link audio vào thư viện để tôi phát."
                        : "Không tìm thấy bài này trong thư viện MP3 hiện tại. Tôi có thể phát: " + String.join(", ", available) + ".";
            }
            if (matches.size() > 1) return "Có nhiều bài phù hợp. Hãy chọn tên chính xác từ get_playlist trước khi phát.";
            var track = matches.getFirst();
            var result = deviceMcp.callDeviceTool(session.getDevice().getDeviceId(), "self.music.play",
                    Map.of("title", track.title(), "artist", track.artist(), "url", track.url()));
            if (Boolean.TRUE.equals(result.get("isError"))) return "App từ chối phát nhạc: " + result.get("content");
            return "Đã gửi bài “" + track.title() + "” tới app. App đang tải nhạc để phát trên loa điện thoại.";
        } catch (Exception e) {
            log.warn("Cannot play local music for session {}", sessionId, e);
            return "Không phát được bài hát. Hãy kiểm tra file MP3 và kết nối với app Android.";
        }
    }

    @Override public ToolCallback getFunctionCallTool(ToolSession session) { return ToolCallbacks.from(this)[0]; }
    @Override public String getToolName() { return TOOL_NAME; }
    @Override public String getToolDescription() { return "Phát nhạc qua loa robot"; }
}
