package com.xiaozhi.common.model.req;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record MusicCatalogReq(@NotBlank String revision,@NotNull @Size(max=2000) List<@Valid TrackReq> tracks) {
    public record TrackReq(@NotBlank @Size(max=200) String id,@NotBlank @Size(max=300) String title,
        @NotNull @Size(max=300) String artist,String fileName,@Size(max=4096) String url) {}
}
