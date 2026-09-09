import { useEffect, useState } from 'preact/hooks'
import type { AudioClip } from '../../domain/domain'
import type { Id } from '../../domain/identity'
import { T } from '../labels'
import { useRecorder } from '../recorder'
import { mmss } from './setlists'

interface Props {
  clips: readonly AudioClip[]
  onSave: (blob: Blob, mimeType: string, durationSec: number) => void
  onDelete: (id: Id) => void
  hint?: string
}

/**
 * Запись голоса и прослушивание.
 *
 * У Картер голос первичен: рант проговаривается вслух, act-out играется.
 * Аудио и есть материал — оно хранится целиком, а не как приложение к тексту.
 */
export function VoiceBlock({ clips, onSave, onDelete, hint }: Props) {
  const rec = useRecorder(T.recUnsupported, T.recDenied)
  const [urls, setUrls] = useState<Map<Id, string>>(new Map())

  useEffect(() => {
    // Ссылки на blob держим ровно пока живёт список: иначе каждая перерисовка
    // оставляет за собой утёкший объект в памяти вкладки.
    const next = new Map<Id, string>()
    for (const c of clips) next.set(c.id, URL.createObjectURL(c.bytes))
    setUrls(next)
    return () => next.forEach((u) => URL.revokeObjectURL(u))
  }, [clips])

  return (
    <div>
      {hint && <p class="hint" style="margin-top:0">{hint}</p>}
      <div class="row">
        {rec.recording ? (
          <>
            <span class="rec-dot" />
            <span class="badge" data-testid="rec-timer">{mmss(rec.seconds)}</span>
            <button
              class="btn grow" data-testid="rec-stop"
              onClick={() => void rec.stop().then((r) => { if (r) onSave(r.blob, r.mimeType, r.durationSec) })}
            >
              {T.recStop}
            </button>
          </>
        ) : (
          <button class="btn grow" data-testid="rec-start" onClick={() => void rec.start()}>
            {T.recStart}
          </button>
        )}
      </div>
      {rec.error && <p class="hint" data-testid="rec-error">{rec.error}</p>}

      {clips.map((c) => (
        <div key={c.id} class="row" style="margin-top:10px" data-testid="clip">
          <audio class="grow" controls src={urls.get(c.id)} />
          <button class="btn danger" onClick={() => onDelete(c.id)}>×</button>
        </div>
      ))}
    </div>
  )
}
