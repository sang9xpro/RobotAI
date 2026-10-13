package com.xiaozhi.robot;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.port.PersonalMemoryStore;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.role.service.RoleService;
import org.springframework.web.bind.annotation.*;
import java.util.Objects;

@RestController
@RequestMapping("/api/robot/people/{personId}/memory")
public class RobotPersonalMemoryController {
    private final PersonalMemoryStore store;
    private final RoleService roles;
    public RobotPersonalMemoryController(PersonalMemoryStore store,RoleService roles) { this.store=store;this.roles=roles; }
    private PersonalMemoryStore.Scope owned(int roleId,String personId) {
        StpUtil.checkLogin(); int user=StpUtil.getLoginIdAsInt();
        var role=roles.getBO(roleId);
        if(role==null || !Objects.equals(user,role.getUserId())) throw new IllegalArgumentException("Role unavailable");
        return new PersonalMemoryStore.Scope(user,"user_chat_"+user,roleId,personId);
    }
    @GetMapping public ApiResponse<com.xiaozhi.common.model.resp.PersonalMemoryResp> get(@PathVariable String personId,@RequestParam int roleId) {
        var snapshot=store.read(owned(roleId,personId));
        return ApiResponse.success(new com.xiaozhi.common.model.resp.PersonalMemoryResp(snapshot.enabled(),snapshot.revision(),
                snapshot.facts().stream().map(f->new com.xiaozhi.common.model.resp.PersonalMemoryResp.FactResp(f.key(),f.value())).toList()));
    }
    @PutMapping public ApiResponse<Void> edit(@PathVariable String personId,@RequestParam int roleId,@RequestBody com.xiaozhi.common.model.req.PersonalMemoryReq fact) {
        store.edit(owned(roleId,personId),new PersonalMemoryStore.Fact(fact.key(),fact.value()));return ApiResponse.success("Đã sửa ký ức");
    }
    @DeleteMapping("/{key}") public ApiResponse<Void> delete(@PathVariable String personId,@RequestParam int roleId,@PathVariable String key) {
        store.delete(owned(roleId,personId),key);return ApiResponse.success("Đã xóa ký ức");
    }
    public record Enabled(boolean enabled) {}
    @PatchMapping public ApiResponse<Void> enable(@PathVariable String personId,@RequestParam int roleId,@RequestBody Enabled setting) {
        store.enable(owned(roleId,personId),setting.enabled());return ApiResponse.success("Đã đổi ghi nhớ");
    }
    @DeleteMapping public ApiResponse<Void> forget(@PathVariable String personId,@RequestParam int roleId) {
        store.forget(owned(roleId,personId));return ApiResponse.success("Đã quên người này");
    }
}
