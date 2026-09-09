import type { Clock, Id } from '../domain/identity'
import type { SyncMeta } from '../domain/identity'
import { generateId, isDeleted } from '../domain/identity'
import type {
  AudioClip, Bit, BitPerformance, BitVersion, ExerciseRecord, Gig, GigType,
  JournalEntry, LaughResult, SetList, SetListItem, SetListRole, Settings,
} from '../domain/domain'
import type { MutationSink } from './sink'
import { get, getAll, put } from './db'

const DAY = 24 * 60 * 60 * 1000
const alive = <T extends { meta: SyncMeta }>(rows: T[]) => rows.filter((r) => !isDeleted(r.meta))

/**
 * Вторая половина хранилища: сеты, выступления, дневник, упражнения.
 *
 * Вынесено отдельным классом от работы с шутками не по слоям, а по размеру:
 * один файл на всё стал бы тем местом, куда никто не хочет заглядывать.
 */
export class Repo2 {
  constructor(
    private readonly db: IDBDatabase,
    private readonly sink: MutationSink,
    private readonly clock: Clock,
  ) {}

  // --- сет-листы --------------------------------------------------------

  async setLists(): Promise<SetList[]> {
    const rows = await getAll<SetList>(this.db, 'setLists')
    return alive(rows).sort((a, b) => b.meta.updatedAt - a.meta.updatedAt)
  }

  async setList(id: Id): Promise<SetList | undefined> {
    return get<SetList>(this.db, 'setLists', id)
  }

  async addSetList(title: string, targetDurationSec: number): Promise<SetList> {
    const s: SetList = {
      id: generateId(this.clock),
      title: title.trim(),
      targetDurationSec,
      items: [],
      meta: this.sink.stamp(),
    }
    await put(this.db, 'setLists', s)
    return s
  }

  private async updateSetList(id: Id, change: (s: SetList) => SetList): Promise<SetList | null> {
    const prev = await get<SetList>(this.db, 'setLists', id)
    if (!prev) return null
    await put(this.db, 'setLists', { ...change(prev), meta: this.sink.stamp() })
    return prev
  }

  addToSetList(id: Id, bitId: Id, plannedDurationSec: number | null) {
    return this.updateSetList(id, (s) => {
      const item: SetListItem = {
        id: generateId(this.clock),
        bitId,
        order: s.items.length,
        role: s.items.length === 0 ? 'OPENER' : 'BODY',
        plannedDurationSec,
      }
      return { ...s, items: [...s.items, item] }
    })
  }

  removeFromSetList(id: Id, itemId: Id) {
    return this.updateSetList(id, (s) => ({
      ...s,
      items: s.items.filter((i) => i.id !== itemId).map((i, n) => ({ ...i, order: n })),
    }))
  }

  setItemRole(id: Id, itemId: Id, role: SetListRole) {
    return this.updateSetList(id, (s) => ({
      ...s,
      items: s.items.map((i) => (i.id === itemId ? { ...i, role } : i)),
    }))
  }

  /** Перестановка на шаг. Тащить пальцем по списку на телефоне неудобнее, чем два раза нажать. */
  moveItem(id: Id, itemId: Id, delta: -1 | 1) {
    return this.updateSetList(id, (s) => {
      const items = [...s.items].sort((a, b) => a.order - b.order)
      const at = items.findIndex((i) => i.id === itemId)
      const to = at + delta
      if (at < 0 || to < 0 || to >= items.length) return s
      const moved = items[at]!
      items[at] = items[to]!
      items[to] = moved
      return { ...s, items: items.map((i, n) => ({ ...i, order: n })) }
    })
  }

  setTargetDuration(id: Id, sec: number) {
    return this.updateSetList(id, (s) => ({ ...s, targetDurationSec: sec }))
  }

  async deleteSetList(id: Id): Promise<SetList | null> {
    const prev = await get<SetList>(this.db, 'setLists', id)
    if (!prev) return null
    await put(this.db, 'setLists', { ...prev, meta: this.sink.tombstone() })
    return prev
  }

  // --- выступления ------------------------------------------------------

  async gigs(): Promise<Gig[]> {
    const rows = await getAll<Gig>(this.db, 'gigs')
    return alive(rows).sort((a, b) => b.dateMillis - a.dateMillis)
  }

  async addGig(setListId: Id | null, type: GigType, venue: string): Promise<Gig> {
    const g: Gig = {
      id: generateId(this.clock),
      setListId,
      type,
      venue: venue.trim(),
      dateMillis: this.clock(),
      actualDurationSec: null,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'gigs', g)
    return g
  }

  async setGigDuration(id: Id, sec: number): Promise<Gig | null> {
    const prev = await get<Gig>(this.db, 'gigs', id)
    if (!prev) return null
    await put(this.db, 'gigs', { ...prev, actualDurationSec: sec, meta: this.sink.stamp() })
    return prev
  }

  async deleteGig(id: Id): Promise<Gig | null> {
    const prev = await get<Gig>(this.db, 'gigs', id)
    if (!prev) return null
    await put(this.db, 'gigs', { ...prev, meta: this.sink.tombstone() })
    return prev
  }

  gigsLast30Days(gigs: readonly Gig[]): number {
    const since = this.clock() - 30 * DAY
    return gigs.filter((g) => g.dateMillis >= since).length
  }

  // --- отметки зала -----------------------------------------------------

  async performances(): Promise<BitPerformance[]> {
    return alive(await getAll<BitPerformance>(this.db, 'performances'))
  }

  /**
   * Отметка реакции зала. На одну шутку в одном выступлении — одна отметка:
   * повторный тап исправляет ошибку, а не добавляет вторую строку.
   */
  async mark(gigId: Id, bitId: Id, result: LaughResult): Promise<BitPerformance | null> {
    const all = await getAll<BitPerformance>(this.db, 'performances')
    const prev = all.find((p) => p.gigId === gigId && p.bitId === bitId && !isDeleted(p.meta))
    const row: BitPerformance = {
      id: prev?.id ?? generateId(this.clock),
      gigId,
      bitId,
      result,
      note: prev?.note ?? null,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'performances', row)
    return prev ?? null
  }

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

  async toggleExercise(number: number, done: boolean): Promise<ExerciseRecord | null> {
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
    return prev ?? null
  }

  async setExerciseNote(number: number, note: string): Promise<ExerciseRecord | null> {
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
    return prev ?? null
  }

  // --- версии шутки -----------------------------------------------------

  async versions(bitId: Id): Promise<BitVersion[]> {
    const rows = await getAll<BitVersion>(this.db, 'versions')
    return rows.filter((v) => v.bitId === bitId).sort((a, b) => b.takenAt - a.takenAt)
  }

  /**
   * Снимок перед изменением, но не чаще раза в пять минут: иначе история
   * превращается в посимвольный лог и в ней нельзя ничего найти.
   */
  async snapshot(bit: Bit): Promise<void> {
    const now = this.clock()
    const last = (await this.versions(bit.id))[0]
    if (last && now - last.takenAt < 5 * 60 * 1000) return
    const v: BitVersion = {
      id: generateId(this.clock),
      bitId: bit.id,
      title: bit.title,
      attitude: bit.attitude,
      elements: bit.elements,
      status: bit.status,
      takenAt: now,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'versions', v)
  }

  // --- аудио ------------------------------------------------------------

  async audioFor(bitId: Id | null, gigId: Id | null): Promise<AudioClip[]> {
    const rows = await getAll<AudioClip>(this.db, 'audio')
    return alive(rows)
      .filter((a) => (bitId ? a.bitId === bitId : true) && (gigId ? a.gigId === gigId : true))
      .sort((a, b) => b.meta.updatedAt - a.meta.updatedAt)
  }

  async addAudio(
    bytes: Blob,
    mimeType: string,
    durationSec: number,
    bitId: Id | null,
    gigId: Id | null,
  ): Promise<AudioClip> {
    const clip: AudioClip = {
      id: generateId(this.clock),
      bitId,
      gigId,
      mimeType,
      durationSec,
      bytes,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'audio', clip)
    return clip
  }

  async deleteAudio(id: Id): Promise<AudioClip | null> {
    const prev = await get<AudioClip>(this.db, 'audio', id)
    if (!prev) return null
    await put(this.db, 'audio', { ...prev, meta: this.sink.tombstone() })
    return prev
  }

  // --- настройки --------------------------------------------------------

  async settings(): Promise<Settings> {
    const row = await get<Settings>(this.db, 'settings', 'settings')
    return row ?? {
      id: 'settings',
      goalMinutes: 5,
      comedyVision: '',
      meta: this.sink.stamp(),
    }
  }

  async saveSettings(patch: Partial<Pick<Settings, 'goalMinutes' | 'comedyVision'>>): Promise<Settings> {
    const cur = await this.settings()
    const next: Settings = { ...cur, ...patch, meta: this.sink.stamp() }
    await put(this.db, 'settings', next)
    return cur
  }
}
