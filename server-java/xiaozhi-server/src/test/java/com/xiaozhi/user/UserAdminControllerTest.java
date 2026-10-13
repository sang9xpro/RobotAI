package com.xiaozhi.user;

import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.req.AdminUserDevicesReq;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserAdminControllerTest extends ControllerTestSupport {
    @Mock private UserAdminAppService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        var controller = new UserAdminController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc = buildMockMvc(controller);
    }
    @Test void inventoryUsesTheSpecificRouteAndValidatesPagination() throws Exception {
        when(service.robots("kitchen", 2, 50)).thenReturn(new PageResult<>(List.of(), 0L, 2, 50));
        mvc.perform(get("/api/admin/users/robots").param("search", "kitchen").param("pageNo", "2").param("pageSize", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(service).robots("kitchen", 2, 50);
        mvc.perform(get("/api/admin/users/robots").param("pageSize", "1001"))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsVirtualDevicesNullElementsAndEmptyBatchesBeforeWriting() throws Exception {
        for (String json : List.of("{\"devices\":[]}", "{\"devices\":[null]}",
                "{\"devices\":[{\"deviceId\":\"user_chat_7\"}]}")) {
            mvc.perform(post("/api/admin/users/9/devices").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void passesExplicitTargetAndOwnerSnapshotForAssignment() throws Exception {
        when(service.assignDevices(eq(9), anyList())).thenReturn(1);
        mvc.perform(post("/api/admin/users/9/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devices\":[{\"deviceId\":\"02:93:00:00:00:01\",\"expectedUserId\":7}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(1));
        verify(service).assignDevices(eq(9), argThat(devices -> devices.getFirst().getExpectedUserId() == 7));
    }
}
