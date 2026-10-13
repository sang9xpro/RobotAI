import { http } from './request'
import type { RobotProfile } from '@/types/robotAdmin'
export const getRobotProfile = () => http.get<RobotProfile>('/user/robot-profile')
export const selectRobotRole = (roleId: number) => http.put<RobotProfile>('/user/robot-profile/role', { roleId })
