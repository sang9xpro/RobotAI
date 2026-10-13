package com.xiaozhi.common.model.resp;
public record RobotSystemStatusResp(String id,String state,String checkedTime,Long latencyMs,String detail) {}
