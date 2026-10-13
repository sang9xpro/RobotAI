<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useStorage, useWindowSize, useEventListener } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import { ROUTES } from '@/router/routes'
import FloatingChat from '@/components/FloatingChat.vue'
import { STORAGE_SIDEBAR_WIDTH } from '@/constants/storage'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

// 悬浮聊天是给非对话页准备的快捷入口，对话页本身已有完整会话界面，再浮一个是重复
const showFloatingChat = computed(() => route.name !== 'chat')
const userStore = useUserStore()

// Keep the expanded preference when collapsing or using a narrower viewport.
const SIDEBAR_WIDTH = 200
const SIDEBAR_COLLAPSED_WIDTH = 80
const savedSidebarWidth = useStorage(STORAGE_SIDEBAR_WIDTH, SIDEBAR_WIDTH)
const { width: viewportWidth } = useWindowSize()
const maxSidebarWidth = computed(() => Math.max(SIDEBAR_WIDTH, Math.min(480, viewportWidth.value - 280)))
function clampSidebarWidth(width: number) {
  return Math.round(Math.min(maxSidebarWidth.value, Math.max(SIDEBAR_WIDTH,
    Number.isFinite(width) ? width : SIDEBAR_WIDTH)))
}
const expandedSidebarWidth = computed(() => clampSidebarWidth(savedSidebarWidth.value))
const isCollapsed = ref(false)
const sidebarWidth = computed(() => isCollapsed.value ? SIDEBAR_COLLAPSED_WIDTH : expandedSidebarWidth.value)
const isResizing = ref(false)
let resizeHandle: HTMLElement | undefined
let resizePointerId: number | undefined
let startX = 0
let startWidth = SIDEBAR_WIDTH

function startResize(event: PointerEvent) {
  if (event.button !== 0 || isCollapsed.value) return
  event.preventDefault()
  resizeHandle = event.currentTarget as HTMLElement
  resizePointerId = event.pointerId
  startX = event.clientX
  startWidth = expandedSidebarWidth.value
  resizeHandle.setPointerCapture(event.pointerId)
  isResizing.value = true
}

function resizeSidebar(event: PointerEvent) {
  if (!isResizing.value || event.pointerId !== resizePointerId) return
  savedSidebarWidth.value = clampSidebarWidth(startWidth + event.clientX - startX)
}

function stopResize() {
  const handle = resizeHandle
  const pointerId = resizePointerId
  resizeHandle = undefined
  resizePointerId = undefined
  isResizing.value = false
  if (handle && pointerId !== undefined && handle.hasPointerCapture(pointerId)) {
    handle.releasePointerCapture(pointerId)
  }
}

function resizeWithKeyboard(event: KeyboardEvent) {
  const step = event.shiftKey ? 40 : 16
  const widths: Record<string, number> = {
    ArrowLeft: expandedSidebarWidth.value - step, ArrowRight: expandedSidebarWidth.value + step,
    Home: SIDEBAR_WIDTH, End: maxSidebarWidth.value,
  }
  const width = widths[event.key]
  if (width === undefined) return
  event.preventDefault()
  savedSidebarWidth.value = clampSidebarWidth(width)
}

useEventListener(window, 'blur', stopResize)
watch(isCollapsed, stopResize)
onBeforeUnmount(stopResize)

// 用户信息
const userInfo = computed(() => userStore.userInfo)

// 供内容区固定定位元素避开侧边栏
const contentOffset = computed(() => `${sidebarWidth.value}px`)

/**
 * 处理断点变化（响应式布局）
 */
function handleBreakpoint(broken: boolean) {
  isCollapsed.value = broken
}

onMounted(() => {
  // 检查登录状态
  if (!userInfo.value) {
    router.push(ROUTES.LOGIN)
  }
})
</script>

<template>
  <div class="main-layout" :class="{ 'is-resizing': isResizing }" :style="{ '--app-content-offset': contentOffset }">
    <a-layout>
      <!-- 占位 Sider - 用于保持内容区域位置 -->
      <div
        class="sider-placeholder"
        :style="{
          width: `${sidebarWidth}px`,
          flex: `0 0 ${sidebarWidth}px`,
          maxWidth: `${sidebarWidth}px`,
          minWidth: `${sidebarWidth}px`,
        }"
      />

      <!-- 固定侧边栏 -->
      <a-layout-sider
        v-model:collapsed="isCollapsed"
        theme="light"
        breakpoint="lg"
        :collapsed-width="SIDEBAR_COLLAPSED_WIDTH"
        :width="expandedSidebarWidth"
        @breakpoint="handleBreakpoint"
        collapsible
        class="fixed-sidebar"
      >
        <AppSidebar :collapsed="isCollapsed" />
      </a-layout-sider>

      <div v-if="!isCollapsed" class="sidebar-resize-handle" role="separator" tabindex="0"
        aria-orientation="vertical" :aria-label="t('robotai.resizeMenu')"
        :aria-valuemin="SIDEBAR_WIDTH" :aria-valuemax="maxSidebarWidth" :aria-valuenow="expandedSidebarWidth"
        :title="t('robotai.resizeMenuHint')" :style="{ left: `${sidebarWidth - 4}px` }"
        @pointerdown="startResize" @pointermove="resizeSidebar" @pointerup="stopResize"
        @pointercancel="stopResize" @lostpointercapture="stopResize" @keydown="resizeWithKeyboard"
        @dblclick="savedSidebarWidth = SIDEBAR_WIDTH"
      />

      <!-- 主内容区域 -->
      <a-layout class="main-content-layout">
        <!-- 顶部栏 -->
        <a-layout-header class="layout-header">
          <AppHeader />
        </a-layout-header>

        <!-- 内容区 -->
        <a-layout-content class="layout-content">
          <!-- 按 fullPath 打 key，同一路由换查询参数时也重新挂载 -->
          <router-view v-slot="{ Component }">
            <component :is="Component" :key="$route.fullPath" />
          </router-view>
        </a-layout-content>

        <!-- 页脚 -->
        <a-layout-footer class="layout-footer">
          <AppFooter />
        </a-layout-footer>
      </a-layout>
    </a-layout>

    <!-- 浮动聊天组件 -->
    <FloatingChat v-if="showFloatingChat" />
  </div>
</template>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  min-height: 100vh;
}

.sider-placeholder {
  overflow: hidden;
  transition: all 0.2s;
}

.fixed-sidebar {
  height: 100vh;
  z-index: 99;
  position: fixed;
  left: 0;
  overflow: auto;

  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
  }
}

.sidebar-resize-handle {
  position: fixed;
  top: 0;
  bottom: 48px;
  width: 8px;
  z-index: 100;
  cursor: col-resize;
  touch-action: none;
  user-select: none;
  outline: none;

  &::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 3px;
    width: 2px;
    height: 40px;
    transform: translateY(-50%);
    border-radius: 2px;
    background: var(--ant-color-text-quaternary);
  }

  &:hover, &:focus-visible {
    background: var(--ant-color-primary-bg);
    &::after { background: var(--ant-color-primary); }
  }
}

.is-resizing {
  cursor: col-resize;
  user-select: none;

  :deep(*) { cursor: col-resize !important; user-select: none !important; }
  .sider-placeholder, .fixed-sidebar { transition: none !important; }
  .sidebar-resize-handle::after { background: var(--ant-color-primary); }
}

.main-content-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.layout-header {
  padding: 0;
  height: auto;
  line-height: normal;
  background: var(--ant-color-bg-container);
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03),
      0 1px 6px -1px rgba(0, 0, 0, 0.02),
      0 2px 4px 0 rgba(0, 0, 0, 0.02);
  flex-shrink: 0;
}

.layout-content {
  flex: 1;
  position: relative;
}


.layout-footer {
  padding: 0;
  background: transparent;
  flex-shrink: 0;
}
</style>

