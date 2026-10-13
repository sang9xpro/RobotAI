import { http } from './request'
import api from './api'
import type { Message, MessageQueryParams } from '@/types/message'
import type { AdminRobot } from '@/types/user'

export interface DeviceHistoryQuery extends MessageQueryParams {
  keyword?: string
  order?: 'asc' | 'desc'
}
export const queryDeviceHistory = (params: DeviceHistoryQuery) => http.getPage<Message>(api.deviceHistory, params)
export const queryHistoryDevices = (params: { pageNo?: number; pageSize?: number; search?: string; deviceId?: string }) =>
  http.getPage<AdminRobot>(`${api.deviceHistory}/devices`, params)
