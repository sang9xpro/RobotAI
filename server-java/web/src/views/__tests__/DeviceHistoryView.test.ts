import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import { useUserStore } from '@/store/user'
import type { Message } from '@/types/message'
import type { DeviceHistoryQuery } from '@/services/deviceHistory'

const api = vi.hoisted(() => ({ queryDeviceHistory: vi.fn(), queryHistoryDevices: vi.fn() }))
vi.mock('@/services/deviceHistory', () => api)
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
import DeviceHistoryView from '../DeviceHistoryView.vue'
const page = (list: unknown[]) => ({ code: 200, message: '', data: { list, total: list.length } })
const global = { stubs: {
  AModal: defineComponent({ template: '<div><slot /></div>' }),
  ADescriptions: defineComponent({ template: '<div><slot /></div>' }),
  ADescriptionsItem: defineComponent({ template: '<div><slot /></div>' }),
} }
type State = { filters: { deviceId: string; keyword: string; sessionId: string; sender?: string; source?: string; order: string };
  data: Message[]; detail: Message | null; columns: { dataIndex: string }[]; search: () => void; viewSession: (message: Message) => void }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r }); return { promise, resolve } }
async function mountView() { const wrapper = shallowMount(DeviceHistoryView, { global }); await flushPromises(); return { wrapper, state: wrapper.vm as unknown as State } }

describe('device conversation history', () => {
  beforeEach(() => {
    localStorage.clear(); setActivePinia(createPinia()); vi.resetAllMocks()
    useUserStore().setUserInfo({ userId: 7, isAdmin: '0' })
    api.queryDeviceHistory.mockResolvedValue(page([]))
    api.queryHistoryDevices.mockResolvedValue(page([]))
  })
  it('searches the selected device and content with bounded pagination', async () => {
    const { wrapper, state } = await mountView()
    Object.assign(state.filters, { deviceId: 'robot-a', keyword: ' Hanoi ', sender: 'user' })
    state.search(); await flushPromises()
    expect(api.queryDeviceHistory).toHaveBeenLastCalledWith(expect.objectContaining({ deviceId: 'robot-a', keyword: 'Hanoi', sender: 'user', pageNo: 1, pageSize: 20 }))
    wrapper.unmount()
  })
  it('opens a complete session in chronological order, removing conflicting content filters', async () => {
    const { wrapper, state } = await mountView()
    Object.assign(state.filters, { keyword: 'hidden', sender: 'tool', source: 'web' })
    state.viewSession({ messageId: 1, deviceId: 'robot-b', sessionId: 's-2', sender: 'user', message: 'Hello' })
    await flushPromises()
    const params = api.queryDeviceHistory.mock.lastCall?.[0] as DeviceHistoryQuery
    expect(params).toMatchObject({ deviceId: 'robot-b', sessionId: 's-2', order: 'asc', pageNo: 1 })
    expect(params.keyword).toBeUndefined(); expect(params.sender).toBeUndefined(); expect(params.source).toBeUndefined()
    wrapper.unmount()
  })
  it('discards a stale response after selecting another device', async () => {
    const { wrapper, state } = await mountView()
    const stale = deferred<ReturnType<typeof page>>()
    api.queryDeviceHistory.mockReturnValueOnce(stale.promise).mockResolvedValueOnce(page([{ messageId: 2, deviceId: 'robot-b' }]))
    state.filters.deviceId = 'robot-a'; state.search()
    state.filters.deviceId = 'robot-b'; state.search(); await flushPromises()
    stale.resolve(page([{ messageId: 1, deviceId: 'robot-a' }])); await flushPromises()
    expect(state.data).toEqual([{ messageId: 2, deviceId: 'robot-b' }])
    wrapper.unmount()
  })
  it('shows conversation owner only in the admin view', async () => {
    const { wrapper, state } = await mountView()
    expect(state.columns.some(c => c.dataIndex === 'ownerName')).toBe(false)
    useUserStore().setUserInfo({ userId: 1, isAdmin: '1' }); await flushPromises()
    expect(state.columns.some(c => c.dataIndex === 'ownerName')).toBe(true)
    wrapper.unmount()
  })
  it('renders message and tool payloads as escaped text', async () => {
    const { wrapper, state } = await mountView()
    state.detail = { messageId: 1, deviceId: 'robot-a', sender: 'user', message: '<img src=x onerror=alert(1)>', toolCalls: '<script>bad()</script>' }
    await flushPromises()
    expect(wrapper.find('pre.detail-text').text()).toBe('<img src=x onerror=alert(1)>')
    expect(wrapper.find('pre.detail-text img').exists()).toBe(false)
    expect(wrapper.find('pre.detail-text script').exists()).toBe(false)
    wrapper.unmount()
  })
})
