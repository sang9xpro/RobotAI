-- Read access is available to ordinary roles. The API determines scope from the
-- authenticated identity, never from query parameters. Only admin sees all owners.
INSERT INTO sys_permission(parentId,name,permissionKey,permissionType,path,component,icon,sort,visible,status)
SELECT NULL,'Device conversation history','system:device-history','menu','/device-history',NULL,'MessageOutlined',7,'1','1'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permissionKey='system:device-history');
INSERT INTO sys_permission(parentId,name,permissionKey,permissionType,sort,visible,status)
SELECT permissionId,'Read device history','system:device-history:api:list','api',1,'0','1'
FROM sys_permission WHERE permissionKey='system:device-history'
AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE permissionKey='system:device-history:api:list');
INSERT INTO sys_auth_role_permission(authRoleId,permissionId)
SELECT role.authRoleId,p.permissionId FROM sys_auth_role role
JOIN sys_permission p ON p.permissionKey IN ('system:device-history','system:device-history:api:list')
LEFT JOIN sys_auth_role_permission existing ON existing.authRoleId=role.authRoleId AND existing.permissionId=p.permissionId
WHERE existing.id IS NULL;

-- The history screen orders across all personas of a device or user.
SET @add_idx = (SELECT IF(COUNT(*)=0,
 'ALTER TABLE sys_message ADD INDEX history_device_time (deviceId,createTime,messageId)', 'SELECT 1')
 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_message' AND INDEX_NAME='history_device_time');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @add_idx = (SELECT IF(COUNT(*)=0,
 'ALTER TABLE sys_message ADD INDEX history_user_time (userId,createTime,messageId)', 'SELECT 1')
 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_message' AND INDEX_NAME='history_user_time');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
