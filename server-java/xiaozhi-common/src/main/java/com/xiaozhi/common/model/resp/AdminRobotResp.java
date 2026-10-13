package com.xiaozhi.common.model.resp;

/** Administrative inventory metadata only, with the current owner for assignment review. */
public record AdminRobotResp(String deviceId, String deviceName, String state, String type,
                             Integer userId, String username, String ownerName) {}
