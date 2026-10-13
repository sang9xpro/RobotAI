import { http } from './request'
import type { SystemCheck } from '@/types/robotAdmin'
export const getSystemStatus = () => http.get<SystemCheck[]>('/robot/system/status')
