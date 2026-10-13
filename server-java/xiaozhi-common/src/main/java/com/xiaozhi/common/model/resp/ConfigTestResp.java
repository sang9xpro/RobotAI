package com.xiaozhi.common.model.resp;

/** Outcome of a real provider call; excludes credentials and upstream error bodies. */
public record ConfigTestResp(boolean success, long elapsedMillis, String detail) {}
