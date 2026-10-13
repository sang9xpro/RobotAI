package com.xiaozhi.music;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.util.MusicCatalogFile;
import com.xiaozhi.common.util.MusicCatalogFile.Track;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

@Service
public class MusicCatalogService {
    private final Path root;
    public MusicCatalogService(RuntimePathConfig paths) { root=paths.resolveMusicDir(); }
    public record Catalog(String revision,List<Track> tracks) {}
    public synchronized Catalog get() throws IOException { return read(); }
    private Catalog read() throws IOException {
        List<Track> tracks=new ArrayList<>(MusicCatalogFile.read(root));
        Set<String> indexed=new HashSet<>();
        tracks.forEach(t->{ if(t.fileName()!=null) indexed.add(t.fileName()); });
        if(Files.isDirectory(root)) try(var files=Files.list(root)) {
            for(Path file:files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")).sorted().toList()) {
                String name=file.getFileName().toString();
                if(!indexed.contains(name)) tracks.add(new Track("file-"+UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name.substring(0,name.length()-4),"",name,null));
            }
        }
        if(tracks.size()>2000) throw new IOException("Too many tracks");
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            Path catalog=root.resolve("catalog.json");
            if(Files.isRegularFile(catalog,LinkOption.NOFOLLOW_LINKS)) hash.update(Files.readAllBytes(catalog));
            for(Track t:tracks) if(t.fileName()!=null) {
                Path file=root.resolve(t.fileName());
                if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) hash.update((t.fileName()+":"+Files.size(file)+":"+
                    Files.getLastModifiedTime(file).toMillis()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return new Catalog(HexFormat.of().formatHex(hash.digest()),List.copyOf(tracks));
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void check(String revision) throws IOException {
        if(!Objects.equals(revision,read().revision())) throw new IllegalStateException("Thư viện đã thay đổi. Hãy tải lại trước khi lưu.");
    }
    private interface Operation { Catalog run() throws IOException; }
    private synchronized Catalog locked(Operation work) throws IOException {
        Files.createDirectories(root);
        Path lock=root.resolve(".catalog.lock");
        try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            var ignored=channel.lock()) { return work.run(); }
    }
    public Catalog save(String revision,List<Track> tracks) throws IOException {
        return locked(()->{
            check(revision);
            for(Track t:tracks) {
                MusicCatalogFile.validate(t);
                if(t.fileName()!=null && !Files.isRegularFile(root.resolve(t.fileName()),LinkOption.NOFOLLOW_LINKS))
                    throw new IllegalArgumentException("MP3 file not found");
            }
            MusicCatalogFile.write(root,tracks); return read();
        });
    }
    public Catalog upload(String original,byte[] bytes) throws IOException {
        MusicCatalogFile.basename(original);
        if(bytes.length<3 || bytes.length>30_000_000 || !((bytes[0]=='I'&&bytes[1]=='D'&&bytes[2]=='3')
            || ((bytes[0]&255)==255 && (bytes[1]&224)==224))) throw new IllegalArgumentException("Invalid MP3 (max 30 MB)");
        return locked(()->{
            if(read().tracks().size()>=2000) throw new IllegalArgumentException("Too many tracks");
            String name=UUID.randomUUID()+".mp3";
            Files.write(root.resolve(name),bytes,StandardOpenOption.CREATE_NEW);
            List<Track> tracks=new ArrayList<>(MusicCatalogFile.read(root));
            tracks.add(new Track(name,original.substring(0,original.length()-4),"",name,null));
            try { MusicCatalogFile.write(root,tracks); }
            catch(Exception e) { Files.deleteIfExists(root.resolve(name));throw e; }
            return read();
        });
    }
    public Catalog delete(String name,String revision) throws IOException {
        MusicCatalogFile.basename(name);
        return locked(()->{
            check(revision); Path file=root.resolve(name);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("MP3 file not found");
            Path trash=root.resolve(".deleted-"+UUID.randomUUID());
            Files.move(file,trash,StandardCopyOption.ATOMIC_MOVE);
            try { MusicCatalogFile.write(root,MusicCatalogFile.read(root).stream().filter(t->!name.equals(t.fileName())).toList()); }
            catch(Exception e) { Files.move(trash,file,StandardCopyOption.ATOMIC_MOVE);throw e; }
            Files.deleteIfExists(trash);return read();
        });
    }
}
