package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Foreground Android music playback is session activity, without microphone input. */
@Data
@EqualsAndHashCode(callSuper = true)
public final class MusicPlaybackMessage extends Message {
    private String state;
    public MusicPlaybackMessage() { super("music_playback"); }
}
