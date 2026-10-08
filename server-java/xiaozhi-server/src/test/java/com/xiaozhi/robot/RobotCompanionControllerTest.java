package com.xiaozhi.robot;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.summary.service.SummaryService;
import com.xiaozhi.ai.llm.service.VisionService;
import com.xiaozhi.common.model.bo.RoleBO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RobotCompanionControllerTest {
    @Test void memoryMutationsNeverAcceptForeignRoleOrDeviceId() {
        var roles = mock(RoleService.class); var summaries = mock(SummaryService.class);
        var controller = new RobotCompanionController();
        ReflectionTestUtils.setField(controller, "roleService", roles);
        ReflectionTestUtils.setField(controller, "summaryService", summaries);
        var other = new RoleBO(); other.setUserId(20); when(roles.getBO(1)).thenReturn(other);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsInt).thenReturn(10);
            assertThrows(IllegalArgumentException.class, () -> controller.deleteMemory(1, 100));
            assertThrows(IllegalArgumentException.class, () -> controller.editMemory(1, 100, new RobotCompanionController.MemoryEdit("test")));
            verifyNoInteractions(summaries);
            other.setUserId(10);
            controller.editMemory(1, 100, new RobotCompanionController.MemoryEdit("corrected"));
            verify(summaries).updateText(1, "user_chat_10", 100L, "corrected");
        }
    }
    @Test void visionRequiresConsentAndBoundedImageBeforeProvider() {
        var controller = new RobotCompanionController(); var vision = mock(VisionService.class);
        ReflectionTestUtils.setField(controller, "visionService", vision);
        var file = new MockMultipartFile("image", "scene.jpg", "image/jpeg", new byte[]{1,2});
        try (var auth = mockStatic(StpUtil.class)) {
            assertThrows(IllegalArgumentException.class, () -> controller.vision(file,"what",false));
            assertThrows(IllegalArgumentException.class, () -> controller.vision(file,"",true));
            assertThrows(IllegalArgumentException.class, () -> controller.vision(new MockMultipartFile("image","big.jpg","image/jpeg",new byte[2_000_001]),"what",true));
            verifyNoInteractions(vision);
            when(vision.recognize(file,"what")).thenReturn("a cup");
            controller.vision(file,"what",true); verify(vision).recognize(file,"what");
        }
    }
}
