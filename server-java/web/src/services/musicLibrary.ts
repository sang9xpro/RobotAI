import request, { http } from './request'
import type { MusicCatalog } from '@/types/robotAdmin'
export const getMusicCatalog = () => http.get<MusicCatalog>('/robot/music/catalog')
export const saveMusicCatalog = (catalog: MusicCatalog) => http.put<MusicCatalog>('/robot/music/catalog', catalog)
export const uploadMusic = (file: File) => {
  const data = new FormData(); data.append('file', file)
  return http.postMultipart<MusicCatalog>('/robot/music/files', data, { timeout: 60000 })
}
export const deleteMusicFile = (name: string, revision: string) =>
  http.delete<MusicCatalog>(`/robot/music/files/${encodeURIComponent(name)}`, { revision })
export const getMusicAudio = async (name: string): Promise<Blob> =>
  (await request.get('/robot/music/audio', { params: { name }, responseType: 'blob' })).data
export function isYouTubeUrl(url: string | null) {
  if (!url) return false
  try {
    const parsed = new URL(url)
    return ['http:', 'https:'].includes(parsed.protocol) && !parsed.username && !parsed.password &&
      ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be'].includes(parsed.hostname.toLowerCase())
  }
  catch { return false }
}
