import { useEffect, useRef, useState } from 'preact/hooks'
import type { JournalEntry } from '../../domain/domain'
import type { Id } from '../../domain/identity'
import { T } from '../labels'
import { count } from '../plural'
import { mmss } from './setlists'

interface Props {
  entries: readonly JournalEntry[]
  streakDays: number
  onSave: (text: string, durationSec: number) => void
  onHarvest: (text: string) => void
  onDelete: (id: Id) => void
}

/**
 * Утренние страницы.
 *
 * Таймер идёт, пока пишешь, и не мешает: у Картер смысл в непрерывности,
 * а не в объёме. Из готовой записи можно выделить кусок и отправить его
 * зерном во «Входящие» — это и есть переход от письма к материалу.
 */
export function JournalScreen({ entries, streakDays, onSave, onHarvest, onDelete }: Props) {
  const [text, setText] = useState('')
  const [seconds, setSeconds] = useState(0)
  const [running, setRunning] = useState(false)
  const area = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (!running) return
    const id = setInterval(() => setSeconds((s) => s + 1), 1000)
    return () => clearInterval(id)
  }, [running])

  function harvestSelection() {
    const el = area.current
    if (!el) return
    const picked = el.value.slice(el.selectionStart, el.selectionEnd).trim()
    if (picked) onHarvest(picked)
  }

  return (
    <div class="scroll" data-screen="journal">
      <div class="card">
        <h2>{T.journalTitle}</h2>
        {streakDays > 0 && (
          <p class="hint" style="margin-top:0" data-testid="journal-streak">
            {count(streakDays, 'день', 'дня', 'дней')} подряд
          </p>
        )}
        <p class="hint" style="margin-top:0">{T.journalHint}</p>
        <textarea
          ref={area} rows={8} data-testid="journal-input" placeholder={T.journalPlaceholder}
          value={text}
          onFocus={() => setRunning(true)}
          onInput={(e) => setText((e.target as HTMLTextAreaElement).value)}
        />
        <div class="row" style="margin-top:10px">
          <span class="badge" data-testid="journal-timer">{mmss(seconds)}</span>
          <button class="btn ghost" onClick={() => setRunning((r) => !r)}>
            {running ? T.stagePause : T.journalStart}
          </button>
          <button
            class="btn grow" data-testid="journal-save" disabled={!text.trim()}
            onClick={() => {
              onSave(text, seconds)
              setText(''); setSeconds(0); setRunning(false)
            }}
          >
            {T.journalSave}
          </button>
        </div>
        <button class="btn ghost" data-testid="journal-harvest" onClick={harvestSelection}>
          {T.journalHarvest}
        </button>
        <p class="hint">{T.journalHarvestHint}</p>
      </div>

      {entries.length === 0 ? (
        <p class="empty">{T.journalEmpty}</p>
      ) : (
        <ul class="list">
          {entries.map((e) => (
            <li key={e.id} class="item column" data-testid="journal-entry">
              <div class="row" style="width:100%">
                <div class="grow">
                  <div class="sub">
                    {new Date(e.dayMillis).toLocaleDateString('ru-RU')} ·{' '}
                    {Math.round(e.durationSec / 60)} {T.journalMinutes}
                  </div>
                </div>
                <button class="btn danger" onClick={() => onDelete(e.id)}>{T.topicDelete}</button>
              </div>
              <p class="entry-text">{e.text}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
