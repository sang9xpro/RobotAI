package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.dialogue.llm.tool.media.LocalMusicLibrary;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class PlayListGetter implements ToolsGlobalRegistry.GlobalFunction {
    public static final String TOOL_NAME = "get_playlist";
    private final LocalMusicLibrary library;
    public PlayListGetter(LocalMusicLibrary library) { this.library = library; }

    @Tool(name = TOOL_NAME, description = "Liệt kê hoặc tìm bài hát trong thư viện nhạc của robot. Tìm không phân biệt dấu tiếng Việt; dùng tên kết quả để gọi play_music.")
    public String getPlayList(@ToolParam(required = false, description = "Tên bài hoặc ca sĩ; bỏ trống để liệt kê") String query) {
        try {
            var tracks = library.search(query);
            if (tracks.isEmpty()) return "Không tìm thấy bài hát trong thư viện nhạc.";
            StringBuilder result = new StringBuilder("Bài hát có thể phát:\n");
            int count = 0;
            for (var track : tracks) {
                if (count == 30 || result.length() + track.fileName().length() + track.artist().length() + 6 > 1900) break;
                result.append("- ").append(track.fileName()).append(track.artist().isBlank() ? "" : " - " + track.artist()).append('\n');
                count++;
            }
            if (count < tracks.size()) result.append("Còn bài khác; hãy tìm bằng tên bài hoặc ca sĩ.");
            return result.toString();
        } catch (IOException e) {
            return "Không đọc được thư viện nhạc. Hãy kiểm tra thư mục nhạc trên backend.";
        }
    }

    @Override public ToolCallback getFunctionCallTool(ToolSession session) { return ToolCallbacks.from(this)[0]; }
    @Override public String getToolName() { return TOOL_NAME; }
    @Override public String getToolDescription() { return "Tìm và liệt kê bài hát"; }
}
