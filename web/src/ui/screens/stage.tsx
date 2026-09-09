import { useEffect, useRef, useState } from 'preact/hooks'
import type { Bit, SetList } from '../../domain/domain'
import type { Id } from '../../domain/identity'
import { ROLE_LABEL, T } from '../labels'
import { mmss } from './setlists'

interface Props {
  setList: SetList
  bits: readonly Bit[]
  onFinish: (elapsedSec: number) => void
  onExit: () => void
}

/**
 * Режим сцены.
 *
 * Тёмный экран, крупные подсказки, обратный отсчёт. Показывается название
 * шутки и добивка, а не полный текст: читать со сцены нельзя, подсказка нужна
 * только чтобы вспомнить, что дальше.
 */
export function StageScreen({ setList, bits, onFinish, onExit }: Props) {
  const byId = new Map(bits.map((b) => [b.id, b]))
  const ordered = [...setList.items].sort((a, b) => a.order - b.order)
  const [at, setAt] = useState(0)
  const [running, setRunning] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const wakeLock = useRef<{ release: () => Promise<void> } | null>(null)

  useEffect(() => {
    if (!running) return
    const id = setInterval(() => setElapsed((e) => e + 1), 1000)
    return () => clearInterval(id)
  }, [running])

  useEffect(() => {
    // Экран не должен гаснуть посреди сета. Если браузер не умеет — не беда,
    // но и падать из-за этого приложение не должно.
    if (!running) return
    let cancelled = false
    const nav = navigator as Navigator & { wakeLock?: { request: (t: 'screen') => Promise<never> } }
    void nav.wakeLock?.request('screen').then(
      (l) => { if (cancelled) void (l as { release: () => Promise<void> }).release(); else wakeLock.current = l },
      () => undefined,
    )
    return () => {
      cancelled = true
      void wakeLock.current?.release().catch(() => undefined)
      wakeLock.current = null
    }
  }, [running])

  const item = ordered[at]
  const bit = item ? byId.get(item.bitId) : undefined
  const left = setList.targetDurationSec - elapsed

  return (
    <div class="stage" data-screen="stage">
      <div class="stage-top">
        <span data-testid="stage-timer" class={left < 0 ? 'over' : ''}>{mmss(left)}</span>
        <span>{at + 1} {T.stageOf} {ordered.length}</span>
        <button class="stage-exit" data-testid="stage-exit" onClick={onExit}>{T.stageExit}</button>
      </div>

      <div class="stage-body" onClick={() => setAt((i) => Math.min(ordered.length - 1, i + 1))}>
        {bit ? (
          <>
            <div class="stage-role">{item ? ROLE_LABEL[item.role] : ''}</div>
            <div class="stage-title" data-testid="stage-title">{bit.title}</div>
            {bit.elements.punch && <div class="stage-punch">{bit.elements.punch.text}</div>}
            {bit.elements.actOut && <div class="stage-actout">▶ {bit.elements.actOut.text}</div>}
          </>
        ) : (
          <div class="stage-title">{T.stageDone}</div>
        )}
      </div>

      <div class="stage-controls">
        <button data-testid="stage-prev" onClick={() => setAt((i) => Math.max(0, i - 1))}>{T.stagePrev}</button>
        <button data-testid="stage-run" onClick={() => setRunning((r) => !r)}>
          {running ? T.stagePause : T.stageStart}
        </button>
        <button data-testid="stage-next" onClick={() => setAt((i) => Math.min(ordered.length - 1, i + 1))}>
          {T.stageNext}
        </button>
      </div>

      <button class="stage-finish" data-testid="stage-finish" onClick={() => onFinish(elapsed)}>
        {T.stageFinish}
      </button>
    </div>
  )
}

export const stageBitIds = (s: SetList): Id[] =>
  [...s.items].sort((a, b) => a.order - b.order).map((i) => i.bitId)
