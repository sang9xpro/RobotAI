<script setup lang="ts">
import { onMounted, onScopeDispose, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from '@/composables/useRequest'
import { getSystemStatus } from '@/services/systemStatus'
import type { SystemCheck } from '@/types/robotAdmin'
const { t } = useI18n()
const { execute, loading } = useRequest()
const rows = ref<SystemCheck[]>([])
const error = ref(false)
let ticket = 0
onScopeDispose(() => { ticket++ })
const columns = computed(() => [
  { title: t('robotai.service'), dataIndex: 'id' }, { title: t('common.status'), dataIndex: 'state' },
  { title: t('robotai.checked'), dataIndex: 'checkedTime' }, { title: t('robotai.latency'), dataIndex: 'latencyMs' },
])
const colors = { healthy: 'green', degraded: 'orange', unavailable: 'red', unknown: 'default' }
async function load() {
  const seq = ++ticket
  error.value = false
  const data = await execute(getSystemStatus, { onError: () => { if (seq === ticket) error.value = true } })
  if (data && seq === ticket) rows.value = data
}
onMounted(load)
</script>
<template>
  <div class="robot-admin-page"><a-card :title="t('robotai.system')">
    <template #extra><a-button :loading="loading" @click="load">{{ t('robotai.refresh') }}</a-button></template>
    <a-alert type="info" show-icon :message="t('robotai.statusHint')" style="margin-bottom:16px" />
    <a-alert v-if="error" type="error" :message="t('common.loadFailed')" style="margin-bottom:16px" />
    <a-table :data-source="rows" :columns="columns" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 650 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'id'">{{ t(`robotai.${record.id}`) }}</template>
        <a-tag v-else-if="column.dataIndex === 'state'" :color="colors[record.state as SystemCheck['state']]">{{ t(`robotai.${record.state}`) }}</a-tag>
        <template v-else-if="column.dataIndex === 'checkedTime'">{{ new Date(record.checkedTime).toLocaleString() }}</template>
        <template v-else-if="column.dataIndex === 'latencyMs'">{{ record.latencyMs == null ? t('robotai.noMeasure') : `${record.latencyMs} ms` }}</template>
      </template>
    </a-table>
  </a-card></div>
</template>
<style scoped>.robot-admin-page{padding:24px}@media(max-width:600px){.robot-admin-page{padding:12px}}</style>
