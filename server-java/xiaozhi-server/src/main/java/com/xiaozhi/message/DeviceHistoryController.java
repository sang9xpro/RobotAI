package com.xiaozhi.message;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.req.DeviceHistoryPageReq;
import com.xiaozhi.common.model.req.HistoryDevicePageReq;
import com.xiaozhi.common.model.resp.AdminRobotResp;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.common.web.ApiResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device-history")
@SaCheckPermission("system:device-history:api:list")
public class DeviceHistoryController {
    @Resource private DeviceHistoryAppService service;
    private Integer scopeUserId() {
        int userId = StpUtil.getLoginIdAsInt();
        return StpUtil.hasRole("admin") ? null : userId;
    }
    @GetMapping public ApiResponse<PageResult<MessageResp>> messages(@Valid DeviceHistoryPageReq filter) {
        return ApiResponse.success(service.messages(filter, scopeUserId()));
    }
    @GetMapping("/devices") public ApiResponse<PageResult<AdminRobotResp>> devices(@Valid HistoryDevicePageReq filter) {
        return ApiResponse.success(service.devices(filter, scopeUserId()));
    }
}
