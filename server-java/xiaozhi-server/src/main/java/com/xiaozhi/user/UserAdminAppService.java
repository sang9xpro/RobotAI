package com.xiaozhi.user;

import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.*;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.device.DeviceAppService;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.model.AdminRobotProjection;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.common.model.resp.AdminRobotResp;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.security.service.AuthenticationService;
import com.xiaozhi.user.convert.UserConvert;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Objects;

/** Privileged user administration; ownership is always the selected target account. */
@Service
public class UserAdminAppService {
    @Resource private UserService userService;
    @Resource private UserAppService userAppService;
    @Resource private UserConvert userConvert;
    @Resource private AuthRoleService authRoleService;
    @Resource private AuthenticationService authenticationService;
    @Resource private DeviceAppService deviceAppService;
    @Resource private DeviceRepository deviceRepository;
    @Resource private DeviceMapper deviceMapper;
    @Resource private RoleService roleService;

    public UserResp get(Integer userId) {
        return userConvert.toResp(requireUser(userId));
    }

    @Transactional
    public UserResp create(AdminUserCreateReq req) {
        int roleId = req.getAuthRoleId() == null ? 2 : req.getAuthRoleId();
        requireRole(roleId);
        UserBO user = new UserBO();
        user.setUsername(req.getUsername());
        user.setPassword(authenticationService.encryptPassword(req.getPassword()));
        user.setName(StringUtils.hasText(req.getName()) ? req.getName().trim() : req.getUsername());
        user.setEmail(blankToNull(req.getEmail()));
        user.setTel(blankToNull(req.getTel()));
        user.setAuthRoleId(roleId);
        user.setIsAdmin(UserBO.ADMIN_NO);
        user.setState(UserBO.STATE_ENABLED);
        UserBO created = userAppService.createUserWithDefaults(user);
        if (req.getDevices() != null && !req.getDevices().isEmpty()) {
            assignDevices(created.getUserId(), req.getDevices());
        }
        return userConvert.toResp(created);
    }

    @Transactional
    public UserResp update(Integer userId, AdminUserUpdateReq req, Integer operatorId) {
        UserBO existing = requireUser(userId);
        if (req.getAuthRoleId() != null && !Objects.equals(req.getAuthRoleId(), existing.getAuthRoleId())) {
            if (Objects.equals(userId, operatorId) || UserBO.ADMIN_YES.equals(existing.getIsAdmin())) {
                throw new IllegalArgumentException("Không thể đổi vai trò của tài khoản đang đăng nhập hoặc quản trị viên hệ thống");
            }
            requireRole(req.getAuthRoleId());
        }
        UserBO patch = new UserBO();
        patch.setUserId(userId);
        patch.setName(req.getName());
        patch.setEmail(req.getEmail());
        patch.setTel(req.getTel());
        patch.setAuthRoleId(req.getAuthRoleId());
        return userConvert.toResp(userService.update(patch));
    }

    public PageResult<DeviceResp> devices(Integer userId, DevicePageReq req) {
        requireUser(userId);
        return deviceAppService.page(req, userId);
    }

    public PageResult<AdminRobotResp> robots(String search, int pageNo, int pageSize) {
        var page = deviceMapper.selectAdminRobots(new Page<AdminRobotProjection>(pageNo, pageSize), search);
        return new PageResult<>(page.getRecords().stream().map(d -> new AdminRobotResp(
                d.getDeviceId(), d.getDeviceName(), d.getState(), d.getType(),
                d.getUserId(), d.getUsername(), d.getOwnerName())).toList(), page.getTotal(), pageNo, pageSize);
    }

    /** A conflict rolls back the whole assignment, including a newly created account. */
    @Transactional
    public int assignDevices(Integer userId, List<AdminUserDevicesReq.Assignment> assignments) {
        userService.requireEnabled(requireUser(userId));
        if (assignments == null || assignments.isEmpty() || assignments.size() > 20
                || assignments.stream().anyMatch(a -> a == null || a.getDeviceId() == null
                || !a.getDeviceId().matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))) {
            throw new IllegalArgumentException("Chọn từ 1 đến 20 robot đã đăng ký");
        }
        var ids = assignments.stream().map(a -> a.getDeviceId().toLowerCase(Locale.ROOT)).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Thiết bị bị chọn trùng");
        }
        var devices = deviceRepository.lockByIds(ids);
        if (devices.size() != ids.size()) throw new ResourceNotFoundException("Có robot không còn tồn tại; hãy tải lại danh sách");
        for (Device device : devices) {
            var selected = assignments.get(ids.indexOf(device.getDeviceId().toLowerCase(Locale.ROOT)));
            if (!Objects.equals(device.getUserId(), selected.getExpectedUserId())) {
                throw new IllegalStateException("Chủ sở hữu robot đã thay đổi; hãy tải lại danh sách trước khi gán");
            }
        }
        var role = roleService.getDefaultOrFirstBO(userId);
        if (role == null || !Objects.equals(role.getUserId(), userId)) {
            throw new IllegalStateException("Người dùng chưa có nhân vật để sử dụng robot");
        }
        int changed = 0;
        for (Device device : devices) {
            if (Objects.equals(device.getUserId(), userId)) continue;
            device.reassignUser(userId, role.getRoleId());
            deviceRepository.save(device);
            changed++;
        }
        return changed;
    }

    private UserBO requireUser(Integer userId) {
        UserBO user = userService.getBO(userId);
        if (user == null) throw new ResourceNotFoundException("Người dùng không tồn tại");
        return user;
    }

    private void requireRole(Integer roleId) {
        var role = authRoleService.getBO(roleId);
        if (role == null || !"1".equals(role.getStatus())) {
            throw new IllegalArgumentException("Vai trò phân quyền không tồn tại hoặc đã bị tắt");
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
