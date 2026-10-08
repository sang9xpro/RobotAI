package com.xiaozhi.robot;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.ai.llm.service.VisionService;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.SummaryResp;
import com.xiaozhi.common.model.resp.RobotVisionResp;
import com.xiaozhi.common.model.resp.RobotWeatherResp;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.summary.convert.SummaryConvert;
import com.xiaozhi.summary.service.SummaryService;
import com.xiaozhi.server.web.BaseController;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Objects;

@RestController
@RequestMapping("/api/robot")
public class RobotCompanionController extends BaseController {
    @Resource private RoleService roleService;
    @Resource private SummaryService summaryService;
    @Resource private SummaryConvert summaryConvert;
    @Resource private VisionService visionService;
    @Resource private RobotWeatherClient weatherClient;

    @GetMapping("/weather")
    public ApiResponse<RobotWeatherResp> weather(@RequestParam double latitude, @RequestParam double longitude)
            throws java.io.IOException, InterruptedException {
        StpUtil.checkLogin();
        return ApiResponse.success(weatherClient.current(latitude,longitude));
    }

    private String ownedDevice(int roleId) {
        int user = StpUtil.getLoginIdAsInt();
        var role = roleService.getBO(roleId);
        if (role == null || !Objects.equals(role.getUserId(), user))
            throw new IllegalArgumentException("Role unavailable");
        return "user_chat_" + user;
    }
    @GetMapping("/memories")
    public ApiResponse<PageResult<SummaryResp>> memories(@RequestParam int roleId,
            @RequestParam(defaultValue="1") int pageNo) {
        String device = ownedDevice(roleId);
        return ApiResponse.success(summaryService.page(device, StpUtil.getLoginIdAsInt(), roleId,
            Math.max(1, pageNo), 20).map(summaryConvert::toResp));
    }
    public record MemoryEdit(@NotBlank @Size(max=8000) String text) {}
    @PutMapping("/memories/{roleId}/{id}")
    public ApiResponse<Integer> editMemory(@PathVariable int roleId, @PathVariable long id,
            @Valid @RequestBody MemoryEdit request) {
        return ApiResponse.success(summaryService.updateText(roleId, ownedDevice(roleId), id, request.text()));
    }
    @DeleteMapping("/memories/{roleId}/{id}")
    public ApiResponse<Integer> deleteMemory(@PathVariable int roleId, @PathVariable long id) {
        return ApiResponse.success(summaryService.delete(roleId, ownedDevice(roleId), id));
    }
    @PostMapping(value="/vision", consumes="multipart/form-data")
    public ApiResponse<RobotVisionResp> vision(@RequestParam MultipartFile image,
            @RequestParam String question, @RequestParam(defaultValue="false") boolean consent) {
        StpUtil.checkLogin();
        if (!consent) throw new IllegalArgumentException("Image sharing consent required");
        if (question.isBlank() || question.length() > 2000 || image.isEmpty() || image.getSize() > 2_000_000
            || !java.util.Set.of("image/jpeg", "image/png").contains(Objects.toString(image.getContentType(), "")))
            throw new IllegalArgumentException("Invalid image or question");
        return ApiResponse.success(new RobotVisionResp(visionService.recognize(image, question)));
    }
}
