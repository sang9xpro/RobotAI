import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { Modal } from 'ant-design-vue'
import type { MusicCatalog, RobotProfile, MusicTrack, SystemCheck } from '@/types/robotAdmin'
const api = vi.hoisted(() => ({ getRobotProfile: vi.fn(), selectRobotRole: vi.fn(), getMusicCatalog: vi.fn(), saveMusicCatalog: vi.fn(), uploadMusic: vi.fn(), deleteMusicFile: vi.fn(), getMusicAudio: vi.fn(), getSystemStatus: vi.fn() }))
vi.mock('@/services/robotProfile', () => ({ getRobotProfile: api.getRobotProfile, selectRobotRole: api.selectRobotRole }))
vi.mock('@/services/musicLibrary', async original => ({ ...(await original<typeof import('@/services/musicLibrary')>()), ...api }))
vi.mock('@/services/systemStatus', () => ({ getSystemStatus: api.getSystemStatus }))
import RobotProfileView from '../RobotProfileView.vue'
import MusicLibraryView from '../MusicLibraryView.vue'
import SystemStatusView from '../SystemStatusView.vue'
import { useUserStore } from '@/store/user'
const ok = <T>(data: T) => ({ code: 200, message: 'ok', data })
function state<T>(wrapper: ReturnType<typeof shallowMount>): T { return (wrapper.vm.$ as unknown as { setupState: T }).setupState }
interface ProfileState { profile?: RobotProfile; selected?: number; error: boolean; select(): Promise<void>; load(): Promise<void> }
interface MusicState { catalog?: MusicCatalog; dirty: boolean; source: string; error: boolean; load(): Promise<void>; add(): void; save(): Promise<void>; upload(file: File): Promise<boolean>; remove(track: MusicTrack): void; preview(track: MusicTrack): Promise<void>; stop(): void }
const initial: MusicCatalog = { revision: 'r1', tracks: [{ id: 'track', title: 'Ken', artist: '', fileName: 'ken.mp3', url: null }] }
beforeEach(() => {
  setActivePinia(createPinia()); vi.clearAllMocks()
  useUserStore().setPermissions([{ permissionKey: 'system:robot:music:update' }] as never)
  api.getMusicCatalog.mockResolvedValue(ok(structuredClone(initial)))
  api.getRobotProfile.mockResolvedValue(ok({ deviceId: 'user_chat_1', roleId: 1, roles: [{ roleId: 1, roleName: 'Ken' }, { roleId: 2, roleName: 'Ken 2' }] }))
})
describe('RobotAI request boundaries', () => {
  it('selects Android character without confusing it with browser chat', async () => {
    const wrapper = shallowMount(RobotProfileView); await flushPromises()
    const view = state<ProfileState>(wrapper)
    expect(view.profile?.deviceId).toBe('user_chat_1')
    api.selectRobotRole.mockResolvedValue(ok({ ...view.profile, roleId: 2 }))
    view.selected = 2; await view.select()
    expect(api.selectRobotRole).toHaveBeenCalledWith(2)
    expect(view.profile?.roleId).toBe(2); wrapper.unmount()
  })
  it('keeps a forbidden profile unloaded and ignores replies after unmount', async () => {
    api.getRobotProfile.mockResolvedValueOnce({ code: 403, data: null, message: 'Forbidden' })
    const failed = shallowMount(RobotProfileView); await flushPromises()
    expect(state<ProfileState>(failed).error).toBe(true); failed.unmount()
    let resolve!: (value: unknown) => void
    api.getRobotProfile.mockReturnValueOnce(new Promise(r => { resolve = r }))
    const wrapper = shallowMount(RobotProfileView); const view = state<ProfileState>(wrapper); wrapper.unmount()
    resolve(ok({ deviceId: 'stale', roleId: 99, roles: [] })); await flushPromises()
    expect(view.profile).toBeUndefined()
  })
  it('does not replace an edited catalog when a file is uploaded', async () => {
    const wrapper = shallowMount(MusicLibraryView); await flushPromises(); const view = state<MusicState>(wrapper)
    view.catalog!.tracks[0]!.title = 'edited'
    expect(view.dirty).toBe(true)
    await view.upload(new File(['ID3'], 'valid.mp3'))
    expect(api.uploadMusic).not.toHaveBeenCalled(); expect(view.catalog!.tracks[0]!.title).toBe('edited')
    wrapper.unmount()
  })
  it('saves a plain draft with the original revision, retains it on conflict, and clears dirty on success', async () => {
    const wrapper = shallowMount(MusicLibraryView); await flushPromises(); const view = state<MusicState>(wrapper)
    view.add(); view.catalog!.tracks[1]!.title = 'URL'; view.catalog!.tracks[1]!.url = 'https://youtu.be/abc'
    api.saveMusicCatalog.mockResolvedValueOnce({ code: 409, data: null, message: 'Reload' })
    await view.save(); expect(view.dirty).toBe(true); expect(view.catalog!.tracks).toHaveLength(2)
    expect(api.saveMusicCatalog.mock.calls[0]![0].revision).toBe('r1')
    api.saveMusicCatalog.mockResolvedValueOnce(ok({ ...view.catalog, revision: 'r2' }))
    await view.save(); expect(view.dirty).toBe(false); expect(view.catalog!.revision).toBe('r2'); wrapper.unmount()
  })
  it('uploads only bounded MP3 and deletes using the observed catalog revision', async () => {
    const wrapper = shallowMount(MusicLibraryView); await flushPromises(); const view = state<MusicState>(wrapper)
    await view.upload(new File(['bad'], 'bad.txt')); expect(api.uploadMusic).not.toHaveBeenCalled()
    api.uploadMusic.mockResolvedValueOnce(ok(initial)); await view.upload(new File(['ID3'], 'valid.mp3'))
    expect(api.uploadMusic).toHaveBeenCalledTimes(1)
    api.deleteMusicFile.mockResolvedValueOnce(ok({ revision: 'r2', tracks: [] }))
    view.remove(view.catalog!.tracks[0]!)
    const props = vi.mocked(Modal.confirm).mock.calls.at(-1)![0]
    await props.onOk?.(); expect(api.deleteMusicFile).toHaveBeenCalledWith('ken.mp3', 'r1')
    expect(view.catalog!.tracks).toHaveLength(0); wrapper.unmount()
  })
  it('revokes a stale protected audio preview instead of playing another track', async () => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() }))
    let resolve!: (value: Blob) => void
    api.getMusicAudio.mockReturnValue(new Promise<Blob>(r => { resolve = r }))
    const wrapper = shallowMount(MusicLibraryView); await flushPromises(); const view = state<MusicState>(wrapper)
    const playing = view.preview(view.catalog!.tracks[0]!); view.stop(); resolve(new Blob(['ID3']))
    await playing; expect(view.source).toBe(''); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test'); wrapper.unmount()
  })
  it('shows measured status without inventing handset or motor metrics', async () => {
    const checks: SystemCheck[] = [{ id: 'motor', state: 'unknown', checkedTime: '2026-10-12T01:00:00Z', latencyMs: null, detail: 'simulated' }]
    api.getSystemStatus.mockResolvedValue(ok(checks))
    const wrapper = shallowMount(SystemStatusView); await flushPromises()
    const view = state<{ rows: SystemCheck[] }>(wrapper)
    expect(view.rows).toEqual(checks); expect(view.rows[0]!.latencyMs).toBeNull(); wrapper.unmount()
  })
  it('retains a draft if deletion was confirmed after another edit', async () => {
    const wrapper = shallowMount(MusicLibraryView); await flushPromises(); const view = state<MusicState>(wrapper)
    view.remove(view.catalog!.tracks[0]!)
    const confirm = vi.mocked(Modal.confirm).mock.calls.at(-1)![0]
    view.catalog!.tracks[0]!.title = 'draft after opening confirmation'
    await confirm.onOk?.()
    expect(api.deleteMusicFile).not.toHaveBeenCalled(); expect(view.dirty).toBe(true); wrapper.unmount()
  })
  it('removing a URL stages metadata changes without deleting a local file', async () => {
    const wrapper = shallowMount(MusicLibraryView); await flushPromises(); const view = state<MusicState>(wrapper)
    view.add(); const link = view.catalog!.tracks[1]!; link.title = 'URL'; link.url = 'https://example.com/ken.mp3'
    await view.preview(link); expect(view.source).toBe(link.url); expect(api.getMusicAudio).not.toHaveBeenCalled()
    view.remove(link); await vi.mocked(Modal.confirm).mock.calls.at(-1)![0].onOk?.()
    expect(api.deleteMusicFile).not.toHaveBeenCalled(); expect(view.catalog!.tracks).toHaveLength(1); wrapper.unmount()
  })
  it('music failure can be retried and stale responses cannot populate an unmounted page', async () => {
    api.getMusicCatalog.mockResolvedValueOnce({ code: 403, message: 'Forbidden', data: null })
    const wrapper = shallowMount(MusicLibraryView); await flushPromises(); const view = state<MusicState>(wrapper)
    expect(view.error).toBe(true); await view.load(); expect(view.error).toBe(false); expect(view.catalog?.revision).toBe('r1')
    let resolve!: (value: unknown) => void
    api.getMusicCatalog.mockReturnValueOnce(new Promise(r => { resolve = r }))
    const refreshing = view.load(); wrapper.unmount(); resolve(ok({ revision: 'stale', tracks: [] })); await refreshing
    expect(view.catalog?.revision).toBe('r1')
  })
  it('status retries failures and ignores responses after unmount', async () => {
    api.getSystemStatus.mockResolvedValueOnce({ code: 500, message: 'offline', data: null })
    const wrapper = shallowMount(SystemStatusView); await flushPromises()
    const view = state<{ rows: SystemCheck[]; error: boolean; load(): Promise<void> }>(wrapper)
    expect(view.error).toBe(true); expect(view.rows).toEqual([])
    api.getSystemStatus.mockResolvedValueOnce(ok([])); await view.load(); expect(view.error).toBe(false)
    let resolve!: (value: unknown) => void
    api.getSystemStatus.mockReturnValueOnce(new Promise(r => { resolve = r }))
    const refresh = view.load(); wrapper.unmount(); resolve(ok([{ id: 'stale' }])); await refresh
    expect(view.rows).toEqual([])
  })
})
