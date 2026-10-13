<script setup lang="ts">
import { ref, onMounted, onScopeDispose } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from '@/composables/useRequest'
import { getRobotProfile, selectRobotRole } from '@/services/robotProfile'
import type { RobotProfile } from '@/types/robotAdmin'
const { t } = useI18n()
const { execute, loading } = useRequest()
const profile = ref<RobotProfile>()
const selected = ref<number>()
const error = ref(false)
let ticket = 0
onScopeDispose(() => { ticket++ })
async function load() {
  const seq = ++ticket
  error.value = false
  const data = await execute(getRobotProfile, { onError: () => { if (seq === ticket) error.value = true } })
  if (seq === ticket && data) { profile.value = data; selected.value = data.roleId ?? undefined }
}
async function select() {
  if (selected.value == null) return
  const seq = ++ticket
  const data = await execute(() => selectRobotRole(selected.value!), { showSuccess: true, successText: t('robotai.applied') })
  if (seq === ticket && data) { profile.value = data; selected.value = data.roleId ?? undefined }
}
onMounted(load)
</script>
<template>
  <div class="robot-admin-page">
    <a-card :title="t('robotai.profile')">
      <a-alert type="info" show-icon :message="t('robotai.profileHint')" />
      <a-alert v-if="error" type="error" :message="t('common.loadFailed')" style="margin-top:16px">
        <template #action><a-button @click="load">{{ t('robotai.retry') }}</a-button></template>
      </a-alert>
      <a-spin :spinning="loading">
        <a-descriptions v-if="profile" :column="1" style="margin-top:24px">
          <a-descriptions-item :label="t('robotai.virtualDevice')"><code>{{ profile.deviceId }}</code> — {{ t('robotai.virtualHint') }}</a-descriptions-item>
        </a-descriptions>
        <a-form :model="{ roleId: selected }" layout="vertical" @finish="select">
          <a-form-item :label="t('robotai.currentRole')">
            <a-select v-model:value="selected" :disabled="loading" :placeholder="t('robotai.currentRole')" :options="profile?.roles.map(role => ({ value: role.roleId, label: role.roleName }))" />
          </a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading" :disabled="selected == null || selected === profile?.roleId">{{ t('robotai.useAndroid') }}</a-button>
        </a-form>
      </a-spin>
      <a-divider />
      <p>{{ t('robotai.configHint') }}</p>
      <a-space wrap>
        <router-link to="/role">{{ t('router.title.role') }}</router-link>
        <router-link v-if="profile?.roleId" :to="{ path: '/memory/chat', query: { roleId: profile.roleId, deviceId: profile.deviceId } }">{{ t('robotai.memories') }}</router-link>
        <router-link to="/chat">{{ t('robotai.browserChat') }}</router-link>
      </a-space>
    </a-card>
  </div>
</template>
<style scoped>.robot-admin-page { padding:24px; max-width:1000px; margin:auto; } @media(max-width:600px){.robot-admin-page{padding:12px}}</style>
