<script setup lang="ts">
import { computed, ref, onMounted, onScopeDispose } from 'vue'
import { useI18n } from 'vue-i18n'
import { queryAdminRobots } from '@/services/user'
import type { AdminRobot, RobotAssignment } from '@/types/user'

const props = defineProps<{ modelValue: RobotAssignment[]; targetUserId?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: RobotAssignment[]] }>()
const { t } = useI18n()
const loading = ref(false)
const error = ref(false)
const robots = ref<AdminRobot[]>([])
const total = ref(0)
const known = new Map<string, AdminRobot>()
let page = 1
let search = ''
let ticket = 0
let timer: ReturnType<typeof setTimeout> | undefined

async function load(append = false) {
  clearTimeout(timer)
  const seq = ++ticket
  const nextPage = append ? page + 1 : 1
  loading.value = true
  error.value = false
  try {
    const res = await queryAdminRobots({ search: search || undefined, pageNo: nextPage, pageSize: 50 })
    if (seq !== ticket) return
    if (res.code !== 200 || !res.data) { error.value = true; return }
    page = nextPage
    robots.value = append ? [...robots.value, ...res.data.list] : res.data.list
    res.data.list.forEach(robot => known.set(robot.deviceId, robot))
    total.value = res.data.total
  } catch {
    if (seq === ticket) error.value = true
  } finally {
    if (seq === ticket) loading.value = false
  }
}

function onSearch(value: string) {
  search = value.trim()
  ++ticket
  clearTimeout(timer)
  timer = setTimeout(() => { void load() }, 300)
}
const options = computed(() => {
  const visible = new Map(robots.value.map(robot => [robot.deviceId, robot]))
  props.modelValue.forEach(selected => {
    const robot = known.get(selected.deviceId)
    if (robot) visible.set(robot.deviceId, robot)
  })
  return [...visible.values()].map(robot => ({ value: robot.deviceId,
    label: `${robot.deviceName || robot.deviceId} · ${robot.deviceId} · ${robot.ownerName || robot.username || t('robotai.unassignedRobot')}`,
    disabled: props.targetUserId !== undefined && robot.userId === props.targetUserId,
  }))
})
const selectedIds = computed({
  get: () => props.modelValue.map(robot => robot.deviceId),
  set: (ids: string[]) => emit('update:modelValue', ids.map(deviceId => ({
    deviceId, expectedUserId: known.get(deviceId)?.userId ?? null,
  }))),
})
onMounted(() => { void load() })
onScopeDispose(() => { ++ticket; clearTimeout(timer) })
</script>

<template>
  <a-select v-model:value="selectedIds" mode="multiple" show-search :filter-option="false"
    :options="options" :loading="loading" :placeholder="t('robotai.selectRobots')" @search="onSearch"
    style="width: 100%" :max-tag-count="3" />
  <div class="picker-status">
    <span>{{ t('robotai.robotPickerCount', { loaded: robots.length, total }) }}</span>
    <a-button type="link" size="small" :loading="loading" @click="load()">{{ t('robotai.refresh') }}</a-button>
    <a-button v-if="robots.length < total" type="link" size="small" :disabled="loading" @click="load(true)">
      {{ t('robotai.loadMoreRobots') }}
    </a-button>
  </div>
  <a-alert v-if="error" type="error" show-icon :message="t('common.loadDataFailed')" />
  <div class="picker-hint">{{ t('robotai.robotAssignmentHint') }}</div>
</template>

<style scoped>
.picker-status { display: flex; align-items: center; flex-wrap: wrap; margin-top: 4px; }
.picker-status, .picker-hint { color: var(--ant-color-text-tertiary); font-size: 12px; }
</style>
