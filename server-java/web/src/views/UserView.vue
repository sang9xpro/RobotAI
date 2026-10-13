<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, type TableColumnsType } from 'ant-design-vue'
import { useTable } from '@/composables/useTable'
import { useExport } from '@/composables/useExport'
import { useRequest } from '@/composables/useRequest'
import TableEmptyState from '@/components/TableEmptyState.vue'
import { queryAuthRoles } from '@/services/authRole'
import { useLoadingStore } from '@/store/loading'
import { queryUsers, updateUserState } from '@/services/user'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AdminUserDialog from '@/components/AdminUserDialog.vue'
import UserDevicesDialog from '@/components/UserDevicesDialog.vue'
import { useAvatar } from '@/composables/useAvatar'
import type { AuthRole } from '@/types/authRole'
import type { User, UserQueryParams } from '@/types/user'
import { authRoleDisplayName } from '@/utils/accessControlLabels'
import dayjs from 'dayjs'

const { t } = useI18n()
const { getAvatarUrl } = useAvatar()
const { exporting, exportToCSV } = useExport()
const userStore = useUserStore()
const { confirmAsync } = useConfirm()
const { executeOk: executeState } = useRequest()
const changingState = ref<number>()
const editorVisible = ref(false)
const editingUser = ref<User | null>(null)
const devicesVisible = ref(false)
const selectedUser = ref<User | null>(null)
function openEditor(user: User | null = null) { editingUser.value = user; editorVisible.value = true }
function openDevices(user: User) { selectedUser.value = user; devicesVisible.value = true }
async function changeState(user: User) {
  if (user.userId === userStore.userInfo?.userId || changingState.value !== undefined) return
  const state = user.state === '1' ? '0' : '1'
  if (!await confirmAsync({ title: state === '0' ? t('robotai.disableUser') : t('robotai.enableUser'),
    content: state === '0' ? t('robotai.disableUserHint') : t('robotai.enableUserHint') })) return
  changingState.value = user.userId
  try {
    await executeState(() => updateUserState(user.userId, state), {
      showSuccess: true, successText: t('common.updateSuccess'), onSuccess: () => fetchData(),
    })
  } finally { changingState.value = undefined }
}

// 表格和分页：分页参数由 useTable 注入，查询条件走 buildQueryParams
const {
  loading,
  data,
  pagination,
  loadError,
  retryLoad,
  fetchData,
  onTableChange,
  debouncedSearch,
} = useTable<User, UserQueryParams>((params) => queryUsers(params), buildQueryParams)

// 全局 Loading
const loadingStore = useLoadingStore()
const authRoleOptions = ref<AuthRole[]>([])
function userAuthRoleName(user: User) {
  const role = authRoleOptions.value.find((item) => item.authRoleId === user.authRoleId)
  return role ? authRoleDisplayName({ ...role, authRoleName: user.authRoleName || role.authRoleName }, t) : user.authRoleName || '-'
}
const { execute: executeAuthRoles } = useRequest()

// 查询表单
const queryForm = reactive({
  name: '',
  email: '',
  tel: '',
  authRoleId: undefined as number | undefined,
})

// 查询过滤器配置：文案跟着 i18n key 走，切换语言后要能刷新，不能是 setup 期一次性求值的普通数组
const queryFilters = computed(() => [
  { label: t('common.name'), key: 'name' as const, placeholder: t('common.name') },
  { label: t('user.email'), key: 'email' as const, placeholder: t('user.email') },
  { label: t('user.phone'), key: 'tel' as const, placeholder: t('user.phone') },
])

// 表格列配置
const columns = computed<TableColumnsType>(() => [
  { title: t('user.username'), dataIndex: 'username', width: 160, fixed: 'left', align: 'center' },
  {
    title: t('common.name'),
    dataIndex: 'name',
    width: 100,
    fixed: 'left',
    align: 'center'
  },
  {
    title: t('common.avatar'),
    dataIndex: 'avatar',
    width: 80,
    fixed: 'left',
    align: 'center',
  },
  {
    title: t('user.email'),
    dataIndex: 'email',
    width: 180,
    align: 'center'
  },
  {
    title: t('user.phone'),
    dataIndex: 'tel',
    width: 150,
    align: 'center'
  },
  {
    title: t('user.deviceCount'),
    dataIndex: 'totalDevice',
    width: 100,
    align: 'center',
  },
  {
    title: t('user.onlineDeviceCount'),
    dataIndex: 'aliveNumber',
    width: 120,
    align: 'center',
  },
  {
    title: t('user.messageCount'),
    dataIndex: 'totalMessage',
    width: 120,
    align: 'center',
  },
  {
    title: t('common.status'),
    dataIndex: 'state',
    width: 80,
    align: 'center',
  },
  {
    title: t('user.accountType'),
    dataIndex: 'isAdmin',
    width: 100,
    align: 'center',
  },
  {
    title: t('user.authRole'),
    dataIndex: 'authRoleName',
    width: 140,
    align: 'center',
  },
  {
    title: t('user.lastLoginTime'),
    dataIndex: 'loginTime',
    width: 150,
    align: 'center',
  },
  {
    title: t('user.lastLoginIp'),
    dataIndex: 'loginIp',
    width: 150,
    align: 'center'
  },
  { title: t('table.action'), dataIndex: 'operation', width: 260, fixed: 'right', align: 'center' },
])

// 后端 BasePageReq 限制每页最多 1000 条
const EXPORT_PAGE_SIZE = 1000

// 组装查询条件（列表与导出共用），分页参数不在这里给：列表由 useTable 注入，导出自己按页累加
function buildQueryParams(): UserQueryParams {
  const queryParams: UserQueryParams = {}

  if (queryForm.name) queryParams.name = queryForm.name
  if (queryForm.email) queryParams.email = queryForm.email
  if (queryForm.tel) queryParams.tel = queryForm.tel
  if (queryForm.authRoleId !== undefined && queryForm.authRoleId !== null) {
    queryParams.authRoleId = queryForm.authRoleId
  }

  return queryParams
}

async function loadAuthRoleOptions() {
  // 只是筛选项加载失败，不打断页面；传输层错误已由 request.ts 拦截器弹过提示
  const page = await executeAuthRoles(() => queryAuthRoles({ pageNo: 1, pageSize: 100 }), {
    showError: false,
  })
  if (page?.list) {
    authRoleOptions.value = page.list
  }
}

// 导出用户数据（按页累加，单页不能超过后端上限）
async function handleExport() {
  loadingStore.showLoading(t('common.exporting'))
  try {
    const allData: User[] = []
    let pageNo = 1
    let total = 0

    do {
      const res = await queryUsers({ ...buildQueryParams(), pageNo, pageSize: EXPORT_PAGE_SIZE })
      if (res.code !== 200 || !res.data?.list) {
        break
      }

      allData.push(...res.data.list)
      total = res.data.total ?? allData.length

      if (res.data.list.length < EXPORT_PAGE_SIZE) {
        break
      }
      pageNo += 1
    } while (allData.length < total)

    if (allData.length === 0) {
      message.warning(t('export.noData'))
      return
    }
    
    // 导出为 CSV 格式
    await exportToCSV(allData, {
      filename: `users_${dayjs().format('YYYY-MM-DD_HH-mm-ss')}`,
      showLoading: false,
      columns: [
        { key: 'name', title: t('common.name') },
        { key: 'email', title: t('user.email') },
        { key: 'tel', title: t('user.phone') },
        { key: 'totalDevice', title: t('user.deviceCount') },
        { key: 'aliveNumber', title: t('user.onlineDeviceCount') },
        { key: 'totalMessage', title: t('user.messageCount') },
        { 
          key: 'state', 
          title: t('common.status'),
          format: (val) => val === '1' ? t('user.normal') : t('user.disabled')
        },
        { 
          key: 'isAdmin', 
          title: t('user.accountType'),
          format: (val) => val === '1' ? t('user.admin') : t('user.normalUser')
        },
        { key: 'authRoleName', title: t('user.authRole'), format: (_, record) => userAuthRoleName(record) },
        { key: 'loginTime', title: t('user.lastLoginTime') },
        { key: 'loginIp', title: t('user.lastLoginIp') },
      ]
    })
    message.success(t('common.exportSuccess'))
  } catch (error) {
    console.error('导出失败:', error)
    message.error(t('common.exportFailed'))
  } finally {
    loadingStore.hideLoading()
  }
}

// 获取头像URL
function getAvatar(avatar?: string) {
  return getAvatarUrl(avatar)
}

loadAuthRoleOptions()
fetchData()
</script>

<template>
  <div class="user-view">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col
            v-for="filter in queryFilters"
            :key="filter.key"
            :xl="6"
            :lg="12"
            :xs="24"
          >
            <a-form-item :label="filter.label">
              <a-input
                v-model:value="queryForm[filter.key]"
                :placeholder="filter.placeholder"
                allow-clear
                @input="debouncedSearch"
              />
            </a-form-item>
          </a-col>
          <a-col :xl="6" :lg="12" :xs="24">
            <a-form-item :label="t('user.authRole')">
              <a-select
                v-model:value="queryForm.authRoleId"
                :placeholder="t('user.authRole')"
                allow-clear
                @change="fetchData"
              >
                <a-select-option
                  v-for="authRole in authRoleOptions"
                  :key="authRole.authRoleId"
                  :value="authRole.authRoleId"
                >
                  {{ authRoleDisplayName(authRole, t) }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 数据表格 -->
    <a-card :title="t('router.title.user')" :bordered="false">
      <template #extra>
        <a-space wrap>
          <a-button v-permission="'system:user:api:create'" type="primary" @click="openEditor()">{{ t('robotai.createUser') }}</a-button>
          <a-button v-permission="'system:user:export'" @click="handleExport" :loading="exporting">{{ t('common.export') }}</a-button>
        </a-space>
      </template>
      
      <a-table
        class="ellipsis-table"
        row-key="userId"
        :columns="columns"
        :data-source="data"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1800 }"
        size="middle"
        @change="onTableChange"
      >
        <template #emptyText>
          <TableEmptyState :error="loadError" @retry="retryLoad" />
        </template>

        <!-- 头像列 -->
        <template #bodyCell="{ column, record }">
          <!-- 姓名列 -->
          <template v-if="column.dataIndex === 'name'">
            <a-tooltip :title="record.name" placement="top">
              <span class="ellipsis-text">{{ record.name }}</span>
            </a-tooltip>
          </template>

          <!-- 头像列 -->
          <template v-else-if="column.dataIndex === 'avatar'">
            <a-avatar :src="getAvatar(record.avatar)" />
          </template>
          
          <!-- 邮箱列 -->
          <template v-else-if="column.dataIndex === 'email'">
            <a-tooltip :title="record.email" placement="top">
              <span class="ellipsis-text">{{ record.email || '-' }}</span>
            </a-tooltip>
          </template>
          
          <!-- 电话列 -->
          <template v-else-if="column.dataIndex === 'tel'">
            <a-tooltip :title="record.tel" placement="top">
              <span class="ellipsis-text">{{ record.tel || '-' }}</span>
            </a-tooltip>
          </template>
          
          <!-- 登录IP列 -->
          <template v-else-if="column.dataIndex === 'loginIp'">
            <a-tooltip :title="record.loginIp" placement="topRight">
              <span class="ellipsis-text">{{ record.loginIp || '-' }}</span>
            </a-tooltip>
          </template>
          
          <!-- 状态列 -->
          <template v-else-if="column.dataIndex === 'state'">
            <a-tag v-if="record.state === '1'" color="green">{{ t('user.normal') }}</a-tag>
            <a-tag v-else color="red">{{ t('user.disabled') }}</a-tag>
          </template>
          
          <!-- 账户类型列 -->
          <template v-else-if="column.dataIndex === 'isAdmin'">
            <a-tag v-if="record.isAdmin === '1'" color="blue">{{ t('user.admin') }}</a-tag>
            <a-tag v-else>{{ t('user.normalUser') }}</a-tag>
          </template>

          <template v-else-if="column.dataIndex === 'authRoleName'">
            <span>{{ userAuthRoleName(record) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'operation'">
            <a-space :size="4" wrap>
              <a-button v-permission="'system:user:api:manage'" type="link" size="small" @click="openEditor(record)">{{ t('common.edit') }}</a-button>
              <a-button v-permission="'system:user:devices:api:list'" type="link" size="small" @click="openDevices(record)">{{ t('robotai.manageUserDevices') }}</a-button>
              <a-button v-permission="'system:user:api:update'" type="link" size="small" :danger="record.state === '1'"
                :disabled="record.userId === userStore.userInfo?.userId || changingState !== undefined"
                :loading="changingState === record.userId" @click="changeState(record)">
                {{ record.state === '1' ? t('common.disable') : t('common.enable') }}
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <AdminUserDialog :visible="editorVisible" :current="editingUser" :roles="authRoleOptions"
      @close="editorVisible = false" @saved="fetchData()" />
    <UserDevicesDialog :visible="devicesVisible" :user="selectedUser"
      @close="devicesVisible = false" @changed="fetchData()" />

    <!-- 回到顶部 -->
    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.user-view {
  padding: 24px;
}
</style>
