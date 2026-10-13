package com.xiaozhi.common.model.bo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Set;
import java.util.UUID;

/** Per-turn observations. Neither identity nor admission constitutes authentication. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ListeningContext(String conversationId, long revision, String reason, String robotName,
                               boolean accepted, boolean visible, boolean facingRobot,
                               boolean activeSpeakerVerified, long cameraAgeMs, boolean overlapping,
                               boolean uncertain, int visiblePeople) {
    public ListeningContext sanitized() {
        try { UUID.fromString(conversationId); } catch (Exception e) { return null; }
        if (reason == null || revision < 0 || cameraAgeMs < -1 || visiblePeople < 0 || visiblePeople > 8
                || !Set.of("wake", "continuation", "manual", "face_voice", "interruption").contains(reason)) return null;
        String name = SpeakerHint.label(robotName, 30);
        if (name.isBlank()) return null;
        return new ListeningContext(conversationId, revision, reason, name, accepted, visible, facingRobot,
                activeSpeakerVerified, cameraAgeMs, overlapping, uncertain, visiblePeople);
    }
    public boolean visualAdmission() {
        return visiblePeople == 1 && visible && facingRobot && activeSpeakerVerified
                && cameraAgeMs >= 0 && cameraAgeMs <= 500;
    }
}
