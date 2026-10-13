package com.xiaozhi.music;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import java.nio.file.Path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class MusicCatalogControllerTest extends ControllerTestSupport {
    @TempDir Path root;
    private MusicCatalogService service() {
        var paths = new RuntimePathConfig(); paths.setMusicDir(root.toString());
        return new MusicCatalogService(paths);
    }
    @Test void staleSaveAndDeleteReturnConflictThroughGlobalAdviceWithoutRemovingAudio() throws Exception {
        var service = service();
        var catalog = service.upload("Ken.mp3", new byte[]{'I','D','3'});
        var mvc = buildMockMvc(new MusicCatalogController(service));
        mvc.perform(put("/api/robot/music/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":\"stale\",\"tracks\":[]}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("Thư viện đã thay đổi. Hãy tải lại trước khi lưu."));
        mvc.perform(delete("/api/robot/music/files/" + catalog.tracks().getFirst().fileName()).param("revision", "stale"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        assertThat(service.get()).isEqualTo(catalog);
    }
    @Test void invalidUrlIsRejectedAtTheBoundary() throws Exception {
        var mvc = buildMockMvc(new MusicCatalogController(service()));
        mvc.perform(put("/api/robot/music/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":\"r\",\"tracks\":[{\"id\":\"one\",\"title\":\"Ken\",\"artist\":\"\",\"url\":\"javascript:alert(1)\"}]}"))
            .andExpect(status().isConflict());
        var service = service();
        var revision = service.get().revision();
        mvc = buildMockMvc(new MusicCatalogController(service));
        mvc.perform(put("/api/robot/music/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":\"" + revision + "\",\"tracks\":[{\"id\":\"one\",\"title\":\"Ken\",\"artist\":\"\",\"url\":\"javascript:alert(1)\"}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        assertThat(service.get().tracks()).isEmpty();
    }
}
