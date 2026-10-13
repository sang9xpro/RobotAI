package com.xiaozhi.common.model.req;

import com.xiaozhi.common.annotation.Sensitive;
import com.xiaozhi.common.model.req.AdminUserDevicesReq.Assignment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** Administrative creation is separate from anonymous registration and its captcha. */
@Data
public class AdminUserCreateReq {
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,20}", message = "Tên đăng nhập cần 3–20 ký tự: chữ, số, dấu chấm, gạch ngang hoặc gạch dưới")
    private String username;
    @NotBlank @Size(min = 6, max = 20, message = "Mật khẩu cần 6–20 ký tự") @Sensitive
    private String password;
    @Size(max = 50) private String name;
    @Email @Size(max = 100) private String email;
    @Pattern(regexp = "\\+?[0-9]{8,20}", message = "Số điện thoại không hợp lệ") private String tel;
    @Positive private Integer authRoleId;
    @Size(max = 20)
    private List<@NotNull @Valid Assignment> devices;
}
