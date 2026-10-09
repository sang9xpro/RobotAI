package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.llm.tool.media.MusicCommandRouter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MusicCommandRouterTest {
    private final ToolsSessionHolder holder = new ToolsSessionHolder("music-test", null, null);
    private final ToolContext context = new ToolContext(Map.of("sessionId", "music-test"));

    @Test void routesExplicitPlayWithExactArguments() {
        var tool = mock(ToolCallback.class);
        holder.registerFunction("play_music", tool);
        when(tool.call("{\"songName\":\"Một nhà\"}", context)).thenReturn("\"Đã phát\"");
        assertThat(MusicCommandRouter.route("Ken ơi, mở bài Một nhà nhé!", holder, context)).contains("Đã phát");
        verify(tool).call("{\"songName\":\"Một nhà\"}", context);
    }

    @Test void routesListAndSearch() {
        var tool = mock(ToolCallback.class);
        holder.registerFunction("get_playlist", tool);
        when(tool.call("{}", context)).thenReturn("Danh sách");
        when(tool.call("{\"query\":\"Mưa\"}", context)).thenReturn("Mưa.mp3");
        assertThat(MusicCommandRouter.route("Có những bài hát nào?", holder, context)).contains("Danh sách");
        assertThat(MusicCommandRouter.route("Tìm bài Mưa", holder, context)).contains("Mưa.mp3");
    }

    @Test void disabledToolsNeverExecute() {
        assertThat(MusicCommandRouter.route("Phát bài Một nhà", holder, context).orElseThrow()).contains("bị tắt");
    }

    @Test void ordinaryConversationAndQuotedRequestsDoNotTriggerPlayback() {
        for (String text : new String[]{"Tôi thích bài Một nhà", "Đừng phát bài Một nhà", "Mở cửa", "Nếu tôi nói phát bài Một nhà thì sao?", ".Ken ơi đừng mở nhạc.", "Tôi không muốn nghe nhạc", "\"mở nhạc\" nghĩa là gì?"})
            assertThat(MusicCommandRouter.route(text, holder, context)).isEmpty();
    }

    @Test void routesActualPhoneTranscriptsAndAnySongRequests() {
        var tool = mock(ToolCallback.class);
        holder.registerFunction("play_music", tool);
        when(tool.call("{\"songName\":\"\"}", context)).thenReturn("Đã gửi bài tới app");
        for (String command : new String[]{".kèn người mở một bài hát cho nghe.", "Ken ơi, mở một bài hát cho tôi nghe nhé!", "Mở nhạc", "Bật cho tôi một bài hát", "Cho tôi nghe nhạc", "Tôi muốn nghe một bài hát bất kỳ", "Phát 1 bài hát"})
            assertThat(MusicCommandRouter.route(command, holder, context)).as(command).contains("Đã gửi bài tới app");
    }

    @Test void leadingSttPunctuationDoesNotSendSongRequestToLanguageModel() {
        var tool = mock(ToolCallback.class);
        holder.registerFunction("play_music", tool);
        when(tool.call("{\"songName\":\"đến thời gian\"}", context)).thenReturn("Không tìm thấy bài này");
        assertThat(MusicCommandRouter.route(".bật cho tôi bài đến thời gian.", holder, context)).contains("Không tìm thấy bài này");
    }

    @Test void routesNamedYoutubeRequestToYoutubeToolWithoutSourceWords() {
        var tool = mock(ToolCallback.class);
        holder.registerFunction("play_youtube", tool);
        when(tool.call("{\"songName\":\"Đếm thời gian\"}", context)).thenReturn("Đã mở video");
        assertThat(MusicCommandRouter.route("Mở bài hát Đếm thời gian trên youtube", holder, context)).contains("Đã mở video");
        verify(tool).call("{\"songName\":\"Đếm thời gian\"}", context);
    }

    @Test void capabilityQuestionUsesAvailableToolsWithoutStartingPlayback() {
        var playlist = mock(ToolCallback.class);
        var play = mock(ToolCallback.class);
        holder.registerFunction("get_playlist", playlist);
        holder.registerFunction("play_music", play);
        holder.registerFunction("self_music_play", play);
        when(playlist.call("{}", context)).thenReturn("Nhạc thử Ken");
        for (String question : new String[]{"Bạn có thể nghe nhạc không?", "Ken có phát nhạc mp3 được không?", "Phát nhạc được không?"})
            assertThat(MusicCommandRouter.route(question, holder, context).orElseThrow()).contains("có thể phát MP3", "Nhạc thử Ken");
        verifyNoInteractions(play);
    }
}
