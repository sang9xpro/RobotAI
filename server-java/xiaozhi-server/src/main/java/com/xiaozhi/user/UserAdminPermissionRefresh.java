package com.xiaozhi.user;

import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.permission.service.PermissionService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Flyway changes permission catalogs while Redis may retain the previous version. */
@Component
public class UserAdminPermissionRefresh {
    private final AuthRoleService roles;
    private final PermissionService permissions;
    public UserAdminPermissionRefresh(AuthRoleService roles, PermissionService permissions) {
        this.roles = roles; this.permissions = permissions;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void refresh() {
        int page = 1;
        while (true) {
            var batch = roles.page(page++, 1000, null, null, null).getList();
            batch.forEach(role -> permissions.clearAuthRoleCache(role.getAuthRoleId()));
            if (batch.size() < 1000) break;
        }
    }
}
