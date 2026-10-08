package com.xiaozhi.user;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RobotProfileServiceTest {
    private final RoleService roles = mock(RoleService.class);
    private final DeviceService devices = mock(DeviceService.class);
    private RobotProfileService service() {
        var s = new RobotProfileService();
        ReflectionTestUtils.setField(s, "roleService", roles);
        ReflectionTestUtils.setField(s, "deviceService", devices);
        return s;
    }
    private RoleBO role(int id, int user) {
        var r = new RoleBO(); r.setRoleId(id); r.setUserId(user); r.setState("1"); r.setRoleName("Ken"); return r;
    }
    @Test void rejectsOtherUsersAndDisabledRoles() {
        when(roles.getBO(2)).thenReturn(role(2, 20));
        assertThrows(IllegalArgumentException.class, () -> service().select(10, 2));
        var disabled = role(3, 10); disabled.setState("0"); when(roles.getBO(3)).thenReturn(disabled);
        assertThrows(IllegalArgumentException.class, () -> service().select(10, 3));
        verifyNoInteractions(devices);
    }
    @Test void profileFiltersForeignRolesAndUsesOwnedDefault() {
        var own = role(1, 10);
        when(roles.listBO(10, 100)).thenReturn(List.of(own, role(2, 20)));
        when(roles.getDefaultOrFirstBO(10)).thenReturn(own);
        var p = service().get(10);
        assertEquals("user_chat_10", p.deviceId()); assertEquals(1, p.roleId()); assertEquals(1, p.roles().size());
    }
    @Test void switchingRolePersistsExistingDeviceAndDoesNotOverwriteUser() {
        var own = role(1, 10);
        when(roles.getBO(1)).thenReturn(own);
        when(roles.listBO(10, 100)).thenReturn(List.of(own));
        when(roles.getDefaultOrFirstBO(10)).thenReturn(own);
        var device = new DeviceBO(); device.setUserId(10); device.setRoleId(1);
        when(devices.getBO("user_chat_10")).thenReturn(device);
        service().select(10, 1);
        verify(devices).bindRole("user_chat_10", 1);
        verify(devices, never()).register(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }
    @Test void refusesDeviceWithMismatchedOwner() {
        var device = new DeviceBO(); device.setUserId(20);
        when(devices.getBO("user_chat_10")).thenReturn(device);
        assertThrows(IllegalStateException.class, () -> service().get(10));
    }
}
