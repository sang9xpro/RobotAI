package com.xiaozhi.memory.service;

import com.xiaozhi.common.port.PersonalMemoryStore;
import com.xiaozhi.common.model.bo.StablePersonalFacts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class PersonalMemoryServiceImpl implements PersonalMemoryStore {
    private final JdbcTemplate jdbc;
    public PersonalMemoryServiceImpl(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    static String id(Scope scope) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (scope.userId()+"\n"+scope.deviceId()+"\n"+scope.roleId()+"\n"+scope.personId()).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void init(Scope scope) {
        jdbc.update("INSERT IGNORE INTO robot_person_memory(scopeId,userId,deviceId,roleId,personId) VALUES (?,?,?,?,?)",
                id(scope),scope.userId(),scope.deviceId(),scope.roleId(),scope.personId());
    }
    private Snapshot header(Scope scope, boolean lock) {
        return jdbc.queryForObject("SELECT enabled,revision FROM robot_person_memory WHERE scopeId=?"+(lock?" FOR UPDATE":""),
                (rs,row)->new Snapshot(rs.getBoolean(1),rs.getLong(2),List.of()), id(scope));
    }
    @Override @Transactional
    public Snapshot read(Scope scope) {
        init(scope); var head=header(scope,true);
        var facts=jdbc.query("SELECT factKey,factValue FROM robot_person_fact WHERE scopeId=? ORDER BY factKey",
                (rs,row)->new Fact(rs.getString(1),rs.getString(2)),id(scope));
        return new Snapshot(head.enabled(),head.revision(),facts);
    }
    @Override @Transactional
    public boolean learn(Scope scope,long revision,List<Fact> facts) {
        init(scope); var head=header(scope,true);
        if(!head.enabled() || head.revision()!=revision) return false;
        for(var fact:facts) if(StablePersonalFacts.safe(fact)) upsert(scope,fact);
        return true;
    }
    private void upsert(Scope scope,Fact fact) {
        jdbc.update("INSERT INTO robot_person_fact(scopeId,factKey,factValue) VALUES (?,?,?) ON DUPLICATE KEY UPDATE factValue=VALUES(factValue)",id(scope),fact.key(),fact.value());
    }
    private void revise(Scope scope) { jdbc.update("UPDATE robot_person_memory SET revision=revision+1 WHERE scopeId=?",id(scope)); }
    @Override @Transactional public void edit(Scope scope,Fact fact) {
        if(!StablePersonalFacts.safe(fact)) throw new IllegalArgumentException("Invalid personal fact");
        init(scope);header(scope,true);upsert(scope,fact);revise(scope);
    }
    @Override @Transactional public void delete(Scope scope,String key) {
        init(scope);header(scope,true);
        jdbc.update("DELETE FROM robot_person_fact WHERE scopeId=? AND factKey=?",id(scope),key);revise(scope);
    }
    @Override @Transactional public void enable(Scope scope,boolean enabled) {
        init(scope);header(scope,true);
        jdbc.update("UPDATE robot_person_memory SET enabled=?,revision=revision+1 WHERE scopeId=?",enabled,id(scope));
    }
    @Override @Transactional public void forgetPerson(int userId,String deviceId,String personId) {
        UUID.fromString(personId);
        var scopes=jdbc.query("SELECT roleId FROM robot_person_memory WHERE userId=? AND deviceId=? AND personId=? ORDER BY scopeId FOR UPDATE",
                (rs,row)->new Scope(userId,deviceId,rs.getInt(1),personId),userId,deviceId,personId);
        scopes.forEach(this::forget);
    }
    @Override @Transactional public void forget(Scope scope) {
        init(scope);header(scope,true);
        jdbc.update("DELETE FROM robot_person_fact WHERE scopeId=?",id(scope));
        jdbc.update("UPDATE robot_person_memory SET enabled=0,revision=revision+1 WHERE scopeId=?",id(scope));
    }
}
