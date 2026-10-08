package com.xiaozhi.common.model.resp;
import java.util.List;
public record RobotProfileResp(String deviceId, Integer roleId, List<RobotRoleResp> roles) {
    public record RobotRoleResp(Integer roleId, String roleName, String voiceName,
                               Integer sttId, Integer modelId, Integer ttsId) {}
}
