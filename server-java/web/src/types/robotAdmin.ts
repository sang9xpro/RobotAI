import type { Role } from './role'
export interface RobotProfile { deviceId: string; roleId: number | null; roles: Pick<Role, 'roleId' | 'roleName' | 'voiceName' | 'sttId' | 'modelId' | 'ttsId'>[] }
export interface MusicTrack { id: string; title: string; artist: string; fileName: string | null; url: string | null }
export interface MusicCatalog { revision: string; tracks: MusicTrack[] }
export interface SystemCheck { id: string; state: 'healthy' | 'degraded' | 'unavailable' | 'unknown'; checkedTime: string; latencyMs: number | null; detail: string }
