package com.xiaozhi.common.model.bo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Local recognition hint, never authentication or an authorization credential. */
public record SpeakerHint(String status, @JsonProperty("person_id") String personId,
                          String name, String address, String source) {
    public static SpeakerHint unknown() { return new SpeakerHint("unknown", null, null, null, "none"); }
    public SpeakerHint sanitized() {
        if (!"recognized".equals(status) || !("voice".equals(source) || "face_voice".equals(source) || "tracked_voice".equals(source))) return unknown();
        try { UUID.fromString(personId); } catch (Exception e) { return unknown(); }
        String safeName = label(name, 60);
        if (safeName.isBlank()) return unknown();
        return new SpeakerHint(status, personId, safeName, label(address, 40), source);
    }
    private static String label(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("[^\\p{L}\\p{M}\\p{N} .'-]", " ").strip();
        return clean.substring(0, Math.min(max, clean.length()));
    }
}
