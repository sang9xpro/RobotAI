/** Stable RBAC keys; labels are independent of database language and permission IDs. */
export const permissionLabels: Record<string, readonly [string, string, string]> = {
  'system:dashboard': ['Dashboard', 'Tổng quan', 'Dashboard'],
  'system:user': ['User management', 'Quản lý người dùng', '用户管理'],
  'system:device': ['Device management', 'Quản lý thiết bị', '设备管理'],
  'system:device-history': ['Device history', 'Lịch sử thiết bị', '设备对话历史'],
  'system:device-history:api:list': ['Read device history API', 'API xem lịch sử thiết bị', '设备历史查询接口'],
  'system:role': ['Character configuration', 'Cấu hình nhân vật', '角色配置'],
  'system:prompt-template': ['Prompt template management', 'Quản lý mẫu prompt', '提示词模板管理'],
  'system:config': ['Configuration management', 'Quản lý cấu hình', '配置管理'],
  'system:setting': ['Settings', 'Cài đặt', '设置'],
  'system:auth-role': ['Access roles', 'Vai trò phân quyền', '权限角色'],
  'system:config:model': ['LLM configuration', 'Cấu hình LLM', '模型配置'],
  'system:config:agent': ['Agent management', 'Quản lý agent', '智能体管理'],
  'system:config:stt': ['Speech recognition configuration', 'Cấu hình nhận dạng giọng nói', '语音识别配置'],
  'system:config:tts': ['Speech synthesis configuration', 'Cấu hình tổng hợp giọng nói', '语音合成配置'],
  'system:setting:account': ['Account profile', 'Hồ sơ tài khoản', '个人中心'],
  'system:auth-role:assign': ['Save permissions', 'Lưu quyền', '保存授权'],
  'system:file': ['File services', 'Dịch vụ tệp', '文件能力'],
  'system:user:api:list': ['List users API', 'API danh sách người dùng', '用户列表接口'],
  'system:device:api:list': ['List devices API', 'API danh sách thiết bị', '设备列表接口'],
  'system:device:api:create': ['Create device API', 'API tạo thiết bị', '设备创建接口'],
  'system:device:api:update': ['Update device API', 'API cập nhật thiết bị', '设备更新接口'],
  'system:device:api:delete': ['Delete device API', 'API xóa thiết bị', '设备删除接口'],
  'system:device:api:batch-update': ['Batch update devices API', 'API cập nhật hàng loạt thiết bị', '设备批量更新接口'],
  'system:device:memory:api:delete': ['Delete device memory API', 'API xóa ký ức thiết bị', '设备记忆删除接口'],
  'system:role:memory:chat:api:list': ['List conversations API', 'API danh sách hội thoại', '对话列表接口'],
  'system:role:memory:chat:api:delete': ['Delete conversation API', 'API xóa hội thoại', '对话删除接口'],
  'system:role:api:list': ['List characters API', 'API danh sách nhân vật', '角色列表接口'],
  'system:role:api:create': ['Create character API', 'API tạo nhân vật', '角色创建接口'],
  'system:role:api:update': ['Update character API', 'API cập nhật nhân vật', '角色更新接口'],
  'system:role:api:delete': ['Delete character API', 'API xóa nhân vật', '角色删除接口'],
  'system:role:memory:summary:api:list': ['List memory summaries API', 'API danh sách tóm tắt ký ức', '摘要记忆列表接口'],
  'system:role:memory:summary:api:delete': ['Delete memory summary API', 'API xóa tóm tắt ký ức', '摘要记忆删除接口'],
  'system:prompt-template:api:list': ['List prompt templates API', 'API danh sách mẫu prompt', '模板列表接口'],
  'system:prompt-template:api:create': ['Create prompt template API', 'API tạo mẫu prompt', '模板创建接口'],
  'system:prompt-template:api:update': ['Update prompt template API', 'API cập nhật mẫu prompt', '模板更新接口'],
  'system:prompt-template:api:delete': ['Delete prompt template API', 'API xóa mẫu prompt', '模板删除接口'],
  'system:config:api:list': ['List configurations API', 'API danh sách cấu hình', '配置列表接口'],
  'system:config:api:create': ['Create configuration API', 'API tạo cấu hình', '配置创建接口'],
  'system:config:api:update': ['Update configuration API', 'API cập nhật cấu hình', '配置更新接口'],
  'system:config:api:delete': ['Delete configuration API', 'API xóa cấu hình', '配置删除接口'],
  'system:config:agent:api:list': ['List agents API', 'API danh sách agent', '智能体列表接口'],
  'system:setting:account:api:update': ['Update account profile API', 'API cập nhật hồ sơ tài khoản', '个人信息更新接口'],
  'system:auth-role:api:list': ['List access roles API', 'API danh sách vai trò phân quyền', '角色列表接口'],
  'system:auth-role:api:detail': ['Access role details API', 'API chi tiết vai trò phân quyền', '角色详情接口'],
  'system:auth-role:api:assign': ['Assign access role permissions API', 'API cấp quyền cho vai trò', '角色授权接口'],
  'system:file:api:upload': ['Upload file API', 'API tải tệp lên', '文件上传接口'],
  'system:chat': ['Web chat', 'Trò chuyện trên web', 'Web 聊天'],
  'system:chat:api:open': ['Open chat session', 'Mở phiên trò chuyện', '开启会话'],
  'system:chat:api:stream': ['Stream chat', 'Trò chuyện dạng stream', '流式聊天'],
  'system:chat:api:close': ['Close chat session', 'Đóng phiên trò chuyện', '关闭会话'],
  'system:user:api:update': ['Update user status API', 'API cập nhật trạng thái người dùng', '用户状态更新接口'],
  'system:role:mcp-tools:api:list': ['List character MCP tools API', 'API danh sách công cụ MCP của nhân vật', '角色MCP工具列表接口'],
  'system:role:mcp-tools:api:update': ['Update character MCP tools API', 'API cập nhật công cụ MCP của nhân vật', '角色MCP工具更新接口'],
  'system:role:mcp-tools:api:system-global': ['List system MCP tools API', 'API danh sách công cụ MCP hệ thống', '系统MCP工具列表接口'],
  'system:config:mcpServer:api:update': ['Toggle global MCP tools API', 'API bật/tắt công cụ MCP toàn hệ thống', 'MCP工具全局开关接口'],
  'system:robot:music:list': ['Music library', 'Thư viện nhạc', '音乐库'],
  'system:robot:music:update': ['Edit music library', 'Sửa thư viện nhạc', '编辑音乐库'],
  'system:robot:status': ['Service status', 'Trạng thái dịch vụ', '服务状态'],
}

export const builtInRoleLabels = {
  admin: {
    name: ['Administrator', 'Quản trị viên', '管理员'],
    description: ['System administrator with all permissions', 'Quản trị hệ thống, có tất cả quyền', '系统管理员，拥有所有权限'],
  },
  user: {
    name: ['Regular user', 'Người dùng thông thường', '普通用户'],
    description: ['Regular user with basic permissions', 'Người dùng thông thường, có quyền thao tác cơ bản', '普通用户，拥有基本操作权限'],
  },
} as const

function messages(index: 0 | 1 | 2) {
  return {
    permissions: Object.fromEntries(Object.entries(permissionLabels).map(([key, labels]) => [key, labels[index]])),
    roles: Object.fromEntries(Object.entries(builtInRoleLabels).map(([key, labels]) => [key, {
      name: labels.name[index], description: labels.description[index],
    }])),
  }
}
export const accessControlEn = messages(0)
export const accessControlVi = messages(1)
export const accessControlZh = messages(2)
