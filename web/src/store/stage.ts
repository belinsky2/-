import type { Clock, Id, SyncMeta } from '../domain/identity'
import { generateId, isDeleted } from '../domain/identity'
import type { MutationSink } from './sink'
import { get, getAll, put } from './db'
import type { BitPerformance, Gig, GigType, LaughResult, SetList, SetListItem, SetListRole } from '../domain/domain'
import type { Change } from './change'

const DAY = 24 * 60 * 60 * 1000
const alive = <T extends { meta: SyncMeta }>(rows: T[]) => rows.filter((r) => !isDeleted(r.meta))

/**
 * Сцена: сет-листы, выступления и реакция зала на каждую шутку.
 * Всё, что происходит между «собрал сет» и «разобрал выступление».
 */
export class StageStore {
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

  async addGig(
    setListId: Id | null,
    type: GigType,
    venue: string,
    actualDurationSec: number | null = null,
  ): Promise<Gig> {
    const g: Gig = {
      id: generateId(this.clock),
      setListId,
      type,
      venue: venue.trim(),
      dateMillis: this.clock(),
      actualDurationSec,
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
  async mark(gigId: Id, bitId: Id, result: LaughResult): Promise<Change<BitPerformance>> {
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
    return { prev: prev ?? null, row }
  }
}
