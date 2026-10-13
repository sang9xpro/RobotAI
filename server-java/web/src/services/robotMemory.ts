import { http } from './request'
export const updateRobotSummary = (roleId: number, id: string | number, text: string) =>
  http.put<number>(`/robot/memories/${roleId}/${encodeURIComponent(String(id))}`, { text })
