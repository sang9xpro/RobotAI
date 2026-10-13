package com.xiaozhi.common.model.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class AdminUserDevicesReq {
    @NotNull @Size(min = 1, max = 20)
    private List<@NotNull @Valid Assignment> devices;

    @Data
    public static class Assignment {
        @NotBlank @Pattern(regexp = "(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}", message = "ID robot không hợp lệ")
        private String deviceId;
        /** Owner displayed when selected; prevents silently overwriting another admin's assignment. */
        @Positive private Integer expectedUserId;
    }
}
