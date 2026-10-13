package com.xiaozhi.message;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.req.DeviceHistoryPageReq;
import com.xiaozhi.common.model.req.HistoryDevicePageReq;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeviceHistoryControllerTest extends ControllerTestSupport {
    @Mock private DeviceHistoryAppService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        var controller = new DeviceHistoryController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc = buildMockMvc(controller);
    }
    @Test void regularUserCannotOverrideScopeThroughQueryParameters() throws Exception {
        when(service.messages(any(), eq(7))).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));
        try (var login = mockLoginUser(7)) {
            mvc.perform(get("/api/device-history").param("userId", "9").param("scopeUserId", "9").param("deviceId", "another-user-device"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        }
        verify(service).messages(argThat(req -> "another-user-device".equals(req.getDeviceId()) && req.getPageSize() == 20), eq(7));
    }
    @Test void adminCanReadAcrossOwnersWithValidatedFilters() throws Exception {
        when(service.messages(any(), isNull())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));
        try (var login = mockLoginUser(1)) {
            login.when(() -> StpUtil.hasRole("admin")).thenReturn(true);
            mvc.perform(get("/api/device-history").param("keyword", "hello").param("sessionId", "s-1").param("order", "asc"))
                    .andExpect(status().isOk());
        }
        verify(service).messages(argThat(req -> "hello".equals(req.getKeyword()) && "s-1".equals(req.getSessionId()) && "asc".equals(req.getOrder())), isNull());
    }
    @Test void devicePickerIsScopedToTheAuthenticatedUser() throws Exception {
        when(service.devices(any(), eq(7))).thenReturn(new PageResult<>(List.of(), 0L, 1, 50));
        try (var login = mockLoginUser(7)) {
            mvc.perform(get("/api/device-history/devices").param("search", "robot").param("pageSize", "50").param("userId", "9"))
                    .andExpect(status().isOk());
        }
        verify(service).devices(argThat(req -> "robot".equals(req.getSearch())), eq(7));
    }
    @Test void invalidTimeRangeAndUnboundedPageAreRejectedBeforeQuerying() throws Exception {
        mvc.perform(get("/api/device-history").param("startTime", "2026-10-13 12:00:00").param("endTime", "2026-10-12 12:00:00"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/device-history").param("pageSize", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/device-history").param("order", "unsafe")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void anonymousRequestsDoNotReachTheService() throws Exception {
        try (var login = mockNoLoginUser()) {
            mvc.perform(get("/api/device-history")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
}
