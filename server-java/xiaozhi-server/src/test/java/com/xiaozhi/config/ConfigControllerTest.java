package com.xiaozhi.config;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.ConfigProbeResultBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigPageReq;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.resp.TestVoiceResp;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住配置接口的分页参数绑定：登录用户 id 原样传给 ConfigAppService；共享管理员配置的判定
 * 完全在 ConfigServiceImpl 内部按 userId 是否等于 ADMIN_USER_ID 完成，Controller 不再参与判定。
 * 同时钉住试拨端点交给 ConfigTestAppService，并把当前登录用户带过去；出参按 success 分流成
 * ApiResponse.success / ApiResponse.error。
 */
@ExtendWith(MockitoExtension.class)
class ConfigControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private ConfigAppService configAppService;

    @Mock
    private ConfigTestAppService configTestAppService;

    private ConfigController configController;

    @BeforeEach
    void setUp() {
        configController = new ConfigController();
        ReflectionTestUtils.setField(configController, "configAppService", configAppService);
        ReflectionTestUtils.setField(configController, "configTestAppService", configTestAppService);
        mockMvc = buildMockMvc(configController);
    }

    @Test
    void listPassesLoginUserIdToAppService() throws Exception {
        when(configAppService.page(any(ConfigPageReq.class), eq(7))).thenReturn(singlePage());

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/config")
                    .param("pageNo", "1")
                    .param("pageSize", "10")
                    .param("configType", "tts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].configId").value(3));
        }

        ArgumentCaptor<ConfigPageReq> captor = ArgumentCaptor.forClass(ConfigPageReq.class);
        verify(configAppService).page(captor.capture(), eq(7));
        assertThat(captor.getValue().getConfigType()).isEqualTo("tts");
    }

    @Test
    void updateReturnsUpdatedConfig() throws Exception {
        ConfigResp updated = new ConfigResp();
        updated.setConfigId(11);
        when(configAppService.update(eq(11), any(ConfigUpdateReq.class), eq(false))).thenReturn(updated);

        mockMvc.perform(put("/api/config/11")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"configName":"新配置"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.configId").value(11));
    }

    @Test
    void createReturnsBadRequestWhenConfigNameMissing() throws Exception {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigType("tts");
        req.setProvider("edge");

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.message").value("配置名称不能为空"));
        }
    }

    @Test
    void testEndpointDelegatesToTestAppServiceWithCurrentUser() throws Exception {
        when(configTestAppService.test(any(ConfigTestReq.class), eq(7)))
            .thenReturn(ConfigProbeResultBO.success("连接成功"));

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/config/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(testReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.message").value("连接成功"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.detail").value("连接成功"))
                .andExpect(jsonPath("$.data.elapsedMillis").isNumber());
        }

        ArgumentCaptor<ConfigTestReq> captor = ArgumentCaptor.forClass(ConfigTestReq.class);
        verify(configTestAppService).test(captor.capture(), eq(7));
        assertThat(captor.getValue().getConfigId()).isEqualTo(11);
    }

    @Test
    void testEndpointReturnsErrorResponseWhenProbeFails() throws Exception {
        when(configTestAppService.test(any(ConfigTestReq.class), eq(7)))
            .thenReturn(ConfigProbeResultBO.failure("接口返回未授权（401），请检查密钥与账号权限"));

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/config/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(testReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.ERROR))
                .andExpect(jsonPath("$.message").value("接口返回未授权（401），请检查密钥与账号权限"));
        }
    }

    @Test
    void multipartSttDecodesWavAndReturnsTranscriptWithoutCredentials() throws Exception {
        var req = testReq(); req.setConfigType("stt"); req.setApiKey("private-fixture-key");
        byte[] wav = java.nio.ByteBuffer.allocate(46).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46464952).putInt(38).putInt(0x45564157).putInt(0x20746d66).putInt(16)
            .putShort((short)1).putShort((short)1).putInt(16000).putInt(32000)
            .putShort((short)2).putShort((short)16).putInt(0x61746164).putInt(2).putShort((short)1).array();
        when(configTestAppService.testSttAudio(any(), eq(7), any()))
            .thenReturn(ConfigProbeResultBO.success("Xin chào Ken"));
        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(multipart("/api/config/test/stt")
                    .file(new MockMultipartFile("config", "config.json", "application/json", toJson(req).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .file(new MockMultipartFile("audio", "sample.wav", "audio/wav", wav)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.detail").value("Xin chào Ken"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        }
        var pcm = ArgumentCaptor.forClass(byte[].class);
        verify(configTestAppService).testSttAudio(any(), eq(7), pcm.capture());
        assertThat(pcm.getValue()).containsExactly((byte)1, (byte)0);
    }

    @Test
    void malformedSttAudioNeverReachesProvider() throws Exception {
        var req = testReq(); req.setConfigType("stt");
        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(multipart("/api/config/test/stt")
                    .file(new MockMultipartFile("config", "config.json", "application/json", toJson(req).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .file(new MockMultipartFile("audio", "sample.wav", "audio/wav", new byte[]{1,2})))
                .andExpect(status().isBadRequest());
        }
        verify(configTestAppService, never()).testSttAudio(any(), any(), any());
    }

    @Test
    void multipartVisionPassesEnteredQuestionAndUploadedBytesWithDetectedMimeType() throws Exception {
        var req = testReq();
        req.setModelType("vision");
        req.setSampleText("Hãy mô tả ảnh bằng tiếng Việt.");
        req.setApiKey("private-fixture-key");
        byte[] image = imageBytes("png", 2, 2);
        when(configTestAppService.testVisionImage(any(), eq(7), any()))
            .thenReturn(ConfigProbeResultBO.success("Kiểm tra Vision thành công. Phản hồi mô hình: Một hình vuông."));
        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(multipart("/api/config/test/vision")
                    .file(new MockMultipartFile("config", "config.json", "application/json", toJson(req).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .file(new MockMultipartFile("image", "picture.png", "application/octet-stream", image)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.detail").value("Kiểm tra Vision thành công. Phản hồi mô hình: Một hình vuông."))
                .andExpect(jsonPath("$.data.elapsedMillis").isNumber())
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        }
        var capturedReq = ArgumentCaptor.forClass(ConfigTestReq.class);
        var capturedImage = ArgumentCaptor.forClass(Media.class);
        verify(configTestAppService).testVisionImage(capturedReq.capture(), eq(7), capturedImage.capture());
        assertThat(capturedReq.getValue().getSampleText()).isEqualTo("Hãy mô tả ảnh bằng tiếng Việt.");
        assertThat(capturedImage.getValue().getDataAsByteArray()).containsExactly(image);
        assertThat(capturedImage.getValue().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
    }

    @Test
    void malformedVisionAttachmentNeverReachesProvider() throws Exception {
        var req = testReq(); req.setModelType("vision"); req.setSampleText("Hãy mô tả ảnh.");
        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(multipart("/api/config/test/vision")
                    .file(new MockMultipartFile("config", "config.json", "application/json", toJson(req).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .file(new MockMultipartFile("image", "fake.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isBadRequest());
        }
        verify(configTestAppService, never()).testVisionImage(any(), any(), any());
    }

    static byte[] imageBytes(String format, int width, int height) throws java.io.IOException {
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    @Test
    void ttsPreviewReturnsStorageUrlAndPassesCurrentUser() throws Exception {
        var req = testReq(); req.setConfigType("tts"); req.setVoiceName("custom-voice"); req.setSampleText("Xin chào");
        when(configTestAppService.testTtsAudio(any(), eq(7))).thenReturn(new TestVoiceResp("/uploads/preview.mp3"));
        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/config/test/tts").contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.audioUrl").value("/uploads/preview.mp3"));
        }
        var captured = ArgumentCaptor.forClass(ConfigTestReq.class);
        verify(configTestAppService).testTtsAudio(captured.capture(), eq(7));
        assertThat(captured.getValue().getVoiceName()).isEqualTo("custom-voice");
    }

    // 异常文案由 GlobalExceptionHandlerTest 集中覆盖，这里只钉路由与路径变量绑定
    @Test
    void deleteReturnsNotFoundWhenConfigMissing() throws Exception {
        doThrow(new ResourceNotFoundException("配置不存在或无权访问"))
            .when(configAppService).delete(9, false);

        mockMvc.perform(delete("/api/config/9"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ResultStatus.NOT_FOUND));
    }

    private static ConfigTestReq testReq() {
        ConfigTestReq req = new ConfigTestReq();
        req.setConfigId(11);
        req.setConfigName("测试配置");
        req.setConfigType("llm");
        req.setProvider("openai");
        return req;
    }

    private static PageResult<ConfigResp> singlePage() {
        ConfigResp resp = new ConfigResp();
        resp.setConfigId(3);
        resp.setConfigName("默认TTS");
        return new PageResult<>(List.of(resp), 1L, 1, 10);
    }
}
