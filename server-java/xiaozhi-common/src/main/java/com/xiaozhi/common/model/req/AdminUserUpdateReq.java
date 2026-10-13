package com.xiaozhi.common.model.req;

import jakarta.validation.constraints.*;
import lombok.Data;

/** Whitelisted profile fields: no password, administrator flag or state changes. */
@Data
public class AdminUserUpdateReq {
    @Size(max = 50) private String name;
    @Email @Size(max = 100) private String email;
    @Pattern(regexp = "(?:\\+?[0-9]{8,20})?", message = "Số điện thoại không hợp lệ") private String tel;
    @Positive private Integer authRoleId;
}
