package com.xiaozhi.memory.service;

import com.xiaozhi.common.port.PersonalMemoryStore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PersonalMemoryServiceImplTest {
    private PersonalMemoryServiceImpl store;
    private TransactionTemplate transactions;
    private final Scope owner=new Scope(1,"user_chat_1",1,UUID.randomUUID().toString());
    @BeforeEach void database() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE robot_person_memory(scopeId CHAR(64) PRIMARY KEY,userId INT,deviceId VARCHAR(128),roleId INT,personId CHAR(36),enabled BOOLEAN DEFAULT TRUE,revision BIGINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE robot_person_fact(scopeId CHAR(64),factKey VARCHAR(32),factValue VARCHAR(160),PRIMARY KEY(scopeId,factKey))");
        store=new PersonalMemoryServiceImpl(jdbc);
        transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    private <T> T tx(java.util.function.Supplier<T> task) { return transactions.execute(status->task.get()); }
    private void write(Runnable task) { transactions.executeWithoutResult(status->task.run()); }
    @Test void memoriesBelongToAccountDeviceRoleAndPerson() {
        long revision=tx(()->store.read(owner)).revision();
        assertThat(tx(()->store.learn(owner,revision,List.of(new Fact("preference","trà xanh"))))).isTrue();
        assertThat(tx(()->store.read(owner)).facts()).hasSize(1);
        for(var other:List.of(new Scope(2,owner.deviceId(),1,owner.personId()),new Scope(1,"other-device",1,owner.personId()),
                new Scope(1,owner.deviceId(),2,owner.personId()),new Scope(1,owner.deviceId(),1,UUID.randomUUID().toString())))
            assertThat(tx(()->store.read(other)).facts()).isEmpty();
    }
    @Test void deletionAndManualEditsRejectOldExtractionTasks() {
        long before=tx(()->store.read(owner)).revision();
        tx(()->store.learn(owner,before,List.of(new Fact("preference","trà"))));
        write(()->store.delete(owner,"preference"));
        assertThat(tx(()->store.learn(owner,before,List.of(new Fact("preference","trà"))))).isFalse();
        assertThat(tx(()->store.read(owner)).facts()).isEmpty();
        long after=tx(()->store.read(owner)).revision();
        write(()->store.edit(owner,new Fact("preference","cà phê")));
        assertThat(tx(()->store.learn(owner,after,List.of(new Fact("preference","trà"))))).isFalse();
        assertThat(tx(()->store.read(owner)).facts()).containsExactly(new Fact("preference","cà phê"));
    }
    @Test void forgettingPersonDisablesAllRolesAndPendingLearning() {
        var second=new Scope(1,owner.deviceId(),2,owner.personId());
        tx(()->store.read(second));tx(()->store.read(owner));
        write(()->store.forgetPerson(1,owner.deviceId(),owner.personId()));
        assertThat(tx(()->store.read(owner)).enabled()).isFalse();
        assertThat(tx(()->store.read(second)).enabled()).isFalse();
        assertThat(tx(()->store.learn(owner,1,List.of(new Fact("preference","trà"))))).isFalse();
    }
}
