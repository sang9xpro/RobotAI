import { shallowMount, enableAutoUnmount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ROUTES } from '@/router/routes'
import { STORAGE_SIDEBAR_WIDTH } from '@/constants/storage'

enableAutoUnmount(afterEach)

const userStoreMock = vi.hoisted(() => ({
  userInfo: null as { userId: number } | null,
  isAdmin: true,
}))

const routerMock = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('@/store/user', () => ({ useUserStore: () => userStoreMock }))
// 布局树里的 useMenu 在模块顶层就引了 useRoute，两个都要给
vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
  useRoute: () => ({ path: '/dashboard', name: 'dashboard' }),
}))

import MainLayout from '../MainLayout.vue'
import FloatingChat from '@/components/FloatingChat.vue'

// router-view 的作用域插槽会被当成元素子节点直接调用，必须给一个真实组件占位
const mountOptions = {
  global: {
    components: {
      RouterView: { template: '<div class="router-view-stub" />' },
      ALayoutSider: { name: 'TestSider', props: ['width', 'collapsed'], template: '<aside><slot /></aside>' },
    },
  },
}

describe('MainLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    userStoreMock.userInfo = { userId: 7 }
    userStoreMock.isAdmin = true
  })

  it('挂载浮动聊天入口', () => {
    const wrapper = shallowMount(MainLayout, mountOptions)

    expect(wrapper.findComponent(FloatingChat).exists()).toBe(true)
  })

  it('内容区偏移量按侧边栏宽度', () => {
    const wrapper = shallowMount(MainLayout, mountOptions)

    expect(
      (wrapper.find('.main-layout').element as HTMLElement).style.getPropertyValue('--app-content-offset'),
    ).toBe('200px')
  })

  it('无登录用户信息时跳登录页', () => {
    userStoreMock.userInfo = null

    shallowMount(MainLayout, mountOptions)

    expect(routerMock.push).toHaveBeenCalledWith(ROUTES.LOGIN)
  })

  it('dragging resizes the content offset and remembers the width on remount', async () => {
    const wrapper = shallowMount(MainLayout, mountOptions)
    const handle = wrapper.get('.sidebar-resize-handle')
    const element = handle.element as HTMLElement
    element.setPointerCapture = vi.fn()
    element.hasPointerCapture = vi.fn().mockReturnValue(true)
    element.releasePointerCapture = vi.fn()
    async function pointer(type: string, clientX: number) {
      const event = new MouseEvent(type, { bubbles: true, button: 0, clientX })
      Object.defineProperty(event, 'pointerId', { value: 1 })
      element.dispatchEvent(event)
      await nextTick()
    }
    await pointer('pointerdown', 200)
    await pointer('pointermove', 360)
    expect(wrapper.get('.main-layout').attributes('style')).toContain('--app-content-offset: 360px')
    expect(wrapper.findComponent({ name: 'ALayoutSider' }).props('width')).toBe(360)
    await pointer('pointerup', 360)
    expect(wrapper.get('.main-layout').classes()).not.toContain('is-resizing')
    expect(localStorage.getItem(STORAGE_SIDEBAR_WIDTH)).toBe('360')
    wrapper.unmount()
    const restored = shallowMount(MainLayout, mountOptions)
    expect(restored.get('[role="separator"]').attributes('aria-valuenow')).toBe('360')
  })

  it('keyboard controls respect bounds and double-click resets the width', async () => {
    const wrapper = shallowMount(MainLayout, mountOptions)
    const handle = wrapper.get('[role="separator"]')
    await handle.trigger('keydown', { key: 'End' })
    const maximum = handle.attributes('aria-valuemax')
    expect(handle.attributes('aria-valuenow')).toBe(maximum)
    await handle.trigger('keydown', { key: 'ArrowRight' })
    expect(handle.attributes('aria-valuenow')).toBe(maximum)
    await handle.trigger('keydown', { key: 'Home' })
    await handle.trigger('keydown', { key: 'ArrowLeft' })
    expect(handle.attributes('aria-valuenow')).toBe('200')
    await handle.trigger('keydown', { key: 'ArrowRight' })
    expect(handle.attributes('aria-valuenow')).toBe('216')
    await handle.trigger('dblclick')
    expect(handle.attributes('aria-valuenow')).toBe('200')
  })

  it('collapse keeps the expanded preference and removes the drag handle', async () => {
    const wrapper = shallowMount(MainLayout, mountOptions)
    await wrapper.get('[role="separator"]').trigger('keydown', { key: 'ArrowRight', shiftKey: true })
    const sider = wrapper.findComponent({ name: 'ALayoutSider' })
    sider.vm.$emit('update:collapsed', true)
    await nextTick()
    expect(wrapper.find('[role="separator"]').exists()).toBe(false)
    expect(wrapper.get('.main-layout').attributes('style')).toContain('--app-content-offset: 80px')
    sider.vm.$emit('update:collapsed', false)
    await nextTick()
    expect(wrapper.get('[role="separator"]').attributes('aria-valuenow')).toBe('240')
  })
})
