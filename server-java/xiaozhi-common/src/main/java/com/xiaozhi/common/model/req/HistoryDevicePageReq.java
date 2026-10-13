package com.xiaozhi.common.model.req;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class HistoryDevicePageReq extends BasePageReq {
    @Size(max = 100) private String search;
    @Size(max = 255) private String deviceId;
}
