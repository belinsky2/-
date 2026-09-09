import { useState } from 'preact/hooks'
import type { Bit, BitPerformance, Gig, GigType, LaughResult, SetList } from '../../domain/domain'
import { GIG_TYPES, LAUGH_RESULTS } from '../../domain/domain'
import type { Id } from '../../domain/identity'
import { gigStats } from '../../domain/metrics'
import { GIG_LABEL, LAUGH_LABEL, T } from '../labels'
import { mmss } from './setlists'

interface ListProps {
  gigs: readonly Gig[]
  setLists: readonly SetList[]
  onAdd: (setListId: Id | null, type: GigType, venue: string) => void
  onOpen: (id: Id) => void
}

export function GigsScreen({ gigs, setLists, onAdd, onOpen }: ListProps) {
  const [venue, setVenue] = useState('')
  const [type, setType] = useState<GigType>('OPEN_MIC')
  const [setId, setSetId] = useState<Id | null>(setLists[0]?.id ?? null)
  const trimmed = venue.trim()

  return (
    <div class="card">
      <h2>{T.gigsTitle}</h2>
      <input
        type="text" data-testid="gig-venue" placeholder={T.gigVenue}
        value={venue} onInput={(e) => setVenue((e.target as HTMLInputElement).value)}
      />
      <div class="chips" style="margin:12px 0">
        {GIG_TYPES.map((g) => (
          <button
            key={g} class={`chip small${type === g ? ' on' : ''}`}
            data-testid={`gigtype-${g}`} onClick={() => setType(g)}
          >
            {GIG_LABEL[g]}
          </button>
        ))}
      </div>
      {setLists.length > 0 && (
        <div class="chips" style="margin-bottom:12px">
          {setLists.map((s) => (
            <button
              key={s.id} class={`chip small${setId === s.id ? ' on' : ''}`}
              onClick={() => setSetId(setId === s.id ? null : s.id)}
            >
              {s.title}
            </button>
          ))}
        </div>
      )}
      <button
        class="btn" data-testid="gig-add" disabled={!trimmed}
        onClick={() => { onAdd(setId, type, trimmed); setVenue('') }}
      >
        {T.gigAdd}
      </button>

      {gigs.length === 0 ? (
        <p class="hint">{T.gigsEmpty}</p>
      ) : (
        <ul class="list" style="margin-top:12px">
          {gigs.map((g) => (
            <li key={g.id} class="item" data-testid="gig-item" onClick={() => onOpen(g.id)}>
              <div class="grow">
                <div class="title">{g.venue}</div>
                <div class="sub">
                  {GIG_LABEL[g.type]} · {new Date(g.dateMillis).toLocaleDateString('ru-RU')}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

interface ReviewProps {
  gig: Gig
  setList: SetList | null
  bits: readonly Bit[]
  performances: readonly BitPerformance[]
  onMark: (bitId: Id, result: LaughResult) => void
  onDuration: (sec: number) => void
  onBack: () => void
}

/**
 * Разбор выступления. Упражнение 35 у Картер: считать смех, а не вспоминать
 * ощущение — ощущение врёт в обе стороны.
 */
export function GigReviewScreen({ gig, setList, bits, performances, onMark, onDuration, onBack }: ReviewProps) {
  const byId = new Map(bits.map((b) => [b.id, b]))
  const mine = performances.filter((p) => p.gigId === gig.id)
  const marked = new Map(mine.map((p) => [p.bitId, p.result]))
  const stats = gigStats(mine, gig.actualDurationSec)

  // Если сет-лист привязан и не пуст — идём по нему: порядок на сцене был
  // такой. Если нет (открытый микрофон без заготовки) — показываем весь
  // материал, который мог прозвучать. Иначе отмечать оказывается нечего.
  const fromSet = setList
    ? [...setList.items].sort((a, b) => a.order - b.order).map((i) => byId.get(i.bitId)).filter(Boolean)
    : []
  const rows = fromSet.length > 0
    ? fromSet
    : bits.filter((b) => b.status !== 'SEED' || marked.has(b.id))

  return (
    <div class="scroll" data-screen="review">
      <div class="card">
        <h2>{T.reviewTitle} · {gig.venue}</h2>
        <p class="hint" style="margin-top:0">
          {fromSet.length > 0 ? T.reviewHint : T.reviewFreeHint}
        </p>
        <p class="big" data-testid="review-score">
          {stats.averageScore.toFixed(2)} <span class="big-unit">{T.reviewScore}</span>
        </p>
        {gig.actualDurationSec !== null && (
          <p class="hint">
            {stats.laughsPerMinute.toFixed(1)} {T.reviewLpm} · {mmss(gig.actualDurationSec)}
          </p>
        )}
        <p class="hint">{T.gigDuration}</p>
        <input
          type="text" inputMode="numeric" data-testid="gig-duration"
          value={gig.actualDurationSec === null ? '' : String(Math.round(gig.actualDurationSec / 60))}
          onChange={(e) => {
            const m = Number((e.target as HTMLInputElement).value)
            if (Number.isFinite(m) && m > 0) onDuration(Math.round(m * 60))
          }}
        />
      </div>

      {rows.length === 0 ? (
        <p class="empty">{T.reviewNoBits}</p>
      ) : (
        <ul class="list">
          {rows.map((b) => (
            <li key={b!.id} class="item column" data-testid="review-row">
              <div class="title">{b!.title}</div>
              <div class="chips" style="margin-top:8px">
                {LAUGH_RESULTS.map((r) => (
                  <button
                    key={r}
                    class={`chip small${marked.get(b!.id) === r ? ' on' : ''}`}
                    data-testid={`mark-${r}`}
                    onClick={() => onMark(b!.id, r)}
                  >
                    {LAUGH_LABEL[r]}
                  </button>
                ))}
              </div>
            </li>
          ))}
        </ul>
      )}

      <div style="padding:0 12px 24px">
        <button class="btn ghost" onClick={onBack}>← {T.back}</button>
      </div>
    </div>
  )
}
