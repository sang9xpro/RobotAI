package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.dialogue.runtime.Persona;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class YouTubeMusicPlayerTest {
    @Test void registeredToolAcceptsSongNameOnlyFromExplicitRouter() {
        var sessions = mock(SessionManager.class);
        var session = mock(ChatSession.class);
        var device = new DeviceBO(); device.setDeviceId("oppo");
        var holder = new ToolsSessionHolder("s1", device, null);
        holder.registerFunction("self_music_search", mock(ToolCallback.class));
        when(sessions.getSession("s1")).thenReturn(session);
        when(session.getDevice()).thenReturn(device);
        when(session.getToolsSessionHolder()).thenReturn(holder);
        var deviceMcp = mock(DeviceMcpService.class);
        when(deviceMcp.callDeviceTool(anyString(), anyString(), anyMap())).thenReturn(Map.of("content", List.of()));
        var player = new YouTubeMusicPlayer(sessions, deviceMcp);
        var context = new ToolContext(Map.of(Persona.TOOL_CONTEXT_SESSION_ID_KEY, "s1"));
        assertThat(player.getFunctionCallTool(null).call("{\"songName\":\"Đếm thời gian\"}", context)).contains("app tìm");
        verify(deviceMcp).callDeviceTool("oppo", "self.music.search", Map.of(
                "title", "Đếm thời gian", "artist", "", "searchQuery", "Đếm thời gian"));
    }

    @Test void sendsSongAndKeywordToAndroidWithoutServerSearch() {
        var sessions = mock(SessionManager.class);
        var session = mock(ChatSession.class);
        var device = new DeviceBO(); device.setDeviceId("oppo");
        var holder = new ToolsSessionHolder("s1", device, null);
        holder.registerFunction("self_music_search", mock(ToolCallback.class));
        when(sessions.getSession("s1")).thenReturn(session);
        when(session.getDevice()).thenReturn(device);
        when(session.getToolsSessionHolder()).thenReturn(holder);
        var deviceMcp = mock(DeviceMcpService.class);
        when(deviceMcp.callDeviceTool(anyString(), anyString(), anyMap())).thenReturn(Map.of("content", List.of()));
        var player = new YouTubeMusicPlayer(sessions, deviceMcp);
        var context = new ToolContext(Map.of(Persona.TOOL_CONTEXT_SESSION_ID_KEY, "s1"));
        assertThat(player.playYoutube("Đếm thời gian", "Đếm thời gian NDMT", "NDMT", context)).contains("app tìm");
        verify(deviceMcp).callDeviceTool("oppo", "self.music.search", Map.of(
                "title", "Đếm thời gian", "artist", "NDMT", "searchQuery", "Đếm thời gian NDMT"));
        assertThat(player.playYoutube("https://youtu.be/M7lc1UVf-VE", context)).contains("app tìm");
        verify(deviceMcp).callDeviceTool("oppo", "self.music.search", Map.of(
                "title", "Video YouTube", "artist", "", "searchQuery", "https://youtu.be/M7lc1UVf-VE",
                "videoUrl", "https://www.youtube.com/watch?v=M7lc1UVf-VE"));
    }

    @Test void directLinksMustUseRealYoutubeHostsAndSearchToolMustBeConnected() {
        assertThat(YouTubeMusicPlayer.videoId("https://youtu.be/M7lc1UVf-VE")).isEqualTo("M7lc1UVf-VE");
        assertThat(YouTubeMusicPlayer.videoId("https://youtube.com.evil.test/watch?v=M7lc1UVf-VE")).isNull();
        var sessions = mock(SessionManager.class);
        var session = mock(ChatSession.class);
        var device = new DeviceBO(); device.setDeviceId("oppo");
        when(sessions.getSession("s1")).thenReturn(session);
        when(session.getDevice()).thenReturn(device);
        when(session.getToolsSessionHolder()).thenReturn(new ToolsSessionHolder("s1", device, null));
        var deviceMcp = mock(DeviceMcpService.class);
        var context = new ToolContext(Map.of(Persona.TOOL_CONTEXT_SESSION_ID_KEY, "s1"));
        assertThat(new YouTubeMusicPlayer(sessions, deviceMcp).playYoutube("Đếm thời gian", context))
                .contains("chưa có MCP tìm nhạc YouTube");
        verifyNoInteractions(deviceMcp);
    }
}
