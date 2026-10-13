<script setup lang="ts">
import { computed, ref, watch, onScopeDispose } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from '@/composables/useRequest'
import { useConfirm } from '@/composables/useConfirm'
import { queryUserDevices, assignUserDevices } from '@/services/user'
import type { User, RobotAssignment } from '@/types/user'
import type { Device } from '@/types/device'
import RobotDevicePicker from './RobotDevicePicker.vue'

const props = defineProps<{ visible: boolean; user: User | null }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const { t } = useI18n()
const { confirmAsync } = useConfirm()
const { loading: saving, executeOk } = useRequest()
const devices = ref<Device[]>([])
const selected = ref<RobotAssignment[]>([])
const loading = ref(false)
const error = ref(false)
const page = ref(1)
const total = ref(0)
let ticket = 0
const columns = computed(() => [
  { title: t('device.deviceId'), dataIndex: 'deviceId', width: 190 },
  { title: t('device.deviceName'), dataIndex: 'deviceName', width: 150 },
  { title: t('role.roleName'), dataIndex: 'roleName', width: 150 },
  { title: t('common.status'), dataIndex: 'state', width: 100 },
])
const title = computed(() => t('robotai.userDevicesTitle', { user: props.user?.name || props.user?.username || '' }))

async function load() {
  const userId = props.user?.userId
  if (userId === undefined) return
  const seq = ++ticket
  loading.value = true
  error.value = false
  try {
    const result = await queryUserDevices(userId, { pageNo: page.value, pageSize: 10 })
    if (seq !== ticket) return
    if (result.code !== 200 || !result.data) { error.value = true; return }
    devices.value = result.data.list
    total.value = result.data.total
  } catch {
    if (seq === ticket) error.value = true
  } finally {
    if (seq === ticket) loading.value = false
  }
}

watch(() => [props.visible, props.user?.userId], () => {
  ++ticket
  selected.value = []
  devices.value = []
  page.value = 1
  total.value = 0
  loading.value = false
  if (props.visible) void load()
}, { immediate: true })
onScopeDispose(() => { ++ticket })

async function assign() {
  const userId = props.user?.userId
  if (userId === undefined || !selected.value.length || selected.value.length > 20) return
  const assignments = selected.value.map(device => ({ ...device }))
  if (assignments.some(device => device.expectedUserId !== null && device.expectedUserId !== userId)) {
    if (!await confirmAsync({ title: t('robotai.confirmAssignRobots'), content: t('robotai.transferRobotsHint') })) return
    if (!props.visible || props.user?.userId !== userId) return
  }
  await executeOk(() => assignUserDevices(userId, assignments), {
    showSuccess: true, successText: t('robotai.robotsAssigned'),
    onSuccess: async () => {
      emit('changed')
      if (props.user?.userId !== userId || !props.visible) return
      selected.value = []
      page.value = 1
      await load()
    },
  })
}
</script>

<template>
  <a-modal :open="visible" :title="title" width="var(--modal-width-wide)" :footer="null"
    :mask-closable="false" :closable="!saving" destroy-on-close @cancel="emit('close')">
    <a-alert type="info" show-icon :message="t('robotai.userDevicesHint')" style="margin-bottom: 16px" />
    <a-alert v-if="error" type="error" show-icon :message="t('common.loadDataFailed')">
      <template #action><a-button @click="load()">{{ t('robotai.retry') }}</a-button></template>
    </a-alert>
    <a-table row-key="deviceId" :columns="columns" :data-source="devices" :loading="loading" size="small"
      :scroll="{ x: 600 }" :pagination="{ current: page, pageSize: 10, total, showSizeChanger: false }"
      @change="(pagination: { current?: number }) => { page = pagination.current || 1; load() }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'deviceName'">
          {{ record.deviceId.startsWith('user_chat_') ? t('robotai.accountVirtualDevice') : record.deviceName }}
        </template>
        <template v-else-if="column.dataIndex === 'state'">
          <a-tag :color="record.state === '1' ? 'green' : record.state === '2' ? 'blue' : 'default'">
            {{ record.state === '1' ? t('device.onlineStatus') : record.state === '2' ? t('device.standbyStatus') : t('device.offlineStatus') }}
          </a-tag>
        </template>
      </template>
    </a-table>
    <div v-permission="'system:user:devices:api:bind'" v-if="user?.state !== '0'" class="assign-section">
      <a-form layout="vertical">
        <a-form-item :label="t('robotai.assignRobots')">
          <RobotDevicePicker v-model="selected" :target-user-id="user?.userId" />
        </a-form-item>
      </a-form>
      <a-button type="primary" :disabled="!selected.length || selected.length > 20" :loading="saving" @click="assign">
        {{ t('robotai.assignRobots') }}
      </a-button>
    </div>
    <a-alert v-else-if="user?.state === '0'" type="warning" :message="t('robotai.enableUserBeforeAssign')" />
    <div class="dialog-footer"><a-button :disabled="saving" @click="emit('close')">{{ t('robotai.closeDialog') }}</a-button></div>
  </a-modal>
</template>

<style scoped>
.assign-section { margin-top: 20px; }
.dialog-footer { text-align: right; margin-top: 16px; }
</style>
