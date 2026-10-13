<script setup lang="ts">
import { ref, computed, watch, onScopeDispose } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, TableColumnsType } from 'ant-design-vue'
import { useConfigManager } from '@/composables/useConfigManager'
import { useChatGptModels, effectiveReasoningEffort } from '@/composables/useChatGptModels'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import TableActionButtons from '@/components/TableActionButtons.vue'
import TableEmptyState from '@/components/TableEmptyState.vue'
import type { ConfigType, Config, ConfigField, LLMModel, LlmModelOption } from '@/types/config'
import { addConfig, updateConfig, testConfig, testSttConfig, testTtsConfig, testVisionConfig } from '@/services/config'
import { getResourceUrl } from '@/utils/resource'
import { CODE_CONFIRM_REQUIRED } from '@/constants/api'

const { t } = useI18n()
const userStore = useUserStore()
const { confirmAsync } = useConfirm()
// 测试连接与表单提交各自一个实例，失败文案互不干扰
const { executeOk: executeTestConfig } = useRequest()
const { executeOk: executeSubmitConfig } = useRequest()

interface Props {
  configType: ConfigType
}

const props = defineProps<Props>()

// 使用 composable
const {
  loading,
  configItems,
  currentType,
  editingConfigId,
  activeTabKey,
  modelOptions,
  pagination,
  queryForm,
  loadError,
  retryLoad,
  typeOptionsLoading,
  configTypeInfo,
  typeOptions,
  currentTypeFields,
  fetchData,
  deleteConfig,
  setAsDefault,
  updateModelOptions,
  getModelsByProviderAndType,
  onTableChange,
  debouncedSearch,
} = useConfigManager(props.configType)

// 敏感字段：编辑时留空表示保持原值
const SECRET_FIELDS = ['apiKey', 'apiSecret', 'ak', 'sk']

/** providerConfig 里 config. 开头的值是 i18n key，其余（品牌名、示例值）原样展示 */
function localized(text?: string) {
  if (!text) return ''
  return text.startsWith('config.') || text.startsWith('robotai.') ? t(text) : text
}

function isSecretField(field: ConfigField) {
  return SECRET_FIELDS.includes(field.name)
}

function fieldRequired(field: ConfigField) {
  return editingConfigId.value && isSecretField(field) ? false : field.required
}

function fieldPlaceholder(field: ConfigField) {
  if (editingConfigId.value && isSecretField(field) && currentType.value !== 'sherpa-onnx') {
    return t('config.keepUnchangedHint')
  }
  return localized(field.placeholder) || t('config.enterField', { field: localized(field.label) })
}

// 当前 provider 的 configName 预设选项（用于 AutoComplete）
const currentConfigNameOptions = computed(() => {
  const option = typeOptions.value.find((item) => item.value === currentType.value)
  return option?.configNameOptions?.map((v) => ({ value: v })) ?? []
})

// 表单
const formRef = ref<FormInstance>()
const formData = ref<Partial<Config>>({
  provider: undefined,
  configName: undefined,
  configDesc: undefined,
  // modelType 仅对 llm 有意义（chat/vision/intent/embedding）；其他类型不应携带，避免写入脏值
  modelType: props.configType === 'llm' ? 'chat' : undefined,
  isDefault: false,
  apiKey: undefined,
  apiUrl: undefined,
  appId: undefined,
  apiSecret: undefined,
  ak: undefined,
  sk: undefined,
  enableThinking: false,
})

const isChatGpt = computed(() => props.configType === 'llm' && currentType.value === 'codex-chatgpt')
const chatGptCatalog = useChatGptModels(
  () => ({ ...formData.value, configId: editingConfigId.value }),
  () => isChatGpt.value && activeTabKey.value === '2',
)
const chatGptModelOptions = chatGptCatalog.options
const chatGptModelsLoading = chatGptCatalog.loading
const chatGptModelsError = chatGptCatalog.error
const canLoadChatGptModels = chatGptCatalog.canLoad
const reasoningLabels: Record<string, string> = {
  none: 'reasoningNone', minimal: 'reasoningMinimal', low: 'reasoningLow', medium: 'reasoningMedium',
  high: 'reasoningHigh', xhigh: 'reasoningXHigh', max: 'reasoningMax', ultra: 'reasoningUltra',
}
function reasoningLabel(effort: string) {
  return reasoningLabels[effort] ? `${t(`robotai.${reasoningLabels[effort]}`)} (${effort})` : effort
}
const reasoningOptions = computed(() => chatGptCatalog.reasoningEfforts.value.map(value => ({
  value, label: reasoningLabel(value),
})))
async function validateReasoningEffort() {
  if (!isChatGpt.value) return
  if (chatGptModelsLoading.value || chatGptModelsError.value || !chatGptCatalog.selectedModel.value) {
    throw new Error(t('robotai.reasoningCatalogRequired'))
  }
  if (!formData.value.reasoningEffort || !chatGptCatalog.reasoningEfforts.value.includes(formData.value.reasoningEffort)) {
    throw new Error(t('robotai.reasoningUnsupported'))
  }
}

// providerConfig 驱动的动态字段一律是文本输入，Config 的索引签名比这更宽（要容纳 isDefault/enableThinking 这类布尔具名字段），
// 所以读写收敛到这两个函数里做一次收窄，避免把 any 撒到模板上
function dynamicFieldValue(name: string): string {
  const value = formData.value[name]
  return value === undefined || value === null ? '' : String(value)
}

function setDynamicField(name: string, value: string) {
  formData.value[name] = value
}

// 表格列配置
const columns = computed(() => {
  const baseColumns: TableColumnsType = [
    {
      title: t('config.category'),
      dataIndex: 'provider',
      width: 150,
      align: 'center',
      customRender: ({ text }: { text: string }) => {
        const provider = typeOptions.value.find((item) => item.value === text)
        return provider ? provider.label : text
      },
    },
    {
      title: t('common.name'),
      dataIndex: 'configName',
      width: 200,
      align: 'center',
    },
  ]

  // LLM 添加模型类型列
  if (props.configType === 'llm') {
    baseColumns.push({
      title: t('config.modelType'),
      dataIndex: 'modelType',
      width: 120,
      align: 'center' as const,
    })
  }

  baseColumns.push(
    {
      title: t('common.description'),
      dataIndex: 'configDesc',
      width: 200,
      align: 'center' as const
    },
    {
      title: t('common.isDefault'),
      dataIndex: 'isDefault',
      width: 120,
      align: 'center' as const,
    },
    {
      title: t('common.createTime'),
      dataIndex: 'createTime',
      width: 200,
      align: 'center' as const,
    },
    {
      title: t('table.action'),
      dataIndex: 'operation',
      width: 180,
      align: 'center' as const,
      fixed: 'right' as const,
    }
  )

  // TTS 过滤掉默认列
  if (props.configType === 'tts') {
    return baseColumns.filter((col) => {
      return 'dataIndex' in col && col.dataIndex !== 'isDefault'
    })
  }

  return baseColumns
})

/**
 * 处理类型变化
 */
function handleTypeChange(value: string) {
  currentType.value = value
  if (value === 'codex-chatgpt') formData.value.reasoningEffort = effectiveReasoningEffort(formData.value)
  if (value === 'codex-chatgpt' && formData.value.modelType === 'embedding') {
    formData.value.modelType = 'chat'
  }
  
  // 清空模型名称
  formData.value.configName = undefined
  
  // 如果是 LLM，更新模型选项
  if (props.configType === 'llm' && formData.value.modelType) {
    updateModelOptions(value, formData.value.modelType)
  }
  
  // 填充默认 URL
  const typeField = currentTypeFields.value.find((f: ConfigField) => f.name === 'apiUrl')
  if (typeField?.placeholder) {
    formData.value.apiUrl = typeField.placeholder
  }
}

/**
 * 处理模型类型变化
 */
function handleModelTypeChange(value: string) {
  if (currentType.value) {
    updateModelOptions(currentType.value, value)
    formData.value.configName = undefined
  }
}

/**
 * apiUrl 输入框后缀提示：向量模型使用 embeddings 端点，而非 chat/completions
 */
function getFieldSuffix(field: ConfigField) {
  if (field.name === 'apiUrl' && formData.value.modelType === 'embedding' && field.suffix?.endsWith('/chat/completions')) {
    return field.suffix.replace(/\/chat\/completions$/, '/embeddings')
  }
  return field.suffix
}

/**
 * 处理标签页切换
 */
function handleTabChange(key: string) {
  activeTabKey.value = key
  if (key === '1') {
    fetchData()
  } else if (key === '2') {
    resetForm()
  }
}

/**
 * 编辑配置
 */
function handleEdit(record: Config) {
  clearTestImage()
  editingConfigId.value = record.configId
  currentType.value = record.provider || ''
  activeTabKey.value = '2'

  // 设置表单值，将后端的 string ('0'/'1') 转换为 boolean
  formData.value = {
    ...record,
    // modelType 仅 llm 使用；非 llm 一律清空，避免把历史脏值再次提交
    modelType: props.configType === 'llm' ? (record.modelType || 'chat') : undefined,
    isDefault: props.configType != 'tts' ? record.isDefault === '1' : false,
    enableThinking: !!record.enableThinking,
    reasoningEffort: record.provider === 'codex-chatgpt' ? effectiveReasoningEffort(record) : undefined,
  }

  // LLM 更新模型选项
  if (props.configType === 'llm') {
    updateModelOptions(record.provider, record.modelType || 'chat')
  }
}

// 测试连接中
const testing = ref(false)
const testAudioFile = ref<File>()
const testImageFile = ref<File>()
const testImageInput = ref<HTMLInputElement>()
const isVisionTest = computed(() => props.configType === 'llm' && formData.value.modelType === 'vision')
const testVoiceName = ref('alloy')
const testSampleText = ref('Xin chào, đây là bài kiểm tra RobotAI.')
let testTicket = 0
onScopeDispose(() => { testTicket++ })
const testResult = ref<{ success: boolean; detail: string; elapsedMillis: number; audioUrl?: string }>()
watch([formData, editingConfigId, testVoiceName, testSampleText], () => { testTicket++; testResult.value = undefined }, { deep: true })
watch(() => formData.value.modelType, () => { clearTestImage() })

function clearTestImage() {
  testTicket++
  testImageFile.value = undefined
  testResult.value = undefined
  if (testImageInput.value) testImageInput.value.value = ''
}

function onTestImageChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  testTicket++
  testImageFile.value = undefined
  testResult.value = undefined
  if (!file) return
  if (!/\.(png|jpe?g|gif)$/i.test(file.name)
    || (file.type && !['image/png', 'image/jpeg', 'image/gif'].includes(file.type))
    || file.size === 0 || file.size > 5 * 1024 * 1024) {
    input.value = ''
    antMessage.error(t('robotai.testImageInvalid'))
    return
  }
  testImageFile.value = file
}
function onTestAudioChange(event: Event) {
  testTicket++
  const file = (event.target as HTMLInputElement).files?.[0]
  testAudioFile.value = undefined
  testResult.value = undefined
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.wav') || file.size > 1025536) {
    antMessage.error(t('robotai.testAudio'))
    return
  }
  testAudioFile.value = file
}

// 表单提交中
const submitLoading = ref(false)

/**
 * 测试配置（使用当前表单值，无需先保存）
 */
async function handleTest() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    await validateReasoningEffort()

    if (isVisionTest.value && !testSampleText.value.trim()) {
      antMessage.error(t('robotai.testTextRequired'))
      return
    }

    const submitData: Partial<Config> = {
      ...formData.value,
      configId: editingConfigId.value,
      configType: props.configType,
    }
    submitData.isDefault = formData.value.isDefault == '1' ? '1' : '0'
    if (props.configType === 'llm') submitData.sampleText = testSampleText.value

    // Display provider results inline and ignore responses for a replaced question or attachment.
    const seq = ++testTicket
    const start = performance.now()
    testResult.value = undefined
    await executeTestConfig(() => props.configType === 'stt' && testAudioFile.value
      ? testSttConfig(submitData, testAudioFile.value)
      : isVisionTest.value && testImageFile.value
      ? testVisionConfig(submitData, testImageFile.value)
      : testConfig(submitData), {
      loadingRef: testing,
      showError: false,
      networkErrorText: t('config.testFailed'),
      onSuccess: (data, res) => {
        if (seq !== testTicket) return
        testResult.value = { success: true, detail: data?.detail || res.message,
          elapsedMillis: data?.elapsedMillis ?? Math.round(performance.now() - start) }
      },
      onFailure: (res) => {
        if (seq !== testTicket) return true
        testResult.value = { success: false, detail: res.message, elapsedMillis: Math.round(performance.now() - start) }
        return true
      },
    })
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'errorFields' in error) {
      antMessage.error(t('config.fillRequiredFields'))
    } else {
      console.error('测试配置失败:', error)
      antMessage.error(t('config.testFailed'))
    }
  }
}

/**
 * 提交表单
 */
async function handleTestTts() {
  try {
    await formRef.value?.validate()
    if (!testVoiceName.value.trim()) return
    const seq = ++testTicket
    const start = performance.now()
    testResult.value = undefined
    await executeTestConfig(() => testTtsConfig({ ...formData.value, configId: editingConfigId.value,
      configType: 'tts', voiceName: testVoiceName.value, sampleText: testSampleText.value }), {
      loadingRef: testing, showError: false,
      onSuccess: (data, res) => {
        if (seq !== testTicket) return
        testResult.value = { success: true, detail: res.message, elapsedMillis: Math.round(performance.now() - start),
          audioUrl: getResourceUrl(data.audioUrl) }
      },
      onFailure: res => {
        if (seq !== testTicket) return true
        testResult.value = { success: false, detail: res.message, elapsedMillis: Math.round(performance.now() - start) }
        return true
      }
    })
  } catch {
    antMessage.error(t('config.fillRequiredFields'))
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    await validateReasoningEffort()
    
    // 准备提交数据
    const submitData: Partial<Config> = {
      ...formData.value,
      configId: editingConfigId.value,
      configType: props.configType,
    }

    // 处理 isDefault：将 boolean 转换为后端需要的 string enum ('0'/'1')
    submitData.isDefault = formData.value.isDefault == '1' ? '1' : '0'

    // LLM 特殊验证
    if (props.configType === 'llm') {
      // 验证模型名称
      if (submitData.configName && /[\u4e00-\u9fa5]/.test(submitData.configName)) {
        antMessage.error(t('config.modelNameNoChinese'))
        return
      }

      const validModels = getModelsByProviderAndType(
        submitData.provider || '',
        submitData.modelType || 'chat'
      )
      const knownModel = validModels.find((m: LLMModel) => m.llm_name === submitData.configName)
      const isValid = knownModel !== undefined
      // 上下文长度用户不用关心：清单里查得到就静默带给后端，查不到留空由后端按默认值处理
      submitData.contextLength = knownModel?.max_tokens
      
      // 模型名称不在已知列表里时问一句，取消则中断提交
      if (!isChatGpt.value && !isValid && validModels.length > 0) {
        const shouldContinue = await confirmAsync({
          title: t('common.confirmSubmit'),
          content: t('config.modelNameInvalid', { name: submitData.configName }),
          okText: t('common.confirm'),
        })
        if (!shouldContinue) return
      }
    }

    await submitConfig(submitData, false)
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'errorFields' in error) {
      antMessage.error(t('config.fillRequiredFields'))
    } else {
      console.error('提交配置失败:', error)
      antMessage.error(t('common.operationFailed'))
    }
  }
}

/**
 * 发一次提交请求。
 * 后端判定这次改动会换掉当前生效的对象存储时不直接执行，先带存量条数回一个待确认，
 * 由这里把后果原样问给用户，确认后带确认参数原样重发一次。
 */
async function submitConfig(submitData: Partial<Config>, confirmStorageSwitch: boolean) {
  const isEditing = !!editingConfigId.value

  await executeSubmitConfig(
    () => (isEditing
      ? updateConfig(submitData, confirmStorageSwitch)
      : addConfig(submitData, confirmStorageSwitch)),
    {
      loadingRef: submitLoading,
      showSuccess: true,
      successText: isEditing ? t('config.updateSuccess') : t('config.createSuccess'),
      errorText: t('common.operationFailed'),
      networkErrorText: t('common.operationFailed'),
      onSuccess: async () => {
        resetForm()
        activeTabKey.value = '1'
        await fetchData()
      },
      onFailure: async (res) => {
        if (res.code !== CODE_CONFIRM_REQUIRED) {
          return false
        }
        const confirmed = await confirmAsync({
          title: t('config.storageSwitchTitle'),
          content: res.message,
          okText: t('config.storageSwitchOk'),
          okType: 'danger',
        })
        if (confirmed) {
          await submitConfig(submitData, true)
        }
        return true
      },
    },
  )
}

/**
 * 重置表单
 */
function resetForm() {
  clearTestImage()
  formRef.value?.resetFields()
  currentType.value = ''
  editingConfigId.value = undefined
  modelOptions.value = []
  formData.value = {
    provider: undefined,
    configName: undefined,
    configDesc: undefined,
    // modelType 仅对 llm 有意义，其他类型保持 undefined 不提交
    modelType: props.configType === 'llm' ? 'chat' : undefined,
    isDefault: false,
    apiKey: undefined,
    apiUrl: undefined,
    appId: undefined,
    apiSecret: undefined,
    ak: undefined,
    sk: undefined,
    enableThinking: false,
  }
}

/**
 * 取消
 */
function handleCancel() {
  resetForm()
  activeTabKey.value = '1'
}

/**
 * 获取默认标签颜色
 */
function getDefaultTagColor(record: Config) {
  if (props.configType === 'llm') {
    const colors: Record<string, string> = {
      chat: 'blue',
      vision: 'purple',
      intent: 'orange',
      embedding: 'green',
    }
    return colors[record.modelType || ''] || 'green'
  }
  return props.configType === 'stt' ? 'cyan' : 'green'
}

/**
 * 获取默认标签文本
 */
function getDefaultTagText(record: Config) {
  if (props.configType === 'llm') {
    const texts: Record<string, string> = {
      chat: t('config.defaultChat'),
      vision: t('config.defaultVision'),
      intent: t('config.defaultIntent'),
      embedding: t('config.defaultEmbedding'),
    }
    return texts[record.modelType || ''] || t('common.default')
  }
  return props.configType === 'stt' ? t('config.defaultStt') : t('common.default')
}

/**
 * 模型类型标签
 */
function getModelTypeTag(modelType: string) {
  const tags: Record<string, { text: string; color: string }> = {
    chat: { text: t('config.chatModel'), color: 'blue' },
    vision: { text: t('config.visionModel'), color: 'purple' },
    intent: { text: t('config.intentModel'), color: 'orange' },
    embedding: { text: t('config.embeddingModel'), color: 'green' },
  }
  return tags[modelType] || { text: '-', color: 'default' }
}

// 初始化
fetchData()
</script>

<template>
  <div class="config-manager">
    <!-- 查询表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px" class="search-card">
      <a-form layout="horizontal" :colon="false">
        <a-row :gutter="16">
          <a-col :xxl="8" :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('config.category')">
              <a-select v-model:value="queryForm.provider" :loading="typeOptionsLoading" @change="debouncedSearch">
                <a-select-option value="">{{ t('common.all') }}</a-select-option>
                <a-select-option
                  v-for="item in typeOptions"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <a-col :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('common.name')">
              <a-input
                v-model:value="queryForm.configName"
                :placeholder="t('config.pleaseEnter')"
                allow-clear
                @input="debouncedSearch"
              />
            </a-form-item>
          </a-col>

          <a-col v-if="configType === 'llm'" :xxl="8" :xl="8" :lg="12" :xs="24">
            <a-form-item :label="t('config.modelType')">
              <a-select v-model:value="queryForm.modelType" @change="debouncedSearch">
                <a-select-option value="">{{ t('common.all') }}</a-select-option>
                <a-select-option value="chat">{{ t('config.chatModel') }}</a-select-option>
                <a-select-option value="vision">{{ t('config.visionModel') }}</a-select-option>
                <a-select-option value="intent">{{ t('config.intentModel') }}</a-select-option>
                <a-select-option value="embedding">{{ t('config.embeddingModel') }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 表格和表单 -->
    <a-card :bordered="false" :body-style="{ padding: '0 24px 24px 24px' }">
      <a-tabs
        v-model:active-key="activeTabKey"
        @change="handleTabChange"
      >
        <!-- 列表标签页 -->
        <a-tab-pane key="1" :tab="`${t(configTypeInfo.label)} ${t('config.list')}`">
          <a-table
            :columns="columns"
            :data-source="configItems"
            :loading="loading"
            :pagination="pagination"
            @change="onTableChange"
            row-key="configId"
            :scroll="{ x: 800 }"
            size="middle"
          >
            <template #emptyText>
              <TableEmptyState :error="loadError" @retry="retryLoad" />
            </template>

            <template #bodyCell="{ column, record }">
              <!-- 模型类型列 -->
              <template v-if="column.dataIndex === 'modelType' && configType === 'llm'">
                <a-tag :color="getModelTypeTag(record.modelType).color">
                  {{ getModelTypeTag(record.modelType).text }}
                </a-tag>
                <a-tag v-if="record.provider === 'codex-chatgpt'" color="cyan">
                  {{ reasoningLabel(effectiveReasoningEffort(record)) }}
                </a-tag>
                <a-tag v-else-if="record.enableThinking" color="cyan">
                  {{ t('config.enableThinking') }}
                </a-tag>
              </template>

              <!-- 描述列 -->
              <template v-else-if="column.dataIndex === 'configDesc'">
                <a-tooltip :title="record.configDesc" :mouse-enter-delay="0.5" placement="topLeft">
                  <span v-if="record.configDesc" class="ellipsis-text">{{ record.configDesc }}</span>
                  <span v-else>-</span>
                </a-tooltip>
              </template>

              <!-- 默认标识列 -->
              <template v-else-if="column.dataIndex === 'isDefault'">
                <a-tag v-if="record.isDefault === '1'" :color="getDefaultTagColor(record)">
                  {{ getDefaultTagText(record) }}
                </a-tag>
                <span v-else>-</span>
              </template>

              <!-- 操作列 -->
              <template v-else-if="column.dataIndex === 'operation'">
                <TableActionButtons
                  :record="record"
                  :permission-prefix="configTypeInfo.permissionPrefix"
                  show-edit
                  :show-set-default="configType !== 'tts'"
                  :show-delete="record.isDefault !== '1'"
                  :is-default="record.isDefault === '1'"
                  :delete-title="t('config.confirmDelete', { type: t(configTypeInfo.label) })"
                  @edit="() => handleEdit(record)"
                  @set-default="() => setAsDefault(record)"
                  @delete="() => deleteConfig(record.configId)"
                />
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- 创建/编辑标签页 -->
        <a-tab-pane
          v-if="userStore.hasPermission(editingConfigId ? `${configTypeInfo.permissionPrefix}:update` : `${configTypeInfo.permissionPrefix}:create`)"
          key="2"
          :tab="editingConfigId ? `${t('config.update', { type: t(configTypeInfo.label) })}` : `${t('config.create')} ${t(configTypeInfo.label)}`"
        >
          <a-form
            ref="formRef"
            :model="formData"
            layout="horizontal"
            :colon="false"
            style="padding: 10px 24px"
            :hideRequiredMark="true"
          >
            <a-row :gutter="20">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="`${t(configTypeInfo.label)} ${t('config.category')}`"
                  name="provider"
                  :rules="[{ required: true, message: t('config.selectCategory', { type: t(configTypeInfo.label) }) }]"
                >
                  <a-select
                    v-model:value="formData.provider"
                    :placeholder="t('config.selectCategory', { type: t(configTypeInfo.label) })"
                    :loading="typeOptionsLoading"
                    @change="handleTypeChange"
                  >
                    <a-select-option
                      v-for="item in typeOptions"
                      :key="item.value"
                      :value="item.value"
                    >
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>

              <!-- LLM 模型类型 -->
              <a-col v-if="configType === 'llm'" :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="t('config.modelType')"
                  name="modelType"
                  :rules="[{ required: true, message: t('config.selectModelType') }]"
                >
                  <a-select
                    v-model:value="formData.modelType"
                    :placeholder="t('config.selectModelType')"
                    @change="handleModelTypeChange"
                  >
                    <a-select-option value="chat">{{ t('config.chatModel') }}</a-select-option>
                    <a-select-option value="vision">{{ t('config.visionModel') }}</a-select-option>
                    <a-select-option value="intent">{{ t('config.intentModel') }}</a-select-option>
                    <a-select-option value="embedding" :disabled="isChatGpt">{{ t('config.embeddingModel') }}</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>

              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item
                  :label="isChatGpt ? t('robotai.chatGptModel') : formData.provider?.toLowerCase() === 'openai' ? t('robotai.modelId') : `${t(configTypeInfo.label)} ${t('common.name')}`"
                  name="configName"
                  :rules="[{ required: true, message: t('config.enterName', { type: t(configTypeInfo.label) }) }]"
                >
                  <a-select
                    v-if="isChatGpt"
                    v-model:value="formData.configName"
                    show-search
                    allow-clear
                    :placeholder="t('robotai.selectChatGptModel')"
                    :options="chatGptModelOptions"
                    :loading="chatGptModelsLoading"
                    option-filter-prop="label"
                  />
                  <!-- LLM 使用 AutoComplete（可选可填） -->
                  <a-auto-complete
                    v-else-if="configType === 'llm' && currentType"
                    v-model:value="formData.configName"
                    allow-clear
                    :placeholder="t('config.enterName', { type: t(configTypeInfo.label) })"
                    :options="modelOptions"
                    :filter-option="
                      (input: string, option: LlmModelOption) =>
                        option.label.toLowerCase().includes(input.toLowerCase())
                    "
                  />
                  <!-- 有预设模型选项时使用 AutoComplete（可选可填） -->
                  <a-auto-complete
                    v-else-if="currentConfigNameOptions.length > 0"
                    v-model:value="formData.configName"
                    :options="currentConfigNameOptions"
                    :placeholder="t('config.enterName', { type: t(configTypeInfo.label) })"
                    :filter-option="
                      (input: string, option: { value: string }) =>
                        option.value.toLowerCase().includes(input.toLowerCase())
                    "
                    allow-clear
                  />
                  <!-- 其他使用输入框 -->
                  <a-input
                    v-else
                    v-model:value="formData.configName"
                    :placeholder="t('config.enterName', { type: t(configTypeInfo.label) })"
                  />
                  <template v-if="isChatGpt" #extra>
                    <div style="margin-top: 6px">
                      <span :style="chatGptModelsError ? { color: 'var(--ant-color-error)' } : undefined">
                        {{ chatGptModelsError ? t('robotai.chatGptModelsFailed')
                          : !canLoadChatGptModels ? t('robotai.chatGptModelsCredentials') : t('robotai.chatGptModelsHint') }}
                      </span>
                      <a-button type="link" size="small" :loading="chatGptModelsLoading"
                        :disabled="!canLoadChatGptModels" @click="chatGptCatalog.refresh()">
                        {{ t('robotai.refresh') }}
                      </a-button>
                    </div>
                  </template>
                </a-form-item>
              </a-col>
            </a-row>

            <a-form-item :label="`${t(configTypeInfo.label)} ${t('common.description')}`" name="configDesc">
              <a-textarea
                v-model:value="formData.configDesc"
                :placeholder="t('config.enterDescription', { type: t(configTypeInfo.label) })"
                :rows="4"
              />
            </a-form-item>

            <!-- 设为默认 -->
            <a-form-item
              v-if="configType !== 'tts'"
              v-permission="`${configTypeInfo.permissionPrefix}:update`"
              :label="t('config.setAsDefault', { type: t(configTypeInfo.label) })"
              name="isDefault"
            >
              <a-switch v-model:checked="formData.isDefault" />
              <span style="margin-left: 8px; color: var(--ant-color-text-tertiary)">
                {{ t('config.defaultTip') }}
              </span>
            </a-form-item>

            <a-form-item v-if="isChatGpt" :label="t('robotai.reasoningEffort')" name="reasoningEffort"
              :rules="[{ validator: validateReasoningEffort }]">
              <a-select v-model:value="formData.reasoningEffort" :options="reasoningOptions"
                :placeholder="t('robotai.selectReasoningEffort')" :loading="chatGptModelsLoading"
                :disabled="chatGptModelsLoading || reasoningOptions.length === 0"
                style="max-width: 360px" />
              <template #extra>{{ t('robotai.reasoningHint') }}</template>
            </a-form-item>

            <!-- Legacy thinking switch for other providers. -->
            <a-form-item
              v-else-if="configType === 'llm'"
              :label="t('config.enableThinking')"
              name="enableThinking"
            >
              <a-switch v-model:checked="formData.enableThinking" />
              <span style="margin-left: 8px; color: var(--ant-color-text-tertiary)">
                {{ t('config.enableThinkingTip') }}
              </span>
            </a-form-item>


            <a-divider>{{ t('config.parameterConfig') }}</a-divider>

            <!-- 动态参数字段 -->
            <a-card
              v-if="currentType"
              size="small"
              :bordered="false"
            >
              <!-- 本地模型使用说明 -->
              <a-alert
                v-if="currentType === 'sherpa-onnx' && configType === 'tts'"
                type="info"
                show-icon
                style="margin-bottom: 16px"
              >
                <template #message>{{ t('config.sherpaOnnx.title') }}</template>
                <template #description>
                  <div style="font-size: 13px; line-height: 2">
                    <p style="margin: 0">{{ t('config.sherpaOnnx.noParams') }}</p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.architectures')"></p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.modelDir')"></p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.matchaVocoder')"></p>
                    <p style="margin: 0" v-html="t('config.sherpaOnnx.chineseDict')"></p>
                  </div>
                </template>
              </a-alert>
              <a-row :gutter="20">
                <a-col
                  v-for="field in currentTypeFields"
                  :key="field.name"
                  :xl="field.span || 12"
                  :lg="12"
                  :xs="24"
                >
                  <a-form-item
                    :label="localized(field.label)"
                    :name="field.name"
                    :rules="[{ required: fieldRequired(field), message: t('config.enterField', { field: localized(field.label) }) }]"
                    style="margin-bottom: 24px"
                  >
                    <a-input
                      :value="dynamicFieldValue(field.name)"
                      :placeholder="fieldPlaceholder(field)"
                      :type="field.inputType || 'text'"
                      :autocomplete="isSecretField(field) ? 'new-password' : 'off'"
                      @update:value="(v: string) => setDynamicField(field.name, v)"
                    >
                      <template v-if="field.suffix" #suffix>
                        <span style="color: var(--ant-color-text-tertiary)">{{ getFieldSuffix(field) }}</span>
                      </template>
                    </a-input>
                    <div v-if="field.help" class="field-help">
                      {{ localized(field.help) }}
                    </div>
                  </a-form-item>
                </a-col>
              </a-row>
            </a-card>

            <a-card v-else :bordered="false">
              <a-empty :description="t('config.selectCategoryFirst', { type: t(configTypeInfo.label) })" />
            </a-card>

            <a-card v-if="['llm', 'stt', 'tts'].includes(configType)" size="small" style="margin-top: 16px">
              <a-alert type="info" show-icon :message="t('robotai.testingConsent')" :description="t('robotai.nextSession')" style="margin-bottom:16px" />
              <p v-if="['stt', 'tts'].includes(configType) && formData.provider?.toLowerCase() === 'openai'">{{ t('robotai.compatibleSpeechHint') }}</p>
              <template v-if="configType === 'stt'">
                <label for="robotai-stt-sample">{{ t('robotai.testAudio') }}</label>
                <input id="robotai-stt-sample" type="file" accept=".wav,audio/wav" @change="onTestAudioChange" />
                <p>{{ t('robotai.testAudio') }}</p>
              </template>
              <a-form-item v-if="configType === 'llm'" :label="t('robotai.testText')"><a-textarea v-model:value="testSampleText" :maxlength="500" /></a-form-item>
              <a-form-item v-if="isVisionTest" :label="t('robotai.testImage')">
                <a-space>
                  <input id="robotai-vision-sample" ref="testImageInput" type="file"
                    :aria-label="t('robotai.testImage')" accept=".png,.jpg,.jpeg,.gif,image/png,image/jpeg,image/gif"
                    @change="onTestImageChange" />
                  <a-button v-if="testImageFile" size="small" @click="clearTestImage">{{ t('robotai.removeTestImage') }}</a-button>
                </a-space>
                <p class="field-help">{{ t('robotai.testImageHint') }}</p>
              </a-form-item>
              <template v-if="configType === 'tts'">
                <a-form-item :label="t('robotai.testVoice')"><a-input v-model:value="testVoiceName" /></a-form-item>
                <a-form-item :label="t('robotai.testText')"><a-textarea v-model:value="testSampleText" :maxlength="500" /></a-form-item>
              </template>
              <a-alert v-if="testResult" :type="testResult.success ? 'success' : 'error'" show-icon
                :message="`${testResult.elapsedMillis} ms`" :description="testResult.detail" />
              <audio v-if="testResult?.audioUrl" :src="testResult.audioUrl" controls preload="none" style="width: 100%; margin-top: 8px" />
            </a-card>

            <a-form-item style="margin-top: 24px">
              <a-space>
                <!-- a-space 会给每个子项包一层 item，v-permission 的 display:none 会留下空格子，这里改用 v-if 整块不渲染 -->
                <a-button
                  v-if="userStore.hasPermission(editingConfigId ? `${configTypeInfo.permissionPrefix}:update` : `${configTypeInfo.permissionPrefix}:create`)"
                  type="primary"
                  :loading="submitLoading"
                  @click="handleSubmit"
                >
                  {{ editingConfigId ? t('config.update', { type: t(configTypeInfo.label) }) : t('config.create', { type: t(configTypeInfo.label) }) }}
                </a-button>
                <a-button v-if="['llm', 'stt'].includes(props.configType)" :loading="testing"
                  :disabled="isVisionTest && !testSampleText.trim()" @click="handleTest">
                  {{ t('config.test') }}
                </a-button>
                <a-button v-if="configType === 'tts'" :loading="testing" :disabled="!testVoiceName.trim()" @click="handleTestTts">{{ t('config.test') }}</a-button>
                <a-button @click="handleCancel">{{ t('common.cancel') }}</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<style scoped lang="scss">
.config-manager {
  padding: 24px;
}

.field-help {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ant-color-text-tertiary);
}
</style>
