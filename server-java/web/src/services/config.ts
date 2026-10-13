import { http } from './request'
import api from './api'
import type { AvailableChatGptModel, Config, ConfigQueryParams } from '@/types/config'
import type { PlatformConfig } from '@/types/agent'
import type { ConfigTestResult, ConfigTtsTestResult } from '@/types/config'

/**
 * 查询配置列表
 */
export function queryConfigs<T = Config>(params: Partial<ConfigQueryParams>) {
  return http.getPage<T>(api.config.root, params)
}

/**
 * 添加配置
 * @param confirmStorageSwitch 已确认换掉当前生效的对象存储会让历史文件不可访问
 */
export function addConfig<T = Config>(data: Partial<T>, confirmStorageSwitch = false) {
  return http.post(api.config.root, data, { params: { confirmStorageSwitch } })
}

/**
 * 更新配置
 * @param confirmStorageSwitch 已确认换掉当前生效的对象存储会让历史文件不可访问
 */
export function updateConfig<T extends { configId?: number } = Config>(data: Partial<T>, confirmStorageSwitch = false) {
  return http.put(`${api.config.root}/${data.configId}`, data, { params: { confirmStorageSwitch } })
}

/**
 * 删除配置
 *
 * @param confirmStorageSwitch 已确认删掉当前生效的对象存储配置会让历史文件不可访问
 */
export function deleteConfig(configId: number, confirmStorageSwitch = false) {
  return http.delete(`${api.config.root}/${configId}`, { params: { confirmStorageSwitch } })
}

/**
 * 测试配置（使用当前表单值，可能尚未保存）
 */
export function testConfig(data: Partial<Config>) {
  return http.post<ConfigTestResult>(api.config.test, data, { timeout: 120000 })
}

/** Uses the current gateway settings, filling an unchanged token on the backend. */
export function queryChatGptModels(data: Partial<Config>) {
  return http.post<AvailableChatGptModel[]>(`${api.config.root}/models`, data, { timeout: 30000 })
}

/**
 * 查询平台配置（智能体 / 音色克隆平台，返回的是 PlatformConfig 形状）
 */
export function queryPlatformConfig(configType: string, provider: string) {
  return queryConfigs<PlatformConfig>({ configType, provider } as Partial<ConfigQueryParams>)
}

/**
 * 添加平台配置
 */
export function addPlatformConfig(data: Partial<PlatformConfig>) {
  return addConfig<PlatformConfig>(data)
}

/**
 * 更新平台配置
 */
export function updatePlatformConfig(data: Partial<PlatformConfig>) {
  return updateConfig<PlatformConfig>(data)
}

/** Multipart JSON + bounded PCM WAV sample for an unsaved STT configuration. */
export function testSttConfig(data: Partial<Config>, audio: File) {
  const form = new FormData()
  form.append('config', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  form.append('audio', audio)
  return http.postMultipart<ConfigTestResult>(`${api.config.test}/stt`, form, { timeout: 120000 })
}

/** Sends the entered question and selected image without saving the configuration or file. */
export function testVisionConfig(data: Partial<Config>, image: File) {
  const form = new FormData()
  form.append('config', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  form.append('image', image)
  return http.postMultipart<ConfigTestResult>(`${api.config.test}/vision`, form, { timeout: 120000 })
}

/** Generates a signed preview using current form values without saving provider credentials. */
export function testTtsConfig(data: Partial<Config>) {
  return http.post<ConfigTtsTestResult>(`${api.config.test}/tts`, data, { timeout: 120000 })
}
