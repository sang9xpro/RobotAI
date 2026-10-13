package com.xiaozhi.common.model.resp;

import java.util.List;

public record PersonalMemoryResp(boolean enabled,long revision,List<FactResp> facts) {
    public record FactResp(String key,String value) {}
}
