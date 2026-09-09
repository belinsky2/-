import type { Bit, BitPerformance, BitStatus } from './domain'
import { laughScore } from './domain'

/**
 * Правила жизненного цикла шутки.
 *
 * Ключевая идея: продвижение вперёд не назначается вручную, а следует из того,
 * что у шутки появилось (премиса, панч) и как она отработала в зале. Иначе
 * раздел «Мой акт» наполняется по ощущениям, а не по данным.
 */

/** Сколько выступлений с результатом не ниже MIN_RESULT нужно для «отшлифована». */
export const PERFORMANCES_TO_POLISH = 2

/** Планка «зашло». Хмык не считается. */
export const MIN_RESULT = 'LAUGH' as const

/** Сколько подряд провалов, прежде чем предложить отложить шутку. */
export const SILENCES_TO_SUGGEST_PARKING = 3

const FORWARD: Record<BitStatus, readonly BitStatus[]> = {
  SEED: ['PREMISE'],
  PREMISE: ['DRAFT'],
  DRAFT: ['TESTED'],
  TESTED: ['POLISHED'],
  POLISHED: [],
  PARKED: [],
  RETIRED: [],
}

const ARCHIVED: readonly BitStatus[] = ['PARKED', 'RETIRED']

const isArchived = (s: BitStatus) => ARCHIVED.includes(s)

/**
 * Разрешён ли переход. Отложить или списать можно с любого состояния,
 * вернуть из архива — тоже: «архив» это состояние, а не помойка.
 */
export function canTransition(from: BitStatus, to: BitStatus): boolean {
  if (from === to) return false
  if (isArchived(to)) return true
  if (isArchived(from)) return true
  return FORWARD[from].includes(to)
}

/** До какого состояния шутка дотягивает по одному только своему содержимому. */
function contentStatus(bit: Bit): BitStatus {
  const e = bit.elements
  const hasPremise = !!e.premise && e.premise.trim() !== '' && bit.attitude !== null
  const hasPunch = !!e.punch && e.punch.text.trim() !== ''
  if (hasPremise && hasPunch) return 'DRAFT'
  if (hasPremise) return 'PREMISE'
  return 'SEED'
}

/**
 * Состояние, которого шутка заслуживает по своему содержимому и истории зала.
 * Никогда не понижает статус сама: решение отложить шутку остаётся за автором.
 */
export function deservedStatus(bit: Bit, performances: readonly BitPerformance[]): BitStatus {
  if (isArchived(bit.status)) return bit.status

  const successes = performances.filter((p) => laughScore(p.result) >= laughScore(MIN_RESULT)).length
  const content = contentStatus(bit)

  if (successes >= PERFORMANCES_TO_POLISH && content === 'DRAFT') return 'POLISHED'
  if (performances.length > 0 && content === 'DRAFT') return 'TESTED'
  return content
}

/** Подсказка по конкретной шутке. Текст формулирует UI, здесь только повод. */
export type BitHint =
  | { readonly kind: 'RewriteOrPark'; readonly silencesInARow: number }
  | { readonly kind: 'MissingActOut' }
  | { readonly kind: 'StuckInDraft'; readonly days: number }
  | { readonly kind: 'UnusedPolished'; readonly days: number }

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000
const STUCK_DRAFT_DAYS = 30
const UNUSED_POLISHED_DAYS = 90

/**
 * Подсказки, которые приложение показывает автору. Именно подсказки:
 * приложение не переписывает материал само.
 */
export function hints(
  bit: Bit,
  performances: readonly BitPerformance[],
  nowMillis: number,
): BitHint[] {
  const out: BitHint[] = []

  // Разбирая выступление, автор отмечает десяток шуток подряд — все они
  // получают одну и ту же миллисекунду. Логические часы разрывают ничью,
  // иначе «свежая серия провалов» определяется порядком в списке.
  const recent = [...performances].sort(
    (a, b) => b.meta.updatedAt - a.meta.updatedAt || b.meta.lamport - a.meta.lamport,
  )
  let leadingSilences = 0
  for (const p of recent) {
    if (p.result !== 'SILENCE') break
    leadingSilences++
  }
  if (leadingSilences >= SILENCES_TO_SUGGEST_PARKING) {
    out.push({ kind: 'RewriteOrPark', silencesInARow: leadingSilences })
  }

  if (bit.status === 'TESTED' && bit.elements.actOut === null) {
    out.push({ kind: 'MissingActOut' })
  }

  const ageDays = Math.floor((nowMillis - bit.meta.updatedAt) / MILLIS_PER_DAY)
  if (bit.status === 'DRAFT' && ageDays >= STUCK_DRAFT_DAYS) {
    out.push({ kind: 'StuckInDraft', days: ageDays })
  }
  if (bit.status === 'POLISHED' && performances.length === 0 && ageDays >= UNUSED_POLISHED_DAYS) {
    out.push({ kind: 'UnusedPolished', days: ageDays })
  }

  return out
}
