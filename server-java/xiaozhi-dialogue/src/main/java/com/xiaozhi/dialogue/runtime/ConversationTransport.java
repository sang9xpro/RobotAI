package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.communication.server.websocket.Rai1Codec;
import java.util.concurrent.ThreadLocalRandom;

/** Capture the turn at task creation; never stamp a late task with a new turn. */
public final class ConversationTransport {
    private boolean enabled;
    private final long generation = ThreadLocalRandom.current().nextLong(1, 0x100000000L);
    private long turn, uplink, downlink;
    private long acknowledged = -1;
    private boolean outputFinished, playbackConfirmed;
    public synchronized void finishOutput() { if(validOutput()) outputFinished = true; }
    public synchronized boolean confirmCompleted() {
        if(!outputFinished || playbackConfirmed || downlink == 0 || acknowledged != downlink-1) return false;
        playbackConfirmed = true; return true;
    }
    private final java.util.List<Sentence> sentences = new java.util.ArrayList<>();
    private record Sentence(String text, long start) {}
    public synchronized void sentence(String text) { if(validOutput()) sentences.add(new Sentence(text, downlink)); }
    public synchronized void acknowledge(long sequence) {
        if(sequence >= acknowledged && sequence < downlink) acknowledged = sequence;
    }
    public synchronized String heardText() {
        var heard = new java.util.ArrayList<String>();
        for(int i=0;i<sentences.size();i++) {
            long end = i+1 < sentences.size() ? sentences.get(i+1).start()-1 : outputFinished ? downlink-1 : Long.MAX_VALUE;
            if(end >= sentences.get(i).start() && end <= acknowledged) heard.add(sentences.get(i).text());
        }
        return String.join(" ", heard);
    }
    private boolean cancelled = true;
    private final ThreadLocal<Long> taskTurn = new ThreadLocal<>();
    public synchronized void enable() { enabled = true; }
    public synchronized boolean enabled() { return enabled; }
    public long generation() { return generation; }
    public synchronized long turn() { return turn; }
    public synchronized boolean begin(long gen, String id) {
        long next;
        try { next = Long.parseLong(id); } catch (Exception e) { return false; }
        if (!enabled || gen != generation || next <= turn || !cancelled && !playbackConfirmed && turn > 0) return false;
        turn = next; uplink = downlink = 0; acknowledged = -1; sentences.clear(); outputFinished = playbackConfirmed = false; cancelled = false; return true;
    }
    public synchronized boolean accepts(long gen, String id) {
        return enabled && !cancelled && gen == generation && Long.toString(turn).equals(id);
    }
    public synchronized void cancel() { cancelled = true; }
    public synchronized boolean accept(Rai1Codec.Frame f) {
        if (cancelled || f.generation() != generation || f.turn() != turn) return false;
        if (f.sequence() != uplink) throw new IllegalArgumentException("Out of order RAI1 audio");
        uplink++; return true;
    }
    public synchronized boolean validOutput() {
        Long captured = taskTurn.get();
        return !cancelled && turn > 0 && (captured == null || captured == turn);
    }
    public synchronized byte[] encode(byte[] opus) {
        if (!validOutput()) return null;
        return Rai1Codec.encode(new Rai1Codec.Frame(generation, turn, downlink++, opus));
    }
    public synchronized long capture() { Long current=taskTurn.get(); return current==null ? turn : current; }
    public synchronized boolean isCurrent(long captured) { return !cancelled && turn==captured; }
    public Runnable bind(Runnable task) { return bind(capture(),task); }
    public Runnable bind(long captured, Runnable task) {
        return () -> { taskTurn.set(captured); try { task.run(); } finally { taskTurn.remove(); } };
    }
}
