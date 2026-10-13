package com.xiaozhi.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Shared disk format for administration and dialogue. Legacy URL-only rows remain readable. */
public final class MusicCatalogFile {
    private MusicCatalogFile() {}
    public record Track(String id, String title, String artist, String fileName, String url) {}
    public static List<Track> read(Path root) throws IOException {
        Path file = root.resolve("catalog.json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file)>1_000_000)
            throw new IOException("Invalid music catalog file");
        var rows = new ObjectMapper().readTree(Files.readString(file));
        if (!rows.isArray() || rows.size()>2000) throw new IOException("Invalid music catalog");
        List<Track> tracks=new ArrayList<>();
        for(var row:rows) {
            String name=row.path("fileName").asText(null), url=row.path("url").asText(null);
            Track track=new Track(row.path("id").asText("legacy-"+tracks.size()),row.path("title").asText(""),
                row.path("artist").asText(""),name,url);
            try { validate(track); tracks.add(track); } catch(IllegalArgumentException ignored) { /* legacy invalid row */ }
        }
        return List.copyOf(tracks);
    }
    public static void validate(Track t) {
        if(t==null || t.id()==null || t.id().isBlank() || t.id().length()>200 || t.title()==null || t.title().isBlank()
            || t.title().length()>300 || t.artist()==null || t.artist().length()>300)
            throw new IllegalArgumentException("Invalid track metadata");
        boolean local=t.fileName()!=null && !t.fileName().isBlank();
        boolean external=t.url()!=null && !t.url().isBlank();
        if(local==external) throw new IllegalArgumentException("Choose an MP3 file or URL");
        if(local) basename(t.fileName());
        else {
            URI uri=URI.create(t.url());
            if(t.url().length()>4096 || !Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null
                || uri.getUserInfo()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("Invalid HTTP(S) music URL");
        }
    }
    public static String basename(String name) {
        if(name==null || name.isBlank() || name.length()>200 || name.contains("/") || name.contains("\\")
            || name.chars().anyMatch(Character::isISOControl) || !name.toLowerCase(Locale.ROOT).endsWith(".mp3"))
            throw new IllegalArgumentException("Invalid MP3 file name");
        return name;
    }
    public static void write(Path root,List<Track> tracks) throws IOException {
        if(tracks.size()>2000) throw new IllegalArgumentException("Too many tracks");
        Set<String> ids=new HashSet<>(),names=new HashSet<>();
        for(Track t:tracks) {
            validate(t);
            if(!ids.add(t.id()) || (t.fileName()!=null && !names.add(t.fileName())))
                throw new IllegalArgumentException("Duplicate track");
        }
        byte[] data=new ObjectMapper().writeValueAsBytes(tracks);
        if(data.length>1_000_000) throw new IllegalArgumentException("Catalog too large");
        Files.createDirectories(root);
        if(Files.isSymbolicLink(root.resolve("catalog.json"))) throw new IOException("Invalid catalog path");
        Path tmp=Files.createTempFile(root,".catalog-",".tmp");
        try {
            Files.write(tmp,data);
            Files.move(tmp,root.resolve("catalog.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }
}
