-- Canonical English labels for built-in access control data.

-- Guard by stable key and original label to preserve administrator customizations.

-- Preserve IDs, keys, routes, grants and timestamps. Do not edit historical migrations.

UPDATE sys_permission SET name='Dashboard', updateTime=updateTime
WHERE permissionKey='system:dashboard' AND name IN ('Tổng quan');

UPDATE sys_permission SET name='User management', updateTime=updateTime
WHERE permissionKey='system:user' AND name IN ('用户管理','Quản lý người dùng');

UPDATE sys_permission SET name='Device management', updateTime=updateTime
WHERE permissionKey='system:device' AND name IN ('设备管理','Quản lý thiết bị');

UPDATE sys_permission SET name='Character configuration', updateTime=updateTime
WHERE permissionKey='system:role' AND name IN ('角色配置','Cấu hình nhân vật');

UPDATE sys_permission SET name='Prompt template management', updateTime=updateTime
WHERE permissionKey='system:prompt-template' AND name IN ('提示词模板管理','Quản lý mẫu prompt');

UPDATE sys_permission SET name='Configuration management', updateTime=updateTime
WHERE permissionKey='system:config' AND name IN ('配置管理','Quản lý cấu hình');

UPDATE sys_permission SET name='Settings', updateTime=updateTime
WHERE permissionKey='system:setting' AND name IN ('设置','Cài đặt');

UPDATE sys_permission SET name='Access roles', updateTime=updateTime
WHERE permissionKey='system:auth-role' AND name IN ('权限角色','Vai trò phân quyền');

UPDATE sys_permission SET name='LLM configuration', updateTime=updateTime
WHERE permissionKey='system:config:model' AND name IN ('模型配置','Cấu hình LLM');

UPDATE sys_permission SET name='Agent management', updateTime=updateTime
WHERE permissionKey='system:config:agent' AND name IN ('智能体管理','Quản lý agent');

UPDATE sys_permission SET name='Speech recognition configuration', updateTime=updateTime
WHERE permissionKey='system:config:stt' AND name IN ('语音识别配置','Cấu hình nhận dạng giọng nói');

UPDATE sys_permission SET name='Speech synthesis configuration', updateTime=updateTime
WHERE permissionKey='system:config:tts' AND name IN ('语音合成配置','Cấu hình tổng hợp giọng nói');

UPDATE sys_permission SET name='Account profile', updateTime=updateTime
WHERE permissionKey='system:setting:account' AND name IN ('个人中心','Hồ sơ tài khoản');

UPDATE sys_permission SET name='Save permissions', updateTime=updateTime
WHERE permissionKey='system:auth-role:assign' AND name IN ('保存授权','Lưu quyền');

UPDATE sys_permission SET name='File services', updateTime=updateTime
WHERE permissionKey='system:file' AND name IN ('文件能力','Dịch vụ tệp');

UPDATE sys_permission SET name='List users API', updateTime=updateTime
WHERE permissionKey='system:user:api:list' AND name IN ('用户列表接口','API danh sách người dùng');

UPDATE sys_permission SET name='List devices API', updateTime=updateTime
WHERE permissionKey='system:device:api:list' AND name IN ('设备列表接口','API danh sách thiết bị');

UPDATE sys_permission SET name='Create device API', updateTime=updateTime
WHERE permissionKey='system:device:api:create' AND name IN ('设备创建接口','API tạo thiết bị');

UPDATE sys_permission SET name='Update device API', updateTime=updateTime
WHERE permissionKey='system:device:api:update' AND name IN ('设备更新接口','API cập nhật thiết bị');

UPDATE sys_permission SET name='Delete device API', updateTime=updateTime
WHERE permissionKey='system:device:api:delete' AND name IN ('设备删除接口','API xóa thiết bị');

UPDATE sys_permission SET name='Batch update devices API', updateTime=updateTime
WHERE permissionKey='system:device:api:batch-update' AND name IN ('设备批量更新接口','API cập nhật hàng loạt thiết bị');

UPDATE sys_permission SET name='Delete device memory API', updateTime=updateTime
WHERE permissionKey='system:device:memory:api:delete' AND name IN ('设备记忆删除接口','API xóa ký ức thiết bị');

UPDATE sys_permission SET name='List conversations API', updateTime=updateTime
WHERE permissionKey='system:role:memory:chat:api:list' AND name IN ('对话列表接口','API danh sách hội thoại');

UPDATE sys_permission SET name='Delete conversation API', updateTime=updateTime
WHERE permissionKey='system:role:memory:chat:api:delete' AND name IN ('对话删除接口','API xóa hội thoại');

UPDATE sys_permission SET name='List characters API', updateTime=updateTime
WHERE permissionKey='system:role:api:list' AND name IN ('角色列表接口','API danh sách nhân vật');

UPDATE sys_permission SET name='Create character API', updateTime=updateTime
WHERE permissionKey='system:role:api:create' AND name IN ('角色创建接口','API tạo nhân vật');

UPDATE sys_permission SET name='Update character API', updateTime=updateTime
WHERE permissionKey='system:role:api:update' AND name IN ('角色更新接口','API cập nhật nhân vật');

UPDATE sys_permission SET name='Delete character API', updateTime=updateTime
WHERE permissionKey='system:role:api:delete' AND name IN ('角色删除接口','API xóa nhân vật');

UPDATE sys_permission SET name='List memory summaries API', updateTime=updateTime
WHERE permissionKey='system:role:memory:summary:api:list' AND name IN ('摘要记忆列表接口','API danh sách tóm tắt ký ức');

UPDATE sys_permission SET name='Delete memory summary API', updateTime=updateTime
WHERE permissionKey='system:role:memory:summary:api:delete' AND name IN ('摘要记忆删除接口','API xóa tóm tắt ký ức');

UPDATE sys_permission SET name='List prompt templates API', updateTime=updateTime
WHERE permissionKey='system:prompt-template:api:list' AND name IN ('模板列表接口','API danh sách mẫu prompt');

UPDATE sys_permission SET name='Create prompt template API', updateTime=updateTime
WHERE permissionKey='system:prompt-template:api:create' AND name IN ('模板创建接口','API tạo mẫu prompt');

UPDATE sys_permission SET name='Update prompt template API', updateTime=updateTime
WHERE permissionKey='system:prompt-template:api:update' AND name IN ('模板更新接口','API cập nhật mẫu prompt');

UPDATE sys_permission SET name='Delete prompt template API', updateTime=updateTime
WHERE permissionKey='system:prompt-template:api:delete' AND name IN ('模板删除接口','API xóa mẫu prompt');

UPDATE sys_permission SET name='List configurations API', updateTime=updateTime
WHERE permissionKey='system:config:api:list' AND name IN ('配置列表接口','API danh sách cấu hình');

UPDATE sys_permission SET name='Create configuration API', updateTime=updateTime
WHERE permissionKey='system:config:api:create' AND name IN ('配置创建接口','API tạo cấu hình');

UPDATE sys_permission SET name='Update configuration API', updateTime=updateTime
WHERE permissionKey='system:config:api:update' AND name IN ('配置更新接口','API cập nhật cấu hình');

UPDATE sys_permission SET name='Delete configuration API', updateTime=updateTime
WHERE permissionKey='system:config:api:delete' AND name IN ('配置删除接口','API xóa cấu hình');

UPDATE sys_permission SET name='List agents API', updateTime=updateTime
WHERE permissionKey='system:config:agent:api:list' AND name IN ('智能体列表接口','API danh sách agent');

UPDATE sys_permission SET name='Update account profile API', updateTime=updateTime
WHERE permissionKey='system:setting:account:api:update' AND name IN ('个人信息更新接口','API cập nhật hồ sơ tài khoản');

UPDATE sys_permission SET name='List access roles API', updateTime=updateTime
WHERE permissionKey='system:auth-role:api:list' AND name IN ('角色列表接口','API danh sách vai trò phân quyền');

UPDATE sys_permission SET name='Access role details API', updateTime=updateTime
WHERE permissionKey='system:auth-role:api:detail' AND name IN ('角色详情接口','API chi tiết vai trò phân quyền');

UPDATE sys_permission SET name='Assign access role permissions API', updateTime=updateTime
WHERE permissionKey='system:auth-role:api:assign' AND name IN ('角色授权接口','API cấp quyền cho vai trò');

UPDATE sys_permission SET name='Upload file API', updateTime=updateTime
WHERE permissionKey='system:file:api:upload' AND name IN ('文件上传接口','API tải tệp lên');

UPDATE sys_permission SET name='Web chat', updateTime=updateTime
WHERE permissionKey='system:chat' AND name IN ('Web 聊天','Trò chuyện trên web');

UPDATE sys_permission SET name='Open chat session', updateTime=updateTime
WHERE permissionKey='system:chat:api:open' AND name IN ('开启会话','Mở phiên trò chuyện');

UPDATE sys_permission SET name='Stream chat', updateTime=updateTime
WHERE permissionKey='system:chat:api:stream' AND name IN ('流式聊天','Trò chuyện dạng stream');

UPDATE sys_permission SET name='Close chat session', updateTime=updateTime
WHERE permissionKey='system:chat:api:close' AND name IN ('关闭会话','Đóng phiên trò chuyện');

UPDATE sys_permission SET name='Update user status API', updateTime=updateTime
WHERE permissionKey='system:user:api:update' AND name IN ('用户状态更新接口','API cập nhật trạng thái người dùng');

UPDATE sys_permission SET name='List character MCP tools API', updateTime=updateTime
WHERE permissionKey='system:role:mcp-tools:api:list' AND name IN ('角色MCP工具列表接口','API danh sách công cụ MCP của nhân vật');

UPDATE sys_permission SET name='Update character MCP tools API', updateTime=updateTime
WHERE permissionKey='system:role:mcp-tools:api:update' AND name IN ('角色MCP工具更新接口','API cập nhật công cụ MCP của nhân vật');

UPDATE sys_permission SET name='List system MCP tools API', updateTime=updateTime
WHERE permissionKey='system:role:mcp-tools:api:system-global' AND name IN ('系统MCP工具列表接口','API danh sách công cụ MCP hệ thống');

UPDATE sys_permission SET name='Toggle global MCP tools API', updateTime=updateTime
WHERE permissionKey='system:config:mcpServer:api:update' AND name IN ('MCP工具全局开关接口','API bật/tắt công cụ MCP toàn hệ thống');

UPDATE sys_permission SET name='Music library', updateTime=updateTime
WHERE permissionKey='system:robot:music:list' AND name IN ('音乐库','Thư viện nhạc');

UPDATE sys_permission SET name='Edit music library', updateTime=updateTime
WHERE permissionKey='system:robot:music:update' AND name IN ('编辑音乐库','Sửa thư viện nhạc');

UPDATE sys_permission SET name='Service status', updateTime=updateTime
WHERE permissionKey='system:robot:status' AND name IN ('服务状态','Trạng thái dịch vụ');

UPDATE sys_auth_role SET authRoleName='Administrator', updateTime=updateTime
WHERE roleKey='admin' AND authRoleName='管理员';

UPDATE sys_auth_role SET description='System administrator with all permissions', updateTime=updateTime
WHERE roleKey='admin' AND description='系统管理员，拥有所有权限';

UPDATE sys_auth_role SET authRoleName='Regular user', updateTime=updateTime
WHERE roleKey='user' AND authRoleName='普通用户';

UPDATE sys_auth_role SET description='Regular user with basic permissions', updateTime=updateTime
WHERE roleKey='user' AND description='普通用户，拥有基本操作权限';
