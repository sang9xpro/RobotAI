package com.xiaozhi.common.model.resp;
public record RobotWeatherResp(double temperatureC, int weatherCode, String time, String timezone, String source) {}
