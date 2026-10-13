package com.xiaozhi.common.model.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceHistoryPageReq extends MessagePageReq {
    @Size(max = 200) private String keyword;
    @Pattern(regexp = "asc|desc") private String order = "desc";
    public DeviceHistoryPageReq() { setPageSize(20); }
    @Override @Max(100) public Integer getPageSize() { return super.getPageSize(); }
    @AssertTrue(message = "Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc")
    public boolean isTimeRangeValid() {
        return getStartTime() == null || getEndTime() == null || !getStartTime().isAfter(getEndTime());
    }
}
