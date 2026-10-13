<script setup lang="ts">
import { ref, computed, onMounted, onScopeDispose } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { useRequest } from '@/composables/useRequest'
import { getMusicCatalog, saveMusicCatalog, uploadMusic, deleteMusicFile, getMusicAudio, isYouTubeUrl } from '@/services/musicLibrary'
import { shouldIgnoreRequestError } from '@/services/request'
import type { MusicCatalog, MusicTrack } from '@/types/robotAdmin'
const { t } = useI18n()
const user = useUserStore()
const canEdit = computed(() => user.hasPermission('system:robot:music:update'))
const { execute, loading } = useRequest()
const catalog = ref<MusicCatalog>()
const error = ref(false)
const savedTracks = ref('')
const dirty = computed(() => !!catalog.value && JSON.stringify(catalog.value.tracks) !== savedTracks.value)
function applyCatalog(data: MusicCatalog) { catalog.value = data; savedTracks.value = JSON.stringify(data.tracks) }
const source = ref('')
const previewing = ref('')
const audioLoading = ref(false)
let ticket = 0, audioTicket = 0
function stop() { audioTicket++; if (source.value.startsWith('blob:')) URL.revokeObjectURL(source.value); source.value = ''; previewing.value = ''; audioLoading.value = false }
onScopeDispose(() => { ticket++; stop() })
const columns = computed(() => [
  { title: t('robotai.title'), dataIndex: 'title', width: 240 }, { title: t('robotai.artist'), dataIndex: 'artist', width: 200 },
  { title: t('robotai.source'), dataIndex: 'source', width: 300 }, { title: t('table.action'), dataIndex: 'action', width: 180 },
])
async function load() {
  const seq = ++ticket
  error.value = false
  const data = await execute(getMusicCatalog, { onError: () => { if (seq === ticket) error.value = true } })
  if (data && seq === ticket) { applyCatalog(data); stop() }
}
async function save() {
  if (!catalog.value) return
  const seq = ++ticket
  const data = await execute(() => saveMusicCatalog({ revision: catalog.value!.revision, tracks: catalog.value!.tracks.map(track => ({ ...track })) }), { showSuccess: true, successText: t('common.saveSuccess') })
  if (data && seq === ticket) applyCatalog(data)
}
function add() {
  catalog.value?.tracks.push({ id: crypto.randomUUID(), title: '', artist: '', fileName: null, url: '' })
}
function discard() { Modal.confirm({ title: t('robotai.discardHint'), onOk: load }) }
async function upload(file: File) {
  if (dirty.value) { message.warning(t('robotai.draftHint')); return false }
  if (!canEdit.value || !file.name.toLowerCase().endsWith('.mp3') || file.size > 30000000 || !file.size) { message.error(t('robotai.uploadHint')); return false }
  const seq = ++ticket
  const data = await execute(() => uploadMusic(file), { showSuccess: true, successText: t('common.saveSuccess') })
  if (data && seq === ticket) applyCatalog(data)
  return false
}
function remove(track: MusicTrack) {
  if (!catalog.value) return
  if (track.fileName && dirty.value) { message.warning(t('robotai.draftHint')); return }
  Modal.confirm({ title: t('robotai.deleteHint'), async onOk() {
    if (track.fileName) {
      if (dirty.value) { message.warning(t('robotai.draftHint')); return }
      const seq = ++ticket
      const data = await execute(() => deleteMusicFile(track.fileName!, catalog.value!.revision))
      if (data && seq === ticket) { applyCatalog(data); stop() }
    } else { catalog.value!.tracks = catalog.value!.tracks.filter(row => row.id !== track.id) }
  } })
}
async function preview(track: MusicTrack) {
  stop(); const seq = ++audioTicket; audioLoading.value = true; previewing.value = track.id
  try {
    const value = track.fileName ? URL.createObjectURL(await getMusicAudio(track.fileName)) : track.url || ''
    if (seq !== audioTicket) { if (value.startsWith('blob:')) URL.revokeObjectURL(value); return }
    source.value = value; previewing.value = track.id
  } catch (err) { if (!shouldIgnoreRequestError(err)) message.error(t('common.loadFailed')) }
  finally { if (seq === audioTicket) audioLoading.value = false }
}
onMounted(load)
</script>
<template>
  <div class="robot-admin-page"><a-card :title="t('robotai.music')">
    <template #extra><a-button :loading="loading" :disabled="dirty" @click="load">{{ t('robotai.refresh') }}</a-button></template>
    <a-alert type="info" show-icon :message="t('robotai.musicHint')" style="margin-bottom:16px" />
    <a-alert v-if="error" type="error" :message="t('common.loadFailed')" style="margin-bottom:16px" />
    <a-alert v-if="dirty" type="warning" :message="t('robotai.draftHint')" style="margin-bottom:16px"><template #action><a-button :disabled="loading" @click="discard">{{ t('robotai.discard') }}</a-button></template></a-alert>
    <a-space v-if="canEdit" wrap style="margin-bottom:16px">
      <a-upload accept=".mp3,audio/mpeg" :show-upload-list="false" :before-upload="upload" :disabled="loading || dirty"><a-button :disabled="loading || dirty">{{ t('robotai.upload') }}</a-button></a-upload>
      <a-button :disabled="!catalog || loading" @click="add">{{ t('robotai.addLink') }}</a-button>
      <a-button type="primary" :loading="loading" :disabled="!catalog || !dirty" @click="save">{{ t('robotai.saveCatalog') }}</a-button>
    </a-space>
    <div v-if="source" style="margin-bottom:16px"><audio :src="source" controls autoplay /><a-button @click="stop">{{ t('robotai.stop') }}</a-button></div>
    <a-table :data-source="catalog?.tracks || []" :columns="columns" :loading="loading" row-key="id" :scroll="{ x: 900 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'title'"><a-input v-if="canEdit" v-model:value="record.title" :maxlength="300" :disabled="loading" :aria-label="t('robotai.title')" /><span v-else>{{ record.title }}</span></template>
        <template v-else-if="column.dataIndex === 'artist'"><a-input v-if="canEdit" v-model:value="record.artist" :maxlength="300" :disabled="loading" :aria-label="t('robotai.artist')" /><span v-else>{{ record.artist }}</span></template>
        <template v-else-if="column.dataIndex === 'source'"><span v-if="record.fileName">{{ t('robotai.file') }} · {{ record.fileName }}</span><a-input v-else-if="canEdit" v-model:value="record.url" :maxlength="4096" :placeholder="t('robotai.url')" :disabled="loading" /><span v-else>{{ record.url }}</span></template>
        <template v-else-if="column.dataIndex === 'action'"><a-space>
          <a v-if="isYouTubeUrl(record.url)" :href="record.url" target="_blank" rel="noopener noreferrer">{{ t('robotai.openLink') }}</a>
          <a-button v-else size="small" :loading="audioLoading && previewing === record.id" :disabled="loading || !record.title" @click="preview(record)">{{ t('robotai.preview') }}</a-button>
          <a-button v-if="canEdit" size="small" danger :disabled="loading || (dirty && !!record.fileName)" @click="remove(record)">{{ t('common.delete') }}</a-button>
        </a-space></template>
      </template>
    </a-table>
  </a-card></div>
</template>
<style scoped>.robot-admin-page{padding:24px}audio{max-width:100%}@media(max-width:600px){.robot-admin-page{padding:12px}}</style>
