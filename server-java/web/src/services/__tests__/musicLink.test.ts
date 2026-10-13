import { describe, expect, it } from 'vitest'
import { isYouTubeUrl } from '../musicLibrary'
describe('music link classification before a draft is saved', () => {
  it('only offers an external YouTube anchor for HTTP(S) on exact supported hosts', () => {
    expect(isYouTubeUrl('https://youtu.be/ken')).toBe(true)
    expect(isYouTubeUrl('https://www.youtube.com/watch?v=ken')).toBe(true)
    for (const link of [null, '', 'not a URL', 'javascript://youtube.com/alert(1)', 'ftp://youtube.com/ken', 'https://youtube.com.attacker.example/ken', 'https://secret@youtube.com/ken', 'https://example.com/audio.mp3']) {
      expect(isYouTubeUrl(link)).toBe(false)
    }
  })
})
