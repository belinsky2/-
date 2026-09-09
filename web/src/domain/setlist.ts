import type { Bit, SetList, SetListItem } from './domain'
import type { Id } from './identity'

/** Замечание к сет-листу. Блокирующих среди них нет — сцена важнее правил. */
export type SetListIssue =
  /** Каллбэк стоит раньше шутки, на которую ссылается: зал не поймёт отсылку. */
  | { readonly kind: 'CallbackBeforeSource'; readonly itemId: Id; readonly sourceBitId: Id }
  /** Две подряд шутки на одну тему — сет звучит однообразно. */
  | { readonly kind: 'SameTopicInARow'; readonly firstItemId: Id; readonly secondItemId: Id; readonly topicId: Id }
  /** Закрывать надо лучшим, что есть. */
  | { readonly kind: 'WeakCloser'; readonly itemId: Id; readonly betterBitId: Id }
  /** Не уложиться в отведённое время — самый частый способ испортить выступление. */
  | { readonly kind: 'OverTime'; readonly plannedSec: number; readonly targetSec: number }
  /** Сет заметно короче заявленного. */
  | { readonly kind: 'UnderTime'; readonly plannedSec: number; readonly targetSec: number }

/** Насколько сет может отклониться от цели, прежде чем это стоит упоминания. */
export const TOLERANCE_RATIO = 0.1

export function plannedDuration(
  items: readonly SetListItem[],
  bitsById: ReadonlyMap<Id, Bit>,
): number {
  return items.reduce(
    (sum, i) => sum + (i.plannedDurationSec ?? bitsById.get(i.bitId)?.durationSec ?? 0),
    0,
  )
}

export function validateSetList(
  setList: SetList,
  bitsById: ReadonlyMap<Id, Bit>,
  averageScoreByBit: ReadonlyMap<Id, number> = new Map(),
): SetListIssue[] {
  const issues: SetListIssue[] = []
  const ordered = [...setList.items].sort((a, b) => a.order - b.order)

  // Каллбэки
  const positionOfBit = new Map<Id, number>()
  ordered.forEach((item, i) => {
    if (!positionOfBit.has(item.bitId)) positionOfBit.set(item.bitId, i)
  })
  ordered.forEach((item, index) => {
    const source = bitsById.get(item.bitId)?.elements.callbackTo
    if (!source) return
    const at = positionOfBit.get(source)
    if (at === undefined || at >= index) {
      issues.push({ kind: 'CallbackBeforeSource', itemId: item.id, sourceBitId: source })
    }
  })

  // Две подряд на одну тему
  for (let i = 0; i + 1 < ordered.length; i++) {
    const a = ordered[i]!
    const b = ordered[i + 1]!
    const topicA = bitsById.get(a.bitId)?.topicId
    const topicB = bitsById.get(b.bitId)?.topicId
    if (topicA && topicA === topicB) {
      issues.push({ kind: 'SameTopicInARow', firstItemId: a.id, secondItemId: b.id, topicId: topicA })
    }
  }

  // Закрывать надо лучшим
  if (averageScoreByBit.size > 0 && ordered.length > 0) {
    const closer = [...ordered].reverse().find((i) => i.role === 'CLOSER') ?? ordered[ordered.length - 1]!
    const closerScore = averageScoreByBit.get(closer.bitId)
    if (closerScore !== undefined) {
      let best: { item: SetListItem; score: number } | null = null
      for (const item of ordered) {
        const s = averageScoreByBit.get(item.bitId)
        if (s !== undefined && (best === null || s > best.score)) best = { item, score: s }
      }
      if (best && best.score > closerScore) {
        issues.push({ kind: 'WeakCloser', itemId: closer.id, betterBitId: best.item.bitId })
      }
    }
  }

  // Хронометраж
  if (ordered.length > 0) {
    const planned = plannedDuration(ordered, bitsById)
    const tolerance = Math.trunc(setList.targetDurationSec * TOLERANCE_RATIO)
    if (planned > setList.targetDurationSec + tolerance) {
      issues.push({ kind: 'OverTime', plannedSec: planned, targetSec: setList.targetDurationSec })
    } else if (planned < setList.targetDurationSec - tolerance) {
      issues.push({ kind: 'UnderTime', plannedSec: planned, targetSec: setList.targetDurationSec })
    }
  }

  return issues
}
