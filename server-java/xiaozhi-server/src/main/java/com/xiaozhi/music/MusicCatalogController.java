package com.xiaozhi.music;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.model.req.MusicCatalogReq;
import com.xiaozhi.common.model.resp.MusicCatalogResp;
import com.xiaozhi.common.util.MusicCatalogFile.Track;
import com.xiaozhi.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
@RestController
@RequestMapping("/api/robot/music")
public class MusicCatalogController {
    private final MusicCatalogService service;
    public MusicCatalogController(MusicCatalogService service) { this.service=service; }
    private MusicCatalogResp response(MusicCatalogService.Catalog catalog) {
        return new MusicCatalogResp(catalog.revision(),catalog.tracks().stream().map(t->
            new MusicCatalogResp.TrackResp(t.id(),t.title(),t.artist(),t.fileName(),t.url())).toList());
    }
    @GetMapping("/catalog") @SaCheckPermission("system:robot:music:list")
    public ApiResponse<MusicCatalogResp> get() throws IOException { return ApiResponse.success(response(service.get())); }
    @PutMapping("/catalog") @SaCheckPermission("system:robot:music:update")
    public ApiResponse<MusicCatalogResp> save(@Valid @RequestBody MusicCatalogReq req) throws IOException {
        return ApiResponse.success(response(service.save(req.revision(),req.tracks().stream().map(t->
            new Track(t.id(),t.title(),t.artist(),t.fileName(),t.url())).toList())));
    }
    @PostMapping("/files") @SaCheckPermission("system:robot:music:update")
    public ApiResponse<MusicCatalogResp> upload(@RequestParam MultipartFile file) throws IOException {
        if(file.isEmpty() || file.getSize()>30_000_000) throw new IllegalArgumentException("Invalid MP3 (max 30 MB)");
        return ApiResponse.success(response(service.upload(file.getOriginalFilename(),file.getBytes())));
    }
    @DeleteMapping("/files/{name}") @SaCheckPermission("system:robot:music:update")
    public ApiResponse<MusicCatalogResp> delete(@PathVariable String name,@RequestParam String revision) throws IOException {
        return ApiResponse.success(response(service.delete(name,revision)));
    }
}
