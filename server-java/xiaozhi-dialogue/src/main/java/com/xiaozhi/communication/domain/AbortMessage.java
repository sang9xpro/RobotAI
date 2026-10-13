package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class AbortMessage extends Message {
    public AbortMessage() {
        super("abort");
    }

    private String reason;
    @com.fasterxml.jackson.annotation.JsonProperty("turn_id")
    private String turnId;
}
