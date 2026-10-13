package com.xiaozhi.device.model;

import lombok.Data;

@Data
public class AdminRobotProjection {
    private String deviceId;
    private String deviceName;
    private String state;
    private String type;
    private Integer userId;
    private String username;
    private String ownerName;
}
