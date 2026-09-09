import { useCallback, useRef, useState } from 'preact/hooks'

/**
 * Запись голоса.
 *
 * У Картер ввод голосом первичен: рант проговаривается, act-out играется.
 * Аудио хранится как есть — оно и есть материал, транскрипт вторичен.
 */
export interface RecorderState {
  readonly recording: boolean
  readonly seconds: number
  readonly error: string | null
  start: () => Promise<void>
  stop: () => Promise<{ blob: Blob; mimeType: string; durationSec: number } | null>
}

export function useRecorder(unsupportedText: string, deniedText: string): RecorderState {
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const rec = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const ticker = useRef<ReturnType<typeof setInterval> | null>(null)
  const stream = useRef<MediaStream | null>(null)

  const cleanup = useCallback(() => {
    if (ticker.current) clearInterval(ticker.current)
    ticker.current = null
    stream.current?.getTracks().forEach((t) => t.stop())
    stream.current = null
  }, [])

  const start = useCallback(async () => {
    setError(null)
    if (typeof MediaRecorder === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      setError(unsupportedText)
      return
    }
    try {
      const s = await navigator.mediaDevices.getUserMedia({ audio: true })
      stream.current = s
      chunks.current = []
      const r = new MediaRecorder(s)
      r.ondataavailable = (e) => { if (e.data.size > 0) chunks.current.push(e.data) }
      r.start()
      rec.current = r
      setSeconds(0)
      setRecording(true)
      ticker.current = setInterval(() => setSeconds((x) => x + 1), 1000)
    } catch {
      // Отказ в микрофоне — это не поломка приложения, а решение человека.
      setError(deniedText)
      cleanup()
    }
  }, [cleanup, deniedText, unsupportedText])

  const stop = useCallback(async () => {
    const r = rec.current
    if (!r) return null
    const done = new Promise<Blob>((resolve) => {
      r.onstop = () => resolve(new Blob(chunks.current, { type: r.mimeType || 'audio/webm' }))
    })
    r.stop()
    const blob = await done
    const durationSec = seconds
    rec.current = null
    setRecording(false)
    cleanup()
    return { blob, mimeType: r.mimeType || 'audio/webm', durationSec }
  }, [cleanup, seconds])

  return { recording, seconds, error, start, stop }
}
