package com.xiaozhi.user;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.req.*;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.common.model.resp.AdminRobotResp;
import com.xiaozhi.common.web.ApiResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@SaCheckRole("admin")
public class UserAdminController {
    @Resource private UserAdminAppService service;

    @PostMapping
    @SaCheckPermission("system:user:api:create")
    @AuditLog(module = "用户管理", operation = "管理员创建用户")
    public ApiResponse<UserResp> create(@Valid @RequestBody AdminUserCreateReq req) {
        if (req.getDevices() != null && !req.getDevices().isEmpty()) {
            StpUtil.checkPermission("system:user:devices:api:bind");
        }
        return ApiResponse.success(service.create(req));
    }

    @GetMapping("/{userId}")
    @SaCheckPermission("system:user:api:manage")
    public ApiResponse<UserResp> get(@PathVariable Integer userId) {
        return ApiResponse.success(service.get(userId));
    }

    @PutMapping("/{userId}")
    @SaCheckPermission("system:user:api:manage")
    @AuditLog(module = "用户管理", operation = "管理员更新用户")
    public ApiResponse<UserResp> update(@PathVariable Integer userId, @Valid @RequestBody AdminUserUpdateReq req) {
        return ApiResponse.success(service.update(userId, req, StpUtil.getLoginIdAsInt()));
    }

    @GetMapping("/{userId}/devices")
    @SaCheckPermission("system:user:devices:api:list")
    public ApiResponse<PageResult<DeviceResp>> devices(@PathVariable Integer userId, @Valid DevicePageReq req) {
        return ApiResponse.success(service.devices(userId, req));
    }

    @GetMapping("/robots")
    @SaCheckPermission("system:user:devices:api:list")
    public ApiResponse<PageResult<AdminRobotResp>> robots(@RequestParam(required = false) String search,
                                                         @Valid DevicePageReq page) {
        return ApiResponse.success(service.robots(search, page.getPageNo(), page.getPageSize()));
    }

    @PostMapping("/{userId}/devices")
    @SaCheckPermission("system:user:devices:api:bind")
    @AuditLog(module = "用户管理", operation = "管理员分配设备")
    public ApiResponse<Integer> bindDevices(@PathVariable Integer userId, @Valid @RequestBody AdminUserDevicesReq req) {
        return ApiResponse.success(service.assignDevices(userId, req.getDevices()));
    }
}
