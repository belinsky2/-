import type { Clock, Id, SyncMeta } from '../domain/identity'
import { generateId, isDeleted } from '../domain/identity'
import type { MutationSink } from './sink'
import { get, getAll, put } from './db'
import type { ExerciseRecord, JournalEntry, Settings } from '../domain/domain'
import type { Change } from './change'

const alive = <T extends { meta: SyncMeta }>(rows: T[]) => rows.filter((r) => !isDeleted(r.meta))

/**
 * Ежедневная практика: утренние страницы, упражнения из книги и личные
 * настройки (цель по таймингу, комедийная цель).
 */
export class PracticeStore {
  constructor(
    private readonly db: IDBDatabase,
    private readonly sink: MutationSink,
    private readonly clock: Clock,
  ) {}

  // --- дневник ----------------------------------------------------------

  async journal(): Promise<JournalEntry[]> {
    const rows = await getAll<JournalEntry>(this.db, 'journal')
    return alive(rows).sort((a, b) => b.dayMillis - a.dayMillis)
  }

  async addJournalEntry(text: string, durationSec: number): Promise<JournalEntry> {
    const e: JournalEntry = {
      id: generateId(this.clock),
      dayMillis: this.clock(),
      text,
      durationSec,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'journal', e)
    return e
  }

  async deleteJournalEntry(id: Id): Promise<JournalEntry | null> {
    const prev = await get<JournalEntry>(this.db, 'journal', id)
    if (!prev) return null
    await put(this.db, 'journal', { ...prev, meta: this.sink.tombstone() })
    return prev
  }

  // --- упражнения -------------------------------------------------------

  async exercises(): Promise<ExerciseRecord[]> {
    return alive(await getAll<ExerciseRecord>(this.db, 'exercises'))
  }

  async toggleExercise(number: number, done: boolean): Promise<Change<ExerciseRecord>> {
    const all = await getAll<ExerciseRecord>(this.db, 'exercises')
    const prev = all.find((e) => e.number === number)
    const row: ExerciseRecord = {
      id: prev?.id ?? generateId(this.clock),
      number,
      done,
      note: prev?.note ?? '',
      meta: this.sink.stamp(),
    }
    await put(this.db, 'exercises', row)
    return { prev: prev ?? null, row }
  }

  async setExerciseNote(number: number, note: string): Promise<Change<ExerciseRecord>> {
    const all = await getAll<ExerciseRecord>(this.db, 'exercises')
    const prev = all.find((e) => e.number === number)
    const row: ExerciseRecord = {
      id: prev?.id ?? generateId(this.clock),
      number,
      done: prev?.done ?? false,
      note,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'exercises', row)
    return { prev: prev ?? null, row }
  }

  // --- настройки --------------------------------------------------------

  async settings(): Promise<Settings> {
    const row = await get<Settings>(this.db, 'settings', 'settings')
    // Значения по умолчанию — не запись, а её отсутствие. Штамп здесь
    // двигал логические часы при каждом перечитывании экрана.
    return row ?? {
      id: 'settings',
      goalMinutes: 5,
      comedyVision: '',
      meta: { updatedAt: 0, lamport: 0, deviceId: '', deletedAt: null },
    }
  }

  async saveSettings(patch: Partial<Pick<Settings, 'goalMinutes' | 'comedyVision'>>): Promise<Settings> {
    const cur = await this.settings()
    const next: Settings = { ...cur, ...patch, meta: this.sink.stamp() }
    await put(this.db, 'settings', next)
    return cur
  }
}
