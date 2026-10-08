package com.xiaozhi.user;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** Self-service profile: user identity always comes from the authenticated controller. */
@Service
public class RobotProfileService {
    @Resource private RoleService roleService;
    @Resource private DeviceService deviceService;

    public record RoleView(Integer roleId, String roleName, String voiceName,
                           Integer sttId, Integer modelId, Integer ttsId) {
        static RoleView of(RoleBO role) {
            return new RoleView(role.getRoleId(), role.getRoleName(), role.getVoiceName(),
                    role.getSttId(), role.getModelId(), role.getTtsId());
        }
    }
    public record Profile(String deviceId, Integer roleId, List<RoleView> roles) {}

    public Profile get(Integer userId) {
        String deviceId = "user_chat_" + userId;
        var device = deviceService.getBO(deviceId);
        if (device != null && !Objects.equals(userId, device.getUserId())) {
            throw new IllegalStateException("Robot identity ownership mismatch");
        }
        List<RoleBO> roles = roleService.listBO(userId, 100).stream()
                .filter(role -> Objects.equals(role.getUserId(), userId) && "1".equals(role.getState())).toList();
        Integer selected = device != null ? device.getRoleId() : null;
        final Integer candidate = selected;
        if (roles.stream().noneMatch(role -> Objects.equals(role.getRoleId(), candidate))) {
            var fallback = roleService.getDefaultOrFirstBO(userId);
            selected = fallback != null && roles.stream().anyMatch(role -> Objects.equals(role.getRoleId(), fallback.getRoleId()))
                    ? fallback.getRoleId() : null;
        }
        return new Profile(deviceId, selected, roles.stream().map(RoleView::of).toList());
    }

    public Profile select(Integer userId, Integer roleId) {
        var role = roleService.getBO(roleId);
        if (role == null || !Objects.equals(userId, role.getUserId()) || !"1".equals(role.getState())) {
            throw new IllegalArgumentException("Role is unavailable for this user");
        }
        String deviceId = "user_chat_" + userId;
        var device = deviceService.getBO(deviceId);
        if (device != null && !Objects.equals(userId, device.getUserId())) {
            throw new IllegalStateException("Robot identity ownership mismatch");
        }
        if (device == null) deviceService.register(deviceId, "RobotAI", "web", userId, roleId);
        else deviceService.bindRole(deviceId, roleId);
        return get(userId);
    }
}
