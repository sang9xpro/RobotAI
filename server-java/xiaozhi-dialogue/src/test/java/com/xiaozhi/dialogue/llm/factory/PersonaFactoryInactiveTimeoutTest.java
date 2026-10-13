package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.dialogue.runtime.Persona;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 复用已有 Persona 的那条快路径也要先把角色的空闲超时写回会话，
 * 否则重连后的会话一直用默认超时，角色配的断连时间不生效。
 */
class PersonaFactoryInactiveTimeoutTest {
    @Test
    void authenticatedAppKeepsSessionUntilDisconnect() {
        var factory = new PersonaFactory();
        var session = mock(ChatSession.class);
        when(session.getPersona()).thenReturn(mock(Persona.class));
        var device = new DeviceBO(); device.setDeviceId("user_chat_7"); device.setUserId(7);
        var role = new RoleBO(); role.setInactiveTimeoutSeconds(45);
        factory.buildPersona(session, device, role);
        verify(session).setInactiveTimeoutSeconds(0);
    }

    @Test
    void initializesSessionTimeoutBeforeReturningExistingPersona() {
        PersonaFactory factory = new PersonaFactory();
        ChatSession session = mock(ChatSession.class);
        DeviceBO device = new DeviceBO();
        RoleBO role = new RoleBO();
        role.setInactiveTimeoutSeconds(45);
        Persona persona = mock(Persona.class);
        when(session.getPersona()).thenReturn(persona);

        Persona result = factory.buildPersona(session, device, role);

        assertThat(result).isSameAs(persona);
        verify(session).setInactiveTimeoutSeconds(45);
    }
}
