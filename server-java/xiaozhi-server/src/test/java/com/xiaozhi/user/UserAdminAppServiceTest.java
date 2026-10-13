package com.xiaozhi.user;

import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.model.bo.*;
import com.xiaozhi.common.model.req.*;
import com.xiaozhi.device.DeviceAppService;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.security.service.AuthenticationService;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAdminAppServiceTest {
    @Mock UserService users;
    @Mock UserAppService userApp;
    @Mock AuthRoleService roles;
    @Mock AuthenticationService auth;
    @Mock DeviceRepository devices;
    @Mock RoleService personas;
    @Mock DeviceAppService deviceApp;
    UserAdminAppService service;
    static final String A = "02:93:00:00:00:01", B = "02:93:00:00:00:02";

    @BeforeEach void setup() {
        service = new UserAdminAppService();
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "userAppService", userApp);
        ReflectionTestUtils.setField(service, "userConvert", Mappers.getMapper(UserConvert.class));
        ReflectionTestUtils.setField(service, "authRoleService", roles);
        ReflectionTestUtils.setField(service, "authenticationService", auth);
        ReflectionTestUtils.setField(service, "deviceRepository", devices);
        ReflectionTestUtils.setField(service, "roleService", personas);
        ReflectionTestUtils.setField(service, "deviceAppService", deviceApp);
    }
    UserBO target() { var user = new UserBO(); user.setUserId(9); user.setAuthRoleId(2); user.setState("1"); user.setIsAdmin("0"); return user; }
    AdminUserDevicesReq.Assignment assignment(String id, Integer owner) {
        var a = new AdminUserDevicesReq.Assignment(); a.setDeviceId(id); a.setExpectedUserId(owner); return a;
    }
    Device robot(String id, int owner) {
        var d = Device.newDevice(id, "Synthetic robot", "esp32", owner, 10); d.pullSignals(); return d;
    }

    @Test void createsNormalAccountWithEncryptedPasswordAndDefaultResources() {
        var role = new AuthRoleBO(); role.setStatus("1"); when(roles.getBO(2)).thenReturn(role);
        when(auth.encryptPassword("synthetic-password")).thenReturn("encrypted");
        when(userApp.createUserWithDefaults(any())).thenReturn(target());
        var req = new AdminUserCreateReq(); req.setUsername("fixture"); req.setPassword("synthetic-password");
        service.create(req);
        var capture = ArgumentCaptor.forClass(UserBO.class);
        verify(userApp).createUserWithDefaults(capture.capture());
        assertThat(capture.getValue().getPassword()).isEqualTo("encrypted");
        assertThat(capture.getValue().getIsAdmin()).isEqualTo("0");
        assertThat(capture.getValue().getAuthRoleId()).isEqualTo(2);
    }

    @Test void assignsMultipleExistingRobotsAndClosesOldPersonasWithoutSwitchingTheirOwner() {
        when(users.getBO(9)).thenReturn(target());
        var a = robot(A, 1); var b = robot(B, 7);
        when(devices.lockByIds(List.of(A, B))).thenReturn(List.of(a, b));
        var role = new RoleBO(); role.setRoleId(30); role.setUserId(9); when(personas.getDefaultOrFirstBO(9)).thenReturn(role);
        assertThat(service.assignDevices(9, List.of(assignment(A, 1), assignment(B, 7)))).isEqualTo(2);
        for (Device d : List.of(a, b)) {
            assertThat(d.getUserId()).isEqualTo(9); assertThat(d.getRoleId()).isEqualTo(30);
            assertThat(d.pullSignals()).contains(Device.DomainSignal.SESSION_CLOSED, Device.DomainSignal.UPDATED)
                    .doesNotContain(Device.DomainSignal.ROLE_CHANGED);
            verify(devices).save(d);
        }
    }

    @Test void staleOwnerInAnySelectionRejectsWholeBatchBeforeWriting() {
        when(users.getBO(9)).thenReturn(target());
        when(devices.lockByIds(List.of(A, B))).thenReturn(List.of(robot(A, 1), robot(B, 8)));
        assertThatThrownBy(() -> service.assignDevices(9, List.of(assignment(A, 1), assignment(B, 7))))
                .isInstanceOf(IllegalStateException.class);
        verify(devices, never()).save(any());
    }

    @Test void virtualDevicesAndDuplicateIdsCannotBeAssigned() {
        when(users.getBO(9)).thenReturn(target());
        assertThatThrownBy(() -> service.assignDevices(9, List.of(assignment("user_chat_7", 7))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignDevices(9, List.of(assignment(A, 1), assignment(A, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(devices);
    }

    @Test void listingDevicesAlwaysUsesTheSelectedUserId() {
        when(users.getBO(9)).thenReturn(target()); var req = new DevicePageReq();
        service.devices(9, req); verify(deviceApp).page(req, 9);
    }

    @Test void profileChangesNeverMutatePasswordsAndSelfRoleChangeIsRejected() {
        when(users.getBO(9)).thenReturn(target());
        var req = new AdminUserUpdateReq(); req.setName("Changed");
        when(users.update(any())).thenReturn(target());
        service.update(9, req, 1);
        var capture = ArgumentCaptor.forClass(UserBO.class); verify(users).update(capture.capture());
        assertThat(capture.getValue().getPassword()).isNull(); assertThat(capture.getValue().getIsAdmin()).isNull();
        req.setAuthRoleId(1);
        assertThatThrownBy(() -> service.update(9, req, 9)).isInstanceOf(IllegalArgumentException.class);
    }
}
