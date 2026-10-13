package com.xiaozhi.dialogue.llm.tool.media;

import com.xiaozhi.common.config.RuntimePathConfig;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Resolve titles/artists to external audio URLs or protected HTTP URLs for local MP3s. */
@Service
public class LocalMusicLibrary {
    private final RuntimePathConfig paths;
    public LocalMusicLibrary(RuntimePathConfig paths) { this.paths = paths; }
    public record Track(String title, String fileName, Path path, String url, String artist) {}

    public List<Track> search(String query) throws IOException {
        String key = normalize(query);
        Path root = paths.resolveMusicDir();
        if (!Files.exists(root)) return List.of();
        List<Track> tracks = new ArrayList<>();
        var catalog = com.xiaozhi.common.util.MusicCatalogFile.read(root);
        var indexed = new java.util.HashSet<String>();
        for (var row : catalog) {
            if (row.fileName() != null) {
                Path file = root.resolve(row.fileName());
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                indexed.add(row.fileName());
                tracks.add(new Track(row.title(), row.fileName(), file,
                        "/api/robot/music/audio?name=" + URLEncoder.encode(row.fileName(), StandardCharsets.UTF_8), row.artist()));
            } else tracks.add(new Track(row.title(), row.title(), null, row.url(), row.artist()));
        }
        try (var files = Files.list(root)) {
            tracks.addAll(files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
                    .filter(p -> !indexed.contains(p.getFileName().toString()))
                    .map(p -> {
                        String name = p.getFileName().toString();
                        return new Track(name.substring(0, name.length() - 4), name, p,
                                "/api/robot/music/audio?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8), "");
                    })
                    .toList());
        }
        return tracks.stream().filter(t -> normalize(t.title() + " " + t.artist()).contains(key))
                .sorted(java.util.Comparator.comparing(Track::fileName)).toList();
    }

    public List<Track> resolve(String songName) throws IOException {
        if (songName == null || songName.isBlank() || songName.contains("/") || songName.contains("\\")) return List.of();
        String name = songName.strip();
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp3")) name = name.substring(0, name.length() - 4);
        if (name.isBlank()) return List.of();
        final String title = name;
        List<Track> candidates = search(title);
        List<Track> withArtist = candidates.stream().filter(t -> !t.artist().isBlank()
                && normalize(t.title() + " " + t.artist()).equals(normalize(title))).toList();
        if (!withArtist.isEmpty()) return withArtist;
        List<Track> exact = candidates.stream().filter(t -> t.title().equalsIgnoreCase(title)).toList();
        if (!exact.isEmpty()) return exact;
        List<Track> normalized = candidates.stream().filter(t -> normalize(t.title()).equals(normalize(title))).toList();
        return normalized.isEmpty() ? candidates : normalized;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[\\p{Punct}“”‘’]+", " ").replace('đ', 'd').replaceAll("\\s+", " ").strip();
    }
}
