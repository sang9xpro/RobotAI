<script setup lang="ts">
import { computed, ref, watch, onScopeDispose } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'ant-design-vue'
import { useRequest } from '@/composables/useRequest'
import { useConfirm } from '@/composables/useConfirm'
import { useUserStore } from '@/store/user'
import { getAdminUser, createAdminUser, updateAdminUser } from '@/services/user'
import type { User, AdminUserInput, RobotAssignment } from '@/types/user'
import type { AuthRole } from '@/types/authRole'
import { authRoleDisplayName } from '@/utils/accessControlLabels'
import RobotDevicePicker from './RobotDevicePicker.vue'

const props = defineProps<{ visible: boolean; current: User | null; roles: AuthRole[] }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { t } = useI18n()
const userStore = useUserStore()
const { confirmAsync } = useConfirm()
const { loading, executeOk } = useRequest()
const { execute } = useRequest()
const formRef = ref<FormInstance>()
const detailsLoading = ref(false)
const detailsError = ref(false)
const form = ref<AdminUserInput & { devices: RobotAssignment[] }>({ devices: [] })
let ticket = 0
const roleOptions = computed(() => props.roles.filter(role => role.status !== '0').map(role => ({
  value: role.authRoleId, label: authRoleDisplayName(role, t),
})))
const protectedRole = computed(() => props.current?.userId === userStore.userInfo?.userId || props.current?.isAdmin === '1')

async function loadDetails() {
  const seq = ++ticket
  const userId = props.current?.userId
  detailsError.value = false
  form.value = { username: '', password: '', name: '', email: '', tel: '', authRoleId: 2, devices: [] }
  if (userId === undefined) return
  detailsLoading.value = true
  const details = await execute(() => getAdminUser(userId), { showError: false })
  if (seq !== ticket) return
  detailsLoading.value = false
  if (!details) { detailsError.value = true; return }
  form.value = { username: details.username, name: details.name, email: details.email || '',
    tel: details.tel || '', authRoleId: details.authRoleId, devices: [] }
}
watch(() => [props.visible, props.current?.userId], () => {
  ++ticket
  detailsLoading.value = false
  if (props.visible) void loadDetails()
  else form.value = { devices: [] }
}, { immediate: true })
onScopeDispose(() => { ++ticket })

async function submit() {
  if (detailsLoading.value || detailsError.value) return
  try { await formRef.value?.validate() } catch { return }
  const seq = ticket
  const userId = props.current?.userId
  const payload: AdminUserInput = userId === undefined
    ? { ...form.value, email: form.value.email?.trim() || undefined, tel: form.value.tel?.trim() || undefined }
    : { name: form.value.name?.trim(), email: form.value.email?.trim(), tel: form.value.tel?.trim(), authRoleId: form.value.authRoleId }
  if (payload.devices?.some(device => device.expectedUserId !== null)) {
    if (!await confirmAsync({ title: t('robotai.confirmAssignRobots'), content: t('robotai.transferRobotsHint') })) return
    if (seq !== ticket) return
  }
  await executeOk(() => userId === undefined ? createAdminUser(payload) : updateAdminUser(userId, payload), {
    showSuccess: true, successText: t('common.saveSuccess'),
    onSuccess: () => { if (seq === ticket) { emit('saved'); emit('close') } },
  })
}
</script>

<template>
  <a-modal :open="visible" :title="current ? t('robotai.editUser') : t('robotai.createUser')"
    width="var(--modal-width-wide)" :mask-closable="false" :closable="!loading" :confirm-loading="loading"
    :ok-button-props="{ disabled: detailsLoading || detailsError }" :cancel-button-props="{ disabled: loading }"
    :ok-text="t('common.save')" :cancel-text="t('common.cancel')" destroy-on-close
    @ok="submit" @cancel="emit('close')">
    <a-spin :spinning="detailsLoading">
      <a-alert v-if="detailsError" type="error" show-icon :message="t('common.loadDataFailed')">
        <template #action><a-button @click="loadDetails()">{{ t('robotai.retry') }}</a-button></template>
      </a-alert>
      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item :label="t('user.username')" name="username"
          :rules="current ? [] : [{ required: true, pattern: /^[A-Za-z0-9_.-]{3,20}$/, message: t('robotai.usernameRule') }]">
          <a-input v-model:value="form.username" :disabled="!!current" autocomplete="off" :maxlength="20" />
        </a-form-item>
        <a-form-item v-if="!current" :label="t('robotai.initialPassword')" name="password"
          :rules="[{ required: true, min: 6, max: 20, message: t('robotai.passwordRule') }]">
          <a-input-password v-model:value="form.password" autocomplete="new-password" :maxlength="20" />
        </a-form-item>
        <a-form-item :label="t('common.name')" name="name"><a-input v-model:value="form.name" :maxlength="50" /></a-form-item>
        <a-form-item :label="t('user.email')" name="email" :rules="[{ type: 'email', message: t('robotai.emailRule') }]">
          <a-input v-model:value="form.email" :maxlength="100" />
        </a-form-item>
        <a-form-item :label="t('user.phone')" name="tel"
          :rules="[{ pattern: /^(\+?[0-9]{8,20})?$/, message: t('robotai.phoneRule') }]">
          <a-input v-model:value="form.tel" :maxlength="21" />
        </a-form-item>
        <a-form-item :label="t('user.authRole')" name="authRoleId" :rules="[{ required: true }]">
          <a-select v-model:value="form.authRoleId" :options="roleOptions" :disabled="protectedRole" />
          <template #extra>{{ t('robotai.userRoleHint') }}</template>
        </a-form-item>
        <a-form-item v-if="!current && userStore.hasPermission('system:user:devices:api:bind')"
          :label="t('robotai.assignRobots')" name="devices"
          :rules="[{ type: 'array', max: 20, message: t('robotai.robotAssignmentLimit') }]">
          <RobotDevicePicker v-model="form.devices" />
        </a-form-item>
        <a-alert v-if="!current" type="info" show-icon :message="t('robotai.newUserDeviceHint')" />
      </a-form>
    </a-spin>
  </a-modal>
</template>
