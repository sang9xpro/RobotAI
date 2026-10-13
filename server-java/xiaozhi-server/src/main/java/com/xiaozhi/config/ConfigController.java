package com.xiaozhi.config;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.bo.ConfigProbeResultBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigPageReq;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.resp.ConfigTestResp;
import com.xiaozhi.common.model.resp.ConfigModelResp;
import java.util.List;
import com.xiaozhi.common.model.resp.TestVoiceResp;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


/**
 * 配置管理
 * 
 * @author Joey
 * 
 */

@RestController
@RequestMapping("/api/config")
@Tag(name = "配置管理", description = "配置相关操作")
public class ConfigController extends BaseController {

    @Resource
    private ConfigAppService configAppService;

    @Resource
    private ConfigTestAppService configTestAppService;

    /**
     * 配置查询
     *
     * @param config
     * @return configList
     */
    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:config:api:list")
    @Operation(summary = "根据条件查询配置", description = "返回配置信息列表")
    public ApiResponse<PageResult<ConfigResp>> list(@Valid ConfigPageReq req) {
        return ApiResponse.success(configAppService.page(req, StpUtil.getLoginIdAsInt()));
    }

    /**
     * 配置信息更新
     *
     * @param configId 配置ID
     * @param req 更新参数
     * @param confirmStorageSwitch 已确认换掉对象存储会让历史文件不可访问
     * @return
     */
    @PutMapping("/{configId}")
    @ResponseBody
    @SaCheckPermission("system:config:api:update")
    @CheckOwner(resource = "configWrite", id = "#configId")
    @AuditLog(module = "配置管理", operation = "更新配置")
    @Operation(summary = "更新配置信息", description = "更新LLM/STT/TTS配置；换掉当前生效的对象存储时先返回待确认，"
        + "带 confirmStorageSwitch=true 重发才执行")
    public ApiResponse<ConfigResp> update(@PathVariable Integer configId,
                                          @Valid @RequestBody ConfigUpdateReq req,
                                          @RequestParam(defaultValue = "false") boolean confirmStorageSwitch) {
        return ApiResponse.success(configAppService.update(configId, req, confirmStorageSwitch));
    }

    /**
     * 添加配置
     *
     * @param req 添加参数
     * @param confirmStorageSwitch 已确认换掉对象存储会让历史文件不可访问
     */
    @PostMapping("")
    @ResponseBody
    @SaCheckPermission("system:config:api:create")
    @AuditLog(module = "配置管理", operation = "创建配置")
    @Operation(summary = "添加配置信息", description = "添加新的LLM/STT/TTS配置；新增的对象存储配置直接设为默认时先返回待确认，"
        + "带 confirmStorageSwitch=true 重发才执行")
    public ApiResponse<ConfigResp> create(@Valid @RequestBody ConfigCreateReq req,
                                          @RequestParam(defaultValue = "false") boolean confirmStorageSwitch) {
        return ApiResponse.success(configAppService.create(req, StpUtil.getLoginIdAsInt(), confirmStorageSwitch));
    }

    /**
     * 测试配置
     *
     * @param req 测试参数（表单当前值，可能未保存）
     * @return
     */
    @PostMapping("/models")
    @SaCheckPermission("system:config:api:list")
    @CheckOwner(resource = "config", id = "#req.configId")
    @Operation(summary = "List models available to the ChatGPT gateway account")
    public ApiResponse<List<ConfigModelResp>> models(@Valid @RequestBody ConfigTestReq req) {
        return ApiResponse.success(configTestAppService.listModels(req, StpUtil.getLoginIdAsInt()));
    }

    @PostMapping("/test")
    @ResponseBody
    @SaCheckPermission("system:config:api:list")
    @CheckOwner(resource = "config", id = "#req.configId")
    @Operation(summary = "测试配置", description = "使用当前表单参数测试模型配置是否可用")
    public ApiResponse<ConfigTestResp> test(@Valid @RequestBody ConfigTestReq req) {
        long start = System.nanoTime();
        ConfigProbeResultBO result = configTestAppService.test(req, StpUtil.getLoginIdAsInt());
        var response = new ConfigTestResp(result.success(), (System.nanoTime() - start) / 1_000_000, result.message());
        return result.success() ? ApiResponse.success(result.message(), response) : ApiResponse.error(result.message(), response);
    }

    @PostMapping(value = "/test/stt", consumes = "multipart/form-data")
    @SaCheckPermission("system:config:api:list")
    @CheckOwner(resource = "config", id = "#req.configId")
    @Operation(summary = "Test STT with an uploaded 16 kHz mono PCM WAV (maximum 30 seconds)")
    public ApiResponse<ConfigTestResp> testSttAudio(@Valid @RequestPart("config") ConfigTestReq req,
                                        @RequestPart("audio") org.springframework.web.multipart.MultipartFile audio)
            throws java.io.IOException {
        if (audio.getSize() > 1025536) throw new IllegalArgumentException("WAV upload exceeds limit");
        byte[] pcm = PcmWavUpload.decode(audio.getBytes());
        long start = System.nanoTime();
        ConfigProbeResultBO result = configTestAppService.testSttAudio(req, StpUtil.getLoginIdAsInt(), pcm);
        var response = new ConfigTestResp(result.success(), (System.nanoTime() - start) / 1_000_000, result.message());
        return result.success() ? ApiResponse.success(result.message(), response) : ApiResponse.error(result.message(), response);
    }

    @PostMapping("/test/tts")
    @SaCheckPermission("system:config:api:list")
    @CheckOwner(resource = "config", id = "#req.configId")
    @Operation(summary = "Test TTS with current form settings and return signed audio preview URL")
    public ApiResponse<TestVoiceResp> testTtsAudio(@Valid @RequestBody ConfigTestReq req) {
        return ApiResponse.success(configTestAppService.testTtsAudio(req, StpUtil.getLoginIdAsInt()));
    }

    @PostMapping(value = "/test/vision", consumes = "multipart/form-data")
    @SaCheckPermission("system:config:api:list")
    @CheckOwner(resource = "config", id = "#req.configId")
    @Operation(summary = "Test Vision with the entered question and an uploaded image")
    public ApiResponse<ConfigTestResp> testVisionImage(@Valid @RequestPart("config") ConfigTestReq req,
            @RequestPart("image") org.springframework.web.multipart.MultipartFile image) throws java.io.IOException {
        var media = VisionImageUpload.read(image);
        long start = System.nanoTime();
        ConfigProbeResultBO result = configTestAppService.testVisionImage(req, StpUtil.getLoginIdAsInt(), media);
        var response = new ConfigTestResp(result.success(), (System.nanoTime() - start) / 1_000_000, result.message());
        return result.success() ? ApiResponse.success(result.message(), response) : ApiResponse.error(result.message(), response);
    }

    /**
     * 删除配置信息
     *
     * @param configId 配置ID
     * @return
     */
    @DeleteMapping("/{configId}")
    @ResponseBody
    @SaCheckPermission("system:config:api:delete")
    @CheckOwner(resource = "configWrite", id = "#configId")
    @AuditLog(module = "配置管理", operation = "删除配置")
    @Operation(summary = "删除配置信息", description = "软删除指定配置")
    public ApiResponse<Void> delete(@PathVariable Integer configId,
                                   @RequestParam(defaultValue = "false") boolean confirmStorageSwitch) {
        configAppService.delete(configId, confirmStorageSwitch);
        return ApiResponse.success("删除成功");
    }
}
