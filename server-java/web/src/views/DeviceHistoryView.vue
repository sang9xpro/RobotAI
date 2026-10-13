<script setup lang="ts">
import { computed, onMounted, onScopeDispose, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { TableColumnsType } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import { useUserStore } from '@/store/user'
import { useTable } from '@/composables/useTable'
import { queryDeviceHistory, queryHistoryDevices, type DeviceHistoryQuery } from '@/services/deviceHistory'
import type { AdminRobot } from '@/types/user'
import type { Message } from '@/types/message'
import TableEmptyState from '@/components/TableEmptyState.vue'
import AudioPlayer from '@/components/AudioPlayer.vue'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin || userStore.authRole?.roleKey === 'admin')
const filters = reactive({ deviceId: '', keyword: '', sender: undefined as string | undefined,
  sessionId: '', source: undefined as string | undefined, order: 'desc' as 'asc' | 'desc' })
const timeRange = ref<[Dayjs, Dayjs] | null>(null)
const detail = ref<Message | null>(null)
function query(): DeviceHistoryQuery {
  return { deviceId: filters.deviceId || undefined, keyword: filters.keyword.trim() || undefined,
    sender: filters.sender, sessionId: filters.sessionId.trim() || undefined, source: filters.source, order: filters.order,
    startTime: timeRange.value?.[0].startOf('day').format('YYYY-MM-DD HH:mm:ss'),
    endTime: timeRange.value?.[1].endOf('day').format('YYYY-MM-DD HH:mm:ss') }
}
const { data, loading, pagination, loadError, retryLoad, fetchData, resetPagination, onTableChange } =
  useTable<Message, DeviceHistoryQuery>(queryDeviceHistory, query)
pagination.pageSize = 20
pagination.pageSizeOptions = ['10', '20', '50', '100']
function search() { detail.value = null; resetPagination(); void fetchData() }
function reset() {
  Object.assign(filters, { deviceId: '', keyword: '', sender: undefined, sessionId: '', source: undefined, order: 'desc' })
  timeRange.value = null
  search()
}
function viewSession(record: Message) {
  Object.assign(filters, { deviceId: record.deviceId, sessionId: record.sessionId || '',
    keyword: '', sender: undefined, source: undefined, order: 'asc' })
  timeRange.value = null
  search()
}
const senderOptions = computed(() => ['user', 'assistant', 'tool'].map(value => ({ value, label: t(`robotai.historySender_${value}`) })))
const sourceOptions = computed(() => ['device', 'web'].map(value => ({ value, label: t(`robotai.historySource_${value}`) })))
const orderOptions = computed(() => [{ value: 'desc', label: t('robotai.historyNewest') }, { value: 'asc', label: t('robotai.historyOldest') }])
const columns = computed<TableColumnsType>(() => [
  { title: t('message.conversationTime'), dataIndex: 'createTime', width: 170 },
  { title: t('device.deviceName'), dataIndex: 'deviceName', width: 190 },
  ...(isAdmin.value ? [{ title: t('robotai.historyOwner'), dataIndex: 'ownerName', width: 160 }] : []),
  { title: t('message.messageSender'), dataIndex: 'sender', width: 120 },
  { title: t('message.messageContent'), dataIndex: 'message', width: 380 },
  { title: t('robotai.historySession'), dataIndex: 'sessionId', width: 170 },
  { title: t('table.action'), dataIndex: 'operation', fixed: 'right', width: 105 },
])

const devices = ref<AdminRobot[]>([])
const deviceLoading = ref(false)
const deviceError = ref(false)
const deviceTotal = ref(0)
const selectedDevices = new Map<string, AdminRobot>()
let devicePage = 1
let deviceSearch = ''
let deviceTicket = 0
let deviceTimer: ReturnType<typeof setTimeout> | undefined
async function loadDevices(append = false) {
  clearTimeout(deviceTimer)
  const seq = ++deviceTicket
  const nextPage = append ? devicePage + 1 : 1
  deviceLoading.value = true; deviceError.value = false
  try {
    const result = await queryHistoryDevices({ pageNo: nextPage, pageSize: 50, search: deviceSearch || undefined })
    if (seq !== deviceTicket) return
    if (result.code !== 200 || !result.data) { deviceError.value = true; return }
    devicePage = nextPage
    devices.value = append ? [...devices.value, ...result.data.list] : result.data.list
    result.data.list.forEach(device => selectedDevices.set(device.deviceId, device))
    deviceTotal.value = result.data.total
  } catch { if (seq === deviceTicket) deviceError.value = true }
  finally { if (seq === deviceTicket) deviceLoading.value = false }
}
function searchDevices(value: string) {
  deviceSearch = value.trim(); ++deviceTicket; clearTimeout(deviceTimer)
  deviceTimer = setTimeout(() => { void loadDevices() }, 300)
}
const deviceOptions = computed(() => {
  const options = new Map(devices.value.map(device => [device.deviceId, device]))
  const selected = selectedDevices.get(filters.deviceId)
  if (selected) options.set(selected.deviceId, selected)
  const list = [...options.values()].map(device => ({ value: device.deviceId,
    label: `${device.deviceId.startsWith('user_chat_') ? t('robotai.accountVirtualDevice') : device.deviceName || device.deviceId} · ${device.deviceId}${isAdmin.value ? ` · ${device.ownerName || device.username || '-'}` : ''}` }))
  if (filters.deviceId && !options.has(filters.deviceId)) list.unshift({ value: filters.deviceId, label: filters.deviceId })
  return list
})
watch(() => route.query.deviceId, value => {
  filters.deviceId = typeof value === 'string' ? value : ''
  filters.sessionId = typeof route.query.sessionId === 'string' ? route.query.sessionId : ''
  search()
}, { immediate: true })
onMounted(() => { void loadDevices() })
onScopeDispose(() => { ++deviceTicket; clearTimeout(deviceTimer) })
</script>

<template>
  <div class="history-view">
    <a-card :bordered="false" :title="t('robotai.deviceHistory')">
      <a-alert type="info" show-icon :message="t(isAdmin ? 'robotai.historyAdminScope' : 'robotai.historyUserScope')" class="scope-hint" />
      <a-form layout="vertical" @finish="search">
        <a-row :gutter="16">
          <a-col :xs="24" :lg="12">
            <a-form-item :label="t('device.deviceName')">
              <a-select v-model:value="filters.deviceId" allow-clear show-search :filter-option="false"
                :placeholder="t('robotai.historyAllDevices')" :options="deviceOptions" :loading="deviceLoading" @search="searchDevices" />
              <a-space size="small">
                <a-button type="link" size="small" :loading="deviceLoading" @click="loadDevices()">{{ t('robotai.refresh') }}</a-button>
                <a-button v-if="devices.length < deviceTotal" type="link" size="small" :disabled="deviceLoading" @click="loadDevices(true)">{{ t('robotai.historyMoreDevices') }}</a-button>
              </a-space>
              <a-alert v-if="deviceError" type="error" :message="t('common.loadDataFailed')" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :lg="12"><a-form-item :label="t('robotai.historyTimeRange')">
            <a-range-picker v-model:value="timeRange" style="width:100%" />
          </a-form-item></a-col>
          <a-col :xs="24" :md="12" :xl="6"><a-form-item :label="t('robotai.historyKeyword')">
            <a-input v-model:value="filters.keyword" allow-clear :maxlength="200" @press-enter="search" />
          </a-form-item></a-col>
          <a-col :xs="24" :md="12" :xl="6"><a-form-item :label="t('message.messageSender')">
            <a-select v-model:value="filters.sender" allow-clear :placeholder="t('common.all')" :options="senderOptions" />
          </a-form-item></a-col>
          <a-col :xs="24" :md="12" :xl="6"><a-form-item :label="t('robotai.historySession')">
            <a-input v-model:value="filters.sessionId" allow-clear :maxlength="100" @press-enter="search" />
          </a-form-item></a-col>
          <a-col :xs="24" :md="12" :xl="6"><a-form-item :label="t('robotai.historySource')">
            <a-select v-model:value="filters.source" allow-clear :placeholder="t('common.all')" :options="sourceOptions" />
          </a-form-item></a-col>
        </a-row>
        <div class="filter-actions">
          <a-space><a-button type="primary" :loading="loading" @click="search">{{ t('robotai.historySearch') }}</a-button>
            <a-button @click="reset">{{ t('robotai.historyReset') }}</a-button>
            <a-button :loading="loading" @click="fetchData">{{ t('robotai.refresh') }}</a-button>
          </a-space>
          <a-select v-model:value="filters.order" :options="orderOptions" style="min-width:165px" @change="search" />
        </div>
      </a-form>
    </a-card>
    <a-card :bordered="false" class="records-card">
      <a-table row-key="messageId" :columns="columns" :data-source="data" :loading="loading"
        :pagination="pagination" :scroll="{ x: isAdmin ? 1395 : 1235 }" @change="onTableChange">
        <template #emptyText><TableEmptyState :error="loadError" @retry="retryLoad" /></template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'deviceName'">
            <div>{{ record.deviceId?.startsWith('user_chat_') ? t('robotai.accountVirtualDevice') : record.deviceName || t('robotai.historyMissingDevice') }}</div>
            <small>{{ record.deviceId }}</small>
          </template>
          <template v-else-if="column.dataIndex === 'ownerName'">{{ record.ownerName || record.username || '-' }}<div><small>{{ record.username }}</small></div></template>
          <template v-else-if="column.dataIndex === 'sender'"><a-tag :color="record.sender === 'user' ? 'blue' : record.sender === 'assistant' ? 'green' : 'orange'">{{ t(`robotai.historySender_${record.sender}`) }}</a-tag></template>
          <template v-else-if="column.dataIndex === 'message'">
            <div class="message-preview">{{ record.message || (record.toolCalls ? t('message.toolCalls') : '-') }}</div>
            <small>{{ record.roleName }}<span v-if="record.audioPath"> · {{ t('robotai.historyHasAudio') }}</span></small>
          </template>
          <template v-else-if="column.dataIndex === 'sessionId'">
            <a-button v-if="record.sessionId" type="link" size="small" @click="viewSession(record)">{{ t('robotai.historyViewSession') }}</a-button>
            <div class="session-id">{{ record.sessionId || '-' }}</div>
          </template>
          <template v-else-if="column.dataIndex === 'operation'"><a-button type="link" @click="detail = record">{{ t('robotai.historyDetail') }}</a-button></template>
        </template>
      </a-table>
    </a-card>
    <a-modal :open="!!detail" :title="t('robotai.historyDetail')" :footer="null" width="var(--modal-width-wide)" destroy-on-close @cancel="detail = null">
      <template v-if="detail">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item :label="t('device.deviceId')">{{ detail.deviceId }}</a-descriptions-item>
          <a-descriptions-item v-if="isAdmin" :label="t('robotai.historyOwner')">{{ detail.ownerName || detail.username || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('message.conversationTime')">{{ detail.createTime }}</a-descriptions-item>
          <a-descriptions-item :label="t('message.messageSender')">{{ t(`robotai.historySender_${detail.sender}`) }}</a-descriptions-item>
          <a-descriptions-item :label="t('role.roleName')">{{ detail.roleName || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('robotai.historySession')">{{ detail.sessionId || '-' }}</a-descriptions-item>
          <a-descriptions-item :label="t('robotai.historySource')">{{ detail.source ? t(`robotai.historySource_${detail.source}`) : '-' }}</a-descriptions-item>
        </a-descriptions>
        <h4>{{ t('message.messageContent') }}</h4><pre class="detail-text">{{ detail.message || '-' }}</pre>
        <template v-if="detail.toolCalls"><h4>{{ t('message.toolCalls') }}</h4><pre class="detail-text">{{ detail.toolCalls }}</pre></template>
        <h4>{{ t('message.voice') }}</h4>
        <AudioPlayer v-if="detail.audioPath" :key="detail.messageId" :audio-url="detail.audioPath" />
        <div v-else>{{ t('robotai.historyNoAudio') }}</div>
        <div class="detail-footer"><a-button @click="detail = null">{{ t('robotai.closeDialog') }}</a-button></div>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.scope-hint { margin-bottom: 16px; }
.records-card { margin-top: 16px; }
.filter-actions { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.message-preview, .detail-text { white-space: pre-wrap; overflow-wrap: anywhere; }
.message-preview { max-height: 120px; overflow: hidden; }
.session-id { font-size: 12px; overflow-wrap: anywhere; }
small { color: var(--ant-color-text-secondary); overflow-wrap: anywhere; }
.detail-text { max-height: 360px; overflow: auto; font-family: inherit; }
.detail-footer { text-align: right; margin-top: 16px; }
</style>
