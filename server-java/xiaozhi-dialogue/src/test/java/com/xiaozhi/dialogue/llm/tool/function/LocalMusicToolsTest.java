package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.tool.media.LocalMusicLibrary;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import org.springframework.ai.tool.ToolCallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LocalMusicToolsTest {
    @TempDir Path root;
    LocalMusicLibrary library;
    LocalMusicPlayer music;
    PlayListGetter playlist;
    Player player;
    DeviceMcpService deviceMcp;
    ToolsSessionHolder holder;
    ToolContext context;

    @BeforeEach void setUp() {
        var paths = new RuntimePathConfig();
        paths.setMusicDir(root.toString());
        library = new LocalMusicLibrary(paths);
        playlist = new PlayListGetter(library);
        var sessions = mock(SessionManager.class);
        var session = mock(ChatSession.class);
        player = mock(Player.class);
        when(sessions.getSession("s1")).thenReturn(session);
        when(session.getPlayer()).thenReturn(player);
        var device = new DeviceBO(); device.setDeviceId("test-device");
        when(session.getDevice()).thenReturn(device);
        holder = new ToolsSessionHolder("s1", device, null);
        holder.registerFunction("self_music_play", mock(ToolCallback.class));
        when(session.getToolsSessionHolder()).thenReturn(holder);
        deviceMcp = mock(DeviceMcpService.class);
        when(deviceMcp.callDeviceTool(anyString(), anyString(), anyMap())).thenReturn(Map.of("content", List.of()));
        music = new LocalMusicPlayer(sessions, library, deviceMcp);
        context = new ToolContext(Map.of(Persona.TOOL_CONTEXT_SESSION_ID_KEY, "s1"));
    }

    @Test void searchesVietnameseAndIgnoresNonTracks() throws Exception {
        Files.writeString(root.resolve("Đường về - Hà Anh.MP3"), "fixture");
        Files.writeString(root.resolve("playlist.txt"), "obsolete");
        Files.createDirectory(root.resolve("folder.mp3"));
        assertThat(library.search("duong ve")).extracting(LocalMusicLibrary.Track::fileName)
                .containsExactly("Đường về - Hà Anh.MP3");
        assertThat(playlist.getPlayList(null)).contains("Đường về").doesNotContain("obsolete", "folder.mp3");
    }

    @Test void rejectsTraversalAndSymlinks() throws Exception {
        Path outside = Files.createTempFile(root.getParent(), "outside-music-", ".mp3");
        try {
            Files.createSymbolicLink(root.resolve("escape.mp3"), outside);
            assertThat(library.resolve("../" + outside.getFileName())).isEmpty();
            assertThat(library.resolve(outside.toString())).isEmpty();
            assertThat(library.resolve("escape")).isEmpty();
            assertThat(library.resolve(".mp3")).isEmpty();
        } finally { Files.deleteIfExists(outside); }
    }

    @Test void ambiguousMatchesNeverPlayButExactNameDoes() throws Exception {
        try (var tone = getClass().getResourceAsStream("/music/tone.mp3")) {
            Files.copy(tone, root.resolve("Mưa.mp3"));
        }
        Files.writeString(root.resolve("Mưa đêm.mp3"), "fixture");
        assertThat(music.playMusic("mu", context)).contains("nhiều bài");
        verifyNoInteractions(player);
        assertThat(music.playMusic("Mưa.mp3", context)).contains("tới app");
        verify(deviceMcp).callDeviceTool("test-device", "self.music.play", Map.of(
                "title", "Mưa", "artist", "", "url", "/api/robot/music/audio?name=M%C6%B0a.mp3"));
        verifyNoInteractions(player);
    }

    @Test void handlesMissingSessionMissingLibraryAndDecoderFailure() throws Exception {
        assertThat(music.playMusic("song", null)).contains("Chưa có");
        assertThat(music.playMusic("song", context)).contains("Thư viện chưa có");
        Files.writeString(root.resolve("song.mp3"), "fixture");
        when(deviceMcp.callDeviceTool(anyString(), anyString(), anyMap())).thenThrow(new IllegalStateException("Device unavailable"));
        assertThat(music.playMusic("song", context)).contains("Không phát được");
        verifyNoInteractions(player);
    }

    @Test void registersBothToolsAndOptionalSearchSchema() {
        var registry = new ToolsGlobalRegistry();
        ReflectionTestUtils.setField(registry, "globalFunctions", List.of(music, playlist));
        assertThat(registry.getAllFunctions(null)).containsKeys("play_music", "get_playlist");
        var callback = playlist.getFunctionCallTool(null);
        assertThat(callback.call("{}", context)).contains("Không tìm thấy");
        assertThat(music.getFunctionCallTool(null).getToolMetadata().returnDirect()).isTrue();
    }

    @Test void resolvesOnlineLinkAndRespectsMissingDeviceTool() throws Exception {
        Files.writeString(root.resolve("catalog.json"), """
                [{"title":"Một nhà","artist":"Da LAB","url":"https://music.example/one.mp3"},
                 {"title":"invalid","url":"file:///secret.mp3"}]
                """);
        assertThat(library.search("da lab")).hasSize(1);
        assertThat(library.resolve("mot nha - da lab")).hasSize(1);
        assertThat(library.resolve("invalid")).isEmpty();
        assertThat(music.playMusic("mot nha", context)).contains("tới app");
        verify(deviceMcp).callDeviceTool("test-device", "self.music.play", Map.of(
                "title", "Một nhà", "artist", "Da LAB", "url", "https://music.example/one.mp3"));
        holder.unregisterFunction("self_music_play");
        assertThat(music.playMusic("mot nha", context)).contains("App chưa có MCP");
        verifyNoInteractions(player);
    }

    @Test void playlistStaysBounded() throws Exception {
        for (int i = 0; i < 50; i++) Files.writeString(root.resolve("Song " + i + ".mp3"), "fixture");
        assertThat(playlist.getPlayList("")).hasSizeLessThan(2001).contains("Còn bài khác");
        assertThat(playlist.getPlayList("Song 49")).contains("Song 49.mp3").doesNotContain("Song 48.mp3");
    }

    @Test void anySongRequestSelectsAvailableTrackAndMissingTitleSuggestsRealTracks() throws Exception {
        Files.writeString(root.resolve("Nhạc thử Ken.mp3"), "fixture");
        assertThat(music.playMusic("", context)).contains("Nhạc thử Ken", "tới app");
        verify(deviceMcp).callDeviceTool(eq("test-device"), eq("self.music.play"), argThat(args -> "Nhạc thử Ken".equals(args.get("title"))));
        clearInvocations(deviceMcp);
        assertThat(music.playMusic("đến thời gian", context)).contains("Không tìm thấy", "Nhạc thử Ken");
        verifyNoInteractions(deviceMcp);
    }
}
