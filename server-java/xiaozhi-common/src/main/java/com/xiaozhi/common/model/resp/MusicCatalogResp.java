package com.xiaozhi.common.model.resp;
import java.util.List;
public record MusicCatalogResp(String revision,List<TrackResp> tracks) {
    public record TrackResp(String id,String title,String artist,String fileName,String url) {}
}
