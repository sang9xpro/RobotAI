package com.xiaozhi.music;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.config.RuntimePathConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RobotMusicAudioControllerTest {
    @TempDir Path root;
    @Test void streamsOriginalBytesAndSupportsRangeAfterAuthentication() throws Exception {
        var config = new RuntimePathConfig(); config.setMusicDir(root.toString());
        var controller = new RobotMusicAudioController(config);
        Files.write(root.resolve("tone.mp3"), new byte[]{1,2,3,4,5});
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        try (var auth = mockStatic(StpUtil.class)) {
            mvc.perform(get("/api/robot/music/audio").param("name", "tone.mp3"))
                    .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3,4,5}));
            mvc.perform(get("/api/robot/music/audio").param("name", "tone.mp3").header("Range", "bytes=1-3"))
                    .andExpect(status().isPartialContent()).andExpect(content().bytes(new byte[]{2,3,4}));
            auth.verify(StpUtil::checkLogin, org.mockito.Mockito.times(2));
        }
    }
    @Test void rejectsTraversalAndSymlink() throws Exception {
        var config = new RuntimePathConfig(); config.setMusicDir(root.toString());
        var controller = new RobotMusicAudioController(config);
        try (var auth = mockStatic(StpUtil.class)) {
            assertThat(controller.audio("../escape.mp3").getStatusCode().value()).isEqualTo(400);
            assertThat(controller.audio("missing.mp3").getStatusCode().value()).isEqualTo(404);
            var outside = Files.createTempFile(root.getParent(), "music-outside-", ".mp3");
            try {
                Files.createSymbolicLink(root.resolve("link.mp3"), outside);
                assertThat(controller.audio("link.mp3").getStatusCode().value()).isEqualTo(404);
            } finally { Files.deleteIfExists(outside); }
        }
    }
}
