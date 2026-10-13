package com.xiaozhi.robot;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.model.resp.RobotSystemStatusResp;
import com.xiaozhi.common.web.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/robot/system")
public class RobotSystemStatusController {
    private final RobotSystemStatusService service;
    public RobotSystemStatusController(RobotSystemStatusService service) { this.service=service; }
    @GetMapping("/status") @SaCheckPermission("system:robot:status")
    public ApiResponse<List<RobotSystemStatusResp>> status() {
        return ApiResponse.success(service.checks().stream().map(c->
            new RobotSystemStatusResp(c.id(),c.state(),c.checkedTime(),c.latencyMs(),c.detail())).toList());
    }
}
