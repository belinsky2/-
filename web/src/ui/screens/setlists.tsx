import { useState } from 'preact/hooks'
import type { Bit, SetList, SetListRole } from '../../domain/domain'
import { SETLIST_ROLES } from '../../domain/domain'
import type { Id } from '../../domain/identity'
import { plannedDuration, validateSetList, type SetListIssue } from '../../domain/setlist'
import { ROLE_LABEL, T } from '../labels'
import { count } from '../plural'

const TARGETS = [300, 420, 600, 900, 1800, 3600]

export function mmss(sec: number): string {
  const m = Math.floor(Math.abs(sec) / 60)
  const s = Math.abs(sec) % 60
  return `${sec < 0 ? '−' : ''}${m}:${String(s).padStart(2, '0')}`
}

function issueText(i: SetListIssue): string {
  switch (i.kind) {
    case 'CallbackBeforeSource': return T.issueCallback
    case 'SameTopicInARow': return T.issueSameTopic
    case 'WeakCloser': return T.issueWeakCloser
    case 'OverTime': return `${T.issueOverTime}: ${mmss(i.plannedSec)} / ${mmss(i.targetSec)}`
    case 'UnderTime': return `${T.issueUnderTime}: ${mmss(i.plannedSec)} / ${mmss(i.targetSec)}`
  }
}

interface ListProps {
  setLists: readonly SetList[]
  onAdd: (title: string, target: number) => void
  onOpen: (id: Id) => void
}

export function SetListsScreen({ setLists, onAdd, onOpen }: ListProps) {
  const [title, setTitle] = useState('')
  const [target, setTarget] = useState(300)
  const trimmed = title.trim()

  return (
    <div class="scroll" data-screen="setlists">
      <div class="card">
        <h2>{T.setsTitle}</h2>
        <input
          type="text" data-testid="set-title" placeholder={T.setPlaceholder}
          value={title} onInput={(e) => setTitle((e.target as HTMLInputElement).value)}
        />
        <div class="chips" style="margin:12px 0">
          {TARGETS.map((sec) => (
            <button
              key={sec} class={`chip small${target === sec ? ' on' : ''}`}
              data-testid={`target-${sec}`} onClick={() => setTarget(sec)}
            >
              {sec / 60} {T.todayMinutes}
            </button>
          ))}
        </div>
        <button
          class="btn" data-testid="set-add" disabled={!trimmed}
          onClick={() => { onAdd(trimmed, target); setTitle('') }}
        >
          {T.setAdd}
        </button>
      </div>

      {setLists.length === 0 ? (
        <p class="empty">{T.setsEmpty}</p>
      ) : (
        <ul class="list">
          {setLists.map((s) => (
            <li key={s.id} class="item" data-testid="set-item" onClick={() => onOpen(s.id)}>
              <div class="grow">
                <div class="title">{s.title}</div>
                <div class="sub">
                  {mmss(s.targetDurationSec)} · {count(s.items.length, 'шутка', 'шутки', 'шуток')}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

interface EditorProps {
  setList: SetList
  bits: readonly Bit[]
  scores: ReadonlyMap<Id, number>
  onAddBit: (bitId: Id, durationSec: number | null) => void
  onRemove: (itemId: Id) => void
  onRole: (itemId: Id, role: SetListRole) => void
  onMove: (itemId: Id, delta: -1 | 1) => void
  onTarget: (sec: number) => void
  onStage: () => void
  onDelete: () => void
}

export function SetListEditor(
  { setList, bits, scores, onAddBit, onRemove, onRole, onMove, onTarget, onStage, onDelete }: EditorProps,
) {
  const [picking, setPicking] = useState(false)
  const byId = new Map(bits.map((b) => [b.id, b]))
  const ordered = [...setList.items].sort((a, b) => a.order - b.order)
  const planned = plannedDuration(ordered, byId)
  const issues = validateSetList(setList, byId, scores)

  // Кандидаты — то, что уже проверено залом. Сырое в сет не ставят.
  const used = new Set(ordered.map((i) => i.bitId))
  const candidates = bits
    .filter((b) => !used.has(b.id) && (b.status === 'TESTED' || b.status === 'POLISHED'))
    .sort((a, b) => (scores.get(b.id) ?? 0) - (scores.get(a.id) ?? 0))

  return (
    <div class="scroll" data-screen="set-editor">
      <div class="card">
        <h2>{setList.title}</h2>
        <p class="big" data-testid="set-planned">
          {mmss(planned)} <span class="big-unit">{T.todayOf} {mmss(setList.targetDurationSec)}</span>
        </p>
        <div class="bar">
          <div
            class="bar-fill"
            style={`width:${Math.min(100, (planned / Math.max(1, setList.targetDurationSec)) * 100)}%`}
          />
        </div>
        <p class="hint">{T.setTarget}</p>
        <div class="chips">
          {TARGETS.map((sec) => (
            <button
              key={sec} class={`chip small${setList.targetDurationSec === sec ? ' on' : ''}`}
              onClick={() => onTarget(sec)}
            >
              {sec / 60}
            </button>
          ))}
        </div>
      </div>

      {issues.length > 0 && (
        <div class="card warn" data-testid="set-issues">
          <h2>{T.setIssues}</h2>
          <ul class="plain">
            {issues.map((i, n) => <li key={n}>{issueText(i)}</li>)}
          </ul>
        </div>
      )}

      {ordered.length === 0 ? (
        <p class="empty">{T.setEmpty}</p>
      ) : (
        <ul class="list">
          {ordered.map((item, n) => {
            const b = byId.get(item.bitId)
            return (
              <li key={item.id} class="item column" data-testid="set-row">
                <div class="row" style="width:100%">
                  <span class="badge">{n + 1}</span>
                  <div class="grow">
                    <div class="title">{b?.title ?? '—'}</div>
                    <div class="sub">{mmss(item.plannedDurationSec ?? b?.durationSec ?? 0)}</div>
                  </div>
                  <button class="btn ghost" onClick={() => onMove(item.id, -1)}>↑</button>
                  <button class="btn ghost" onClick={() => onMove(item.id, 1)}>↓</button>
                </div>
                <div class="chips" style="margin-top:8px">
                  {SETLIST_ROLES.map((r) => (
                    <button
                      key={r} class={`chip small${item.role === r ? ' on' : ''}`}
                      onClick={() => onRole(item.id, r)}
                    >
                      {ROLE_LABEL[r]}
                    </button>
                  ))}
                  <button class="chip small danger-chip" onClick={() => onRemove(item.id)}>×</button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      <div style="padding:12px">
        <button class="btn" data-testid="set-add-bit" onClick={() => setPicking(!picking)}>
          {T.setAddBit}
        </button>
      </div>

      {picking && (
        <div class="card">
          <h2>{T.setCandidates}</h2>
          <p class="hint" style="margin-top:0">{T.setCandidatesHint}</p>
          {candidates.length === 0 ? (
            <p class="hint">{T.setNoCandidates}</p>
          ) : (
            <ul class="list">
              {candidates.map((b) => (
                <li
                  key={b.id} class="item" data-testid="candidate"
                  onClick={() => { onAddBit(b.id, b.durationSec); setPicking(false) }}
                >
                  <div class="grow">
                    <div class="title">{b.title}</div>
                    <div class="sub">
                      {scores.has(b.id) ? `средний ${scores.get(b.id)!.toFixed(1)}` : 'без истории'}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div style="padding:0 12px 24px" class="row">
        <button class="btn grow" data-testid="go-stage" disabled={ordered.length === 0} onClick={onStage}>
          {T.setStage}
        </button>
        <button class="btn danger" onClick={onDelete}>{T.setDelete}</button>
      </div>
    </div>
  )
}
