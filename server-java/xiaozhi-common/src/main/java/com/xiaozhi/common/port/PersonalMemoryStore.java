package com.xiaozhi.common.port;

import java.util.List;
import java.util.UUID;

/** Biometric identity is a local hint; account/device/role ownership remains server controlled. */
public interface PersonalMemoryStore {
    record Scope(int userId, String deviceId, int roleId, String personId) {
        public Scope { UUID.fromString(personId); if(userId<=0 || roleId<=0 || deviceId==null || deviceId.length()>128) throw new IllegalArgumentException("Invalid memory scope"); }
    }
    record Fact(String key, String value) {}
    record Snapshot(boolean enabled, long revision, List<Fact> facts) {}
    Snapshot read(Scope scope);
    boolean learn(Scope scope, long expectedRevision, List<Fact> facts);
    void edit(Scope scope, Fact fact);
    void delete(Scope scope, String key);
    void enable(Scope scope, boolean enabled);
    void forget(Scope scope);
    void forgetPerson(int userId, String deviceId, String personId);
}
