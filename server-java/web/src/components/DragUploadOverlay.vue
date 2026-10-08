<template>
  <Transition name="fade">
    <div v-if="show" class="drag-upload-overlay">
      <div class="drag-upload-content">
        <InboxOutlined class="drag-upload-icon" />
        <div class="drag-upload-text">{{ text }}</div>
        <div v-if="hint" class="drag-upload-hint">{{ hint }}</div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { InboxOutlined } from '@ant-design/icons-vue'

defineProps<{
  /** 是否显示 */
  show: boolean
  /** 主提示文本 */
  text: string
  /** 辅助提示文本 */
  hint?: string
}>()
</script>

<style scoped lang="scss">
/* 全局拖拽提示层 */
.drag-upload-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(24, 144, 255, 0.1);
  backdrop-filter: blur(4px);
  z-index: 9998;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.drag-upload-content {
  background: var(--ant-color-bg-container);
  border: 2px dashed var(--ant-color-primary);
  border-radius: 12px;
  padding: 48px 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.drag-upload-icon {
  font-size: 64px;
  color: var(--ant-color-primary);
  animation: bounce 1s infinite;
}

.drag-upload-text {
  font-size: 20px;
  font-weight: 500;
  color: var(--ant-color-text);
}

.drag-upload-hint {
  font-size: 14px;
  color: var(--ant-color-text-secondary);
}

/* 淡入淡出动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 图标弹跳动画 */
@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}
</style>
