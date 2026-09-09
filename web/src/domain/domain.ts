import type { Id, SyncMeta } from './identity'

/**
 * Отношение к теме — то, с чего по Картер начинается шутка. Именно оно
 * превращает тему в премису: «самое [attitude] в [теме] — это...».
 */
export const ATTITUDES = ['HARD', 'WEIRD', 'SCARY', 'STUPID'] as const
export type Attitude = (typeof ATTITUDES)[number]

/** Техника, которой сделан панч. */
export const PUNCH_TECHNIQUES = ['MIX', 'TURN', 'LIST_OF_THREE', 'SELF_MOCKING', 'OTHER'] as const
export type PunchTechnique = (typeof PUNCH_TECHNIQUES)[number]

/**
 * Состояние шутки. Цифровой эквивалент перекладывания листка из секции
 * «Jokes in Progress» в секцию «My Act».
 *
 * Состояния «стоит в сет-листе» здесь нет намеренно: оно выводится из связи
 * с сет-листом, а хранимая копия связи неизбежно с ней разойдётся.
 */
export const BIT_STATUSES = [
  'SEED', 'PREMISE', 'DRAFT', 'TESTED', 'POLISHED', 'PARKED', 'RETIRED',
] as const
export type BitStatus = (typeof BIT_STATUSES)[number]

/** Реакция зала на конкретную шутку на конкретном выступлении. */
export const LAUGH_RESULTS = ['SILENCE', 'CHUCKLE', 'LAUGH', 'BIG_LAUGH', 'APPLAUSE_BREAK'] as const
export type LaughResult = (typeof LAUGH_RESULTS)[number]

const LAUGH_SCORE: Record<LaughResult, number> = {
  SILENCE: 0, CHUCKLE: 1, LAUGH: 2, BIG_LAUGH: 3, APPLAUSE_BREAK: 4,
}

export function laughScore(r: LaughResult): number {
  return LAUGH_SCORE[r]
}

/** Роль номера в сет-листе. */
export const SETLIST_ROLES = ['OPENER', 'BODY', 'CLOSER', 'CALLBACK'] as const
export type SetListRole = (typeof SETLIST_ROLES)[number]

/** Тип выхода: от репетиции дома до платного концерта. */
export const GIG_TYPES = ['REHEARSAL', 'OPEN_MIC', 'SHOWCASE', 'PAID'] as const
export type GigType = (typeof GIG_TYPES)[number]

/** Тема, из которой растут шутки. */
export interface Topic {
  readonly id: Id
  readonly title: string
  readonly passionScore: number
  readonly isCore: boolean
  readonly meta: SyncMeta
}

/** Панч: текст плюс техника, которой он сделан. */
export interface Punch {
  readonly text: string
  readonly technique: PunchTechnique
}

/** Act-out: шутку надо сыграть, а не рассказать. */
export interface ActOut {
  readonly text: string
  readonly hasSpaceWork: boolean
  readonly audioHash: string | null
}

/** Структурированное тело шутки. */
export interface BitElements {
  readonly premise: string | null
  readonly setup: string | null
  readonly punch: Punch | null
  readonly actOut: ActOut | null
  readonly tags: readonly string[]
  readonly callbackTo: Id | null
}

export const EMPTY_ELEMENTS: BitElements = {
  premise: null, setup: null, punch: null, actOut: null, tags: [], callbackTo: null,
}

/** Единица материала. */
export interface Bit {
  readonly id: Id
  readonly topicId: Id | null
  readonly title: string
  readonly status: BitStatus
  readonly attitude: Attitude | null
  readonly elements: BitElements
  readonly durationSec: number | null
  readonly meta: SyncMeta
}

/** Позиция в сет-листе. */
export interface SetListItem {
  readonly id: Id
  readonly bitId: Id
  readonly order: number
  readonly role: SetListRole
  readonly plannedDurationSec: number | null
}

/** Сет-лист под конкретный хронометраж. */
export interface SetList {
  readonly id: Id
  readonly title: string
  readonly targetDurationSec: number
  readonly items: readonly SetListItem[]
  readonly meta: SyncMeta
}

/** Выступление или прогон. */
export interface Gig {
  readonly id: Id
  readonly setListId: Id | null
  readonly type: GigType
  readonly venue: string
  readonly dateMillis: number
  readonly actualDurationSec: number | null
  readonly meta: SyncMeta
}

/** Как конкретная шутка отработала на конкретном выступлении. */
export interface BitPerformance {
  readonly id: Id
  readonly gigId: Id
  readonly bitId: Id
  readonly result: LaughResult
  readonly note: string | null
  readonly meta: SyncMeta
}

/** Проверка, которую в Kotlin делал init-блок Topic. */
export function assertPassionScore(score: number): void {
  if (!Number.isInteger(score) || score < 0 || score > 10) {
    throw new RangeError(`passionScore вне 0..10: ${score}`)
  }
}

/** Запись утренних страниц. Свободное письмо, из которого потом растут зёрна. */
export interface JournalEntry {
  readonly id: Id
  readonly dayMillis: number
  readonly text: string
  readonly durationSec: number
  readonly meta: SyncMeta
}

/** Отметка о пройденном упражнении из тетради. */
export interface ExerciseRecord {
  readonly id: Id
  readonly number: number
  readonly done: boolean
  readonly note: string
  readonly meta: SyncMeta
}

/** Снимок шутки до изменения: проигравшая при слиянии версия не должна пропасть. */
export interface BitVersion {
  readonly id: Id
  readonly bitId: Id
  readonly title: string
  readonly attitude: Attitude | null
  readonly elements: BitElements
  readonly status: BitStatus
  readonly takenAt: number
  readonly meta: SyncMeta
}

/** Аудиозапись: рант, act-out или целое выступление. */
export interface AudioClip {
  readonly id: Id
  readonly bitId: Id | null
  readonly gigId: Id | null
  readonly mimeType: string
  readonly durationSec: number
  readonly bytes: Blob
  readonly meta: SyncMeta
}

/** Настройки: цель по времени акта и комедийная цель из упражнения 2. */
export interface Settings {
  readonly id: 'settings'
  readonly goalMinutes: number
  readonly comedyVision: string
  readonly meta: SyncMeta
}

export const GOAL_CHOICES = [5, 15, 30, 60] as const
