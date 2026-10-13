package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class PlaybackMessage extends Message {
    public PlaybackMessage() { super("playback"); }
    @com.fasterxml.jackson.annotation.JsonProperty("turn_id") private String turnId;
    @com.fasterxml.jackson.annotation.JsonProperty("audio_seq") private Long audioSeq;
    private String state;
}
