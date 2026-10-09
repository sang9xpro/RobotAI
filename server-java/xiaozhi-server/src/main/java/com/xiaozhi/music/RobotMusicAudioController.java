package com.xiaozhi.music;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.config.RuntimePathConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Android streams the original MP3 over HTTP; dialogue never encodes music as speech. */
@RestController
@RequestMapping("/api/robot/music")
public class RobotMusicAudioController {
    private final RuntimePathConfig paths;
    public RobotMusicAudioController(RuntimePathConfig paths) { this.paths = paths; }
    @GetMapping("/audio")
    public ResponseEntity<Resource> audio(@RequestParam String name) {
        StpUtil.checkLogin();
        if (name.isBlank() || name.contains("/") || name.contains("\\") || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3"))
            return ResponseEntity.badRequest().build();
        var root = paths.resolveMusicDir();
        var path = root.resolve(name).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("Cache-Control", "private, no-store").body(new FileSystemResource(path));
    }
}
