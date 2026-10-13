package com.xiaozhi.music;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.util.MusicCatalogFile;
import com.xiaozhi.common.util.MusicCatalogFile.Track;
import com.xiaozhi.dialogue.llm.tool.media.LocalMusicLibrary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class MusicCatalogServiceTest {
    @TempDir Path root;
    private RuntimePathConfig paths;
    private MusicCatalogService service;
    @BeforeEach void setup() { paths=new RuntimePathConfig();paths.setMusicDir(root.toString());service=new MusicCatalogService(paths); }
    @Test void uploadRenameAndResolveUseTheSameCatalog() throws Exception {
        var uploaded=service.upload("Xin chào.mp3",new byte[]{'I','D','3',1,2});
        assertThat(uploaded.tracks()).hasSize(1);
        var track=uploaded.tracks().getFirst();
        assertThat(track.title()).isEqualTo("Xin chào");
        var changed=service.save(uploaded.revision(),List.of(new Track(track.id(),"Bài hát Ken","Ca sĩ",track.fileName(),null)));
        var matches=new LocalMusicLibrary(paths).resolve("bai hat ken");
        assertThat(matches).hasSize(1);assertThat(matches.getFirst().artist()).isEqualTo("Ca sĩ");
        assertThat(changed.revision()).isNotEqualTo(uploaded.revision());
        assertThatThrownBy(()->service.save(uploaded.revision(),uploaded.tracks())).isInstanceOf(IllegalStateException.class).hasMessageContaining("tải lại");
        var deleted=service.delete(track.fileName(),changed.revision());
        assertThat(deleted.tracks()).isEmpty();assertThat(Files.exists(root.resolve(track.fileName()))).isFalse();
    }
    @Test void rejectsBadFilesTraversalLinksAndDuplicateMetadata() throws Exception {
        assertThatThrownBy(()->service.upload("../outside.mp3",new byte[]{'I','D','3'})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.upload("false.mp3",new byte[]{1,2,3})).isInstanceOf(IllegalArgumentException.class);
        var external=new Track("link","YouTube","",null,"https://youtu.be/abc");
        var catalog=service.save(service.get().revision(),List.of(external));
        assertThatThrownBy(()->service.save(catalog.revision(),List.of(external,external))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(catalog.revision(),List.of(new Track("id","Song","",null,"javascript:alert(1)")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(catalog.revision(),List.of(new Track("id","Song","","missing.mp3",null)))).isInstanceOf(IllegalArgumentException.class);
        Path outside=Files.createTempFile(root.getParent(),"mp3-", ".mp3");
        try { Files.createSymbolicLink(root.resolve("symlink.mp3"),outside);
            assertThat(service.get().tracks()).hasSize(1);
            assertThatThrownBy(()->service.delete("symlink.mp3",catalog.revision())).isInstanceOf(IllegalArgumentException.class);
        } finally { Files.deleteIfExists(outside); }
    }
    @Test void indexesLegacyFilesAndUrlCatalogWithoutDuplicates() throws Exception {
        Files.write(root.resolve("old.mp3"),new byte[]{'I','D','3'});
        Files.writeString(root.resolve("catalog.json"),"[{\"title\":\"Link\",\"url\":\"https://example.com/song.mp3\"}]");
        var catalog=service.get();assertThat(catalog.tracks()).hasSize(2);
        var saved=service.save(catalog.revision(),catalog.tracks());
        assertThat(service.get().revision()).isEqualTo(saved.revision());
        assertThat(new LocalMusicLibrary(paths).search("")).hasSize(2);
        var other=new MusicCatalogService(paths);
        other.save(saved.revision(),List.of(new Track("l","Another","",null,"https://example.com/song.mp3"),catalog.tracks().get(1)));
        assertThatThrownBy(()->service.save(saved.revision(),saved.tracks())).isInstanceOf(IllegalStateException.class).hasMessageContaining("tải lại");
    }
    @Test void refusesSymbolicCatalogAndOversizeCatalog() throws Exception {
        var outside=root.resolve("outside.json");Files.writeString(outside,"[]");
        Files.createSymbolicLink(root.resolve("catalog.json"),outside);
        assertThatThrownBy(service::get).isInstanceOf(java.io.IOException.class);
        Files.delete(root.resolve("catalog.json"));Files.writeString(root.resolve("catalog.json"),"x".repeat(1_000_001));
        assertThatThrownBy(service::get).isInstanceOf(java.io.IOException.class);
    }
}
