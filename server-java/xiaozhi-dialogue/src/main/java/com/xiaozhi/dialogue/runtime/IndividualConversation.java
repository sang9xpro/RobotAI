package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.common.model.bo.ListeningContext;
import com.xiaozhi.common.model.bo.SpeakerHint;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/** One interlocutor. A new owner receives a fresh server-generated history scope. */
public final class IndividualConversation {
    public enum Action { ANSWER, CLARIFY, IGNORE }
    public record Decision(Action action, boolean switched, String scopeId, String text) {}
    private String memoryPerson;
    private long memoryRevision = -1;
    public synchronized boolean memoryChanged(String person,long revision) {
        boolean changed = person.equals(memoryPerson) && memoryRevision >= 0 && revision != memoryRevision;
        memoryPerson = person; memoryRevision = revision; return changed;
    }
    private String owner;
    private String clientConversation;
    private String scopeId;
    private long lastAccepted;
    private long lastRevision = -1;
    private long playbackCompleted = -1;

    public synchronized Decision admit(String text, SpeakerHint speaker, ListeningContext raw, long now) {
        speaker = speaker == null ? SpeakerHint.unknown() : speaker.sanitized();
        ListeningContext context = raw == null ? null : raw.sanitized();
        if (context == null || !context.accepted() || text == null || text.isBlank())
            return new Decision(Action.IGNORE, false, scopeId, text);
        String clean = normalize(text);
        String name = normalize(context.robotName());
        String call = "(?:chào |xin chào |này )?" + java.util.regex.Pattern.quote(name) + " ơi";
        boolean wake = clean.equals(name) || clean.matches("^" + call + "(?: .*)?$")
                || clean.matches("^.+ " + java.util.regex.Pattern.quote(name) + " ơi$");
        String key = "recognized".equals(speaker.status()) || "temporary".equals(speaker.status())
                ? speaker.status() + ":" + speaker.personId() : "unknown:" + context.conversationId();
        boolean same = key.equals(owner) && context.conversationId().equals(clientConversation)
                && now >= lastAccepted && now - lastAccepted < 180_000;
        if (same && context.revision() <= lastRevision) return new Decision(Action.IGNORE, false, scopeId, text);
        boolean allowed = switch (context.reason()) {
            case "manual" -> true;
            case "wake" -> wake;
            case "face_voice" -> "recognized".equals(speaker.status()) && context.visualAdmission() && !context.uncertain() && !context.overlapping();
            case "interruption" -> same && !"unknown".equals(speaker.status()) && !context.uncertain() && !context.overlapping();
            case "continuation" -> same && !"unknown".equals(speaker.status())
                    && !context.uncertain() && !context.overlapping() && (context.visualAdmission()
                    || playbackCompleted >= 0 && now - playbackCompleted >= 0 && now - playbackCompleted <= 30_000);
            default -> false;
        };
        if (!allowed) return new Decision(Action.IGNORE, false, scopeId, text);
        boolean switched = !same;
        if (switched) { scopeId = UUID.randomUUID().toString(); playbackCompleted = -1; }
        owner = key; clientConversation = context.conversationId(); lastAccepted = now; lastRevision = context.revision();
        if (wake) clean = clean.replaceFirst("^" + call + "(?: |$)", "").strip();
        boolean clarification = context.overlapping() || context.uncertain() || wake && (clean.isBlank() || clean.equals(name));
        return new Decision(clarification ? Action.CLARIFY : Action.ANSWER, switched, scopeId, text);
    }
    public synchronized void playbackCompleted(long now) { playbackCompleted = now; }
    public synchronized void reset() { owner = null; clientConversation = null; scopeId = null; lastRevision = -1; playbackCompleted = -1; }
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ").strip().replaceAll("\\s+", " ");
    }
}
