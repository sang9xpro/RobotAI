package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.common.model.bo.SpeakerHint;

/** Scoped to one authenticated transport. STOP may complete its matching START once only. */
public final class SpeakerTurnContext {
    private String turnId;
    private long started;
    public synchronized void begin(String id) {
        turnId = id != null && id.matches("[0-9]{1,19}") ? id : null;
        started = System.nanoTime();
    }
    public synchronized SpeakerHint complete(String id, SpeakerHint hint) {
        boolean valid = turnId != null && turnId.equals(id) && System.nanoTime() - started < 90_000_000_000L;
        turnId = null;
        return valid && hint != null ? hint.sanitized() : SpeakerHint.unknown();
    }
    public synchronized boolean accepts(String id) {
        return turnId != null && turnId.equals(id);
    }
}
