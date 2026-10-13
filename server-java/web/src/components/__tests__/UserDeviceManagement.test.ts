import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import type { RobotAssignment } from '@/types/user'

const api = vi.hoisted(() => ({
  queryAdminRobots: vi.fn(), queryUserDevices: vi.fn(), assignUserDevices: vi.fn(),
  getAdminUser: vi.fn(), createAdminUser: vi.fn(), updateAdminUser: vi.fn(), confirmAsync: vi.fn(),
}))
vi.mock('@/services/user', () => api)
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirmAsync: api.confirmAsync }) }))
import RobotDevicePicker from '../RobotDevicePicker.vue'
import UserDevicesDialog from '../UserDevicesDialog.vue'
import AdminUserDialog from '../AdminUserDialog.vue'

const global = {
  directives: { permission: {} },
  stubs: {
    AModal: defineComponent({ template: '<div><slot /></div>' }),
    AForm: defineComponent({ setup: (_, { expose }) => { expose({ validate: async () => {} }) }, template: '<div><slot /></div>' }),
    ASpin: defineComponent({ template: '<div><slot /></div>' }),
    AFormItem: defineComponent({ template: '<div><slot /></div>' }),
    ASelect: defineComponent({ props: ['value', 'options'], emits: ['update:value'], template: '<div />' }),
  },
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(r => { resolve = r })
  return { promise, resolve }
}
const page = (list: unknown[]) => ({ code: 200, data: { list, total: list.length } })

describe('user robot administration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    api.queryUserDevices.mockResolvedValue(page([]))
    api.queryAdminRobots.mockResolvedValue(page([]))
    api.assignUserDevices.mockResolvedValue({ code: 200, data: 2 })
    api.confirmAsync.mockResolvedValue(true)
  })

  it('submits the displayed owner with each selected robot and prevents selecting robots already owned by the target', async () => {
    api.queryAdminRobots.mockResolvedValue(page([
      { deviceId: '02:93:00:00:00:01', userId: 7, deviceName: 'Robot A' },
      { deviceId: '02:93:00:00:00:02', userId: 9, deviceName: 'Robot B' },
    ]))
    const wrapper = shallowMount(RobotDevicePicker, { props: { modelValue: [], targetUserId: 9 }, global })
    await flushPromises()
    const select = wrapper.findComponent({ name: 'ASelect' })
    // The custom stub has no name; resolve it by its rendered component type.
    const control = select.exists() ? select : wrapper.findComponent(global.stubs.ASelect)
    expect(control.props('options')[1].disabled).toBe(true)
    control.vm.$emit('update:value', ['02:93:00:00:00:01'])
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual([{ deviceId: '02:93:00:00:00:01', expectedUserId: 7 }])
    wrapper.unmount()
  })

  it('discards a device list arriving after another account was opened', async () => {
    const old = deferred<ReturnType<typeof page>>()
    api.queryUserDevices.mockReturnValueOnce(old.promise).mockResolvedValueOnce(page([{ deviceId: 'new-user-robot' }]))
    const wrapper = shallowMount(UserDevicesDialog, { props: { visible: true, user: { userId: 7 } }, global })
    await wrapper.setProps({ user: { userId: 9 } })
    await flushPromises()
    old.resolve(page([{ deviceId: 'old-user-robot' }]))
    await flushPromises()
    expect((wrapper.vm as unknown as { devices: { deviceId: string }[] }).devices).toEqual([{ deviceId: 'new-user-robot' }])
    wrapper.unmount()
  })

  it('does not transfer ownership when the confirmation is cancelled', async () => {
    api.confirmAsync.mockResolvedValue(false)
    const wrapper = shallowMount(UserDevicesDialog, { props: { visible: true, user: { userId: 9 } }, global })
    const state = wrapper.vm as unknown as { selected: RobotAssignment[]; assign: () => Promise<void> }
    state.selected = [{ deviceId: '02:93:00:00:00:01', expectedUserId: 7 }]
    await state.assign()
    expect(api.confirmAsync).toHaveBeenCalledOnce()
    expect(api.assignUserDevices).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('assigns a batch to the selected account, then reloads its devices', async () => {
    const wrapper = shallowMount(UserDevicesDialog, { props: { visible: true, user: { userId: 9 } }, global })
    await flushPromises()
    const assignments = [{ deviceId: '02:93:00:00:00:01', expectedUserId: 7 }, { deviceId: '02:93:00:00:00:02', expectedUserId: null }]
    const state = wrapper.vm as unknown as { selected: RobotAssignment[]; assign: () => Promise<void> }
    state.selected = assignments
    await state.assign()
    expect(api.assignUserDevices).toHaveBeenCalledWith(9, assignments)
    expect(api.queryUserDevices).toHaveBeenLastCalledWith(9, { pageNo: 1, pageSize: 10 })
    expect(wrapper.emitted('changed')).toHaveLength(1)
    wrapper.unmount()
  })

  it('loads full profile details and edits only permitted fields', async () => {
    api.getAdminUser.mockResolvedValue({ code: 200, data: { userId: 9, username: 'member', name: 'Member', tel: '0912345678', authRoleId: 2 } })
    api.updateAdminUser.mockResolvedValue({ code: 200, data: {} })
    const wrapper = shallowMount(AdminUserDialog, { props: { visible: true, current: { userId: 9, tel: '091****678' }, roles: [] }, global })
    await flushPromises()
    const state = wrapper.vm as unknown as { form: { name: string; tel: string }; submit: () => Promise<void> }
    expect(state.form.tel).toBe('0912345678')
    state.form.name = 'Changed'
    await state.submit()
    expect(api.updateAdminUser).toHaveBeenCalledWith(9, { name: 'Changed', tel: '0912345678', email: '', authRoleId: 2 })
    expect(api.createAdminUser).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
