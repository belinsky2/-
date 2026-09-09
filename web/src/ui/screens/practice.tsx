import { useState } from 'preact/hooks'
import type { ExerciseRecord } from '../../domain/domain'
import { groupByPart, nextExercise, type Exercise } from '../../store/exercises'
import { PART_LABEL, T } from '../labels'

interface Props {
  exercises: readonly Exercise[]
  records: readonly ExerciseRecord[]
  onToggle: (number: number, done: boolean) => void
  onNote: (number: number, note: string) => void
}

export function PracticeScreen({ exercises, records, onToggle, onNote }: Props) {
  const [open, setOpen] = useState<number | null>(null)
  const byNumber = new Map(records.map((r) => [r.number, r]))
  const done = new Set(records.filter((r) => r.done).map((r) => r.number))
  const next = nextExercise(exercises, done)

  return (
    <div class="scroll" data-screen="practice">
      <div class="card">
        <h2>{T.tabPractice}</h2>
        <p class="big" data-testid="practice-progress">
          {done.size} <span class="big-unit">{T.todayOf} {exercises.length} {T.practiceDone}</span>
        </p>
        <div class="bar"><div class="bar-fill" style={`width:${(done.size / Math.max(1, exercises.length)) * 100}%`} /></div>
        {next && (
          <p class="hint" data-testid="practice-next">
            {T.practiceNext}: №{next.number} — {next.shortTitle}
          </p>
        )}
        <p class="hint">{T.practiceNoText}</p>
      </div>

      {groupByPart(exercises).map(([part, list]) => (
        <div key={part}>
          <h3 class="section">{PART_LABEL[part] ?? part}</h3>
          <ul class="list">
            {list.map((e) => {
              const rec = byNumber.get(e.number)
              const isOpen = open === e.number
              return (
                <li key={e.number} class="item column" data-testid="exercise-item">
                  <div class="row" style="width:100%">
                    <input
                      type="checkbox"
                      data-testid={`exercise-${e.number}`}
                      checked={rec?.done ?? false}
                      onChange={() => onToggle(e.number, !(rec?.done ?? false))}
                    />
                    <div class="grow" onClick={() => setOpen(isOpen ? null : e.number)}>
                      <div class="title">№{e.number} · {e.shortTitle}</div>
                      {e.bookPage !== null && <div class="sub">{T.practicePage} {e.bookPage}</div>}
                    </div>
                  </div>
                  {isOpen && (
                    <div style="width:100%;margin-top:10px">
                      <textarea
                        rows={3} placeholder={T.practiceNote}
                        value={rec?.note ?? ''}
                        onChange={(ev) => onNote(e.number, (ev.target as HTMLTextAreaElement).value)}
                      />
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        </div>
      ))}
    </div>
  )
}
