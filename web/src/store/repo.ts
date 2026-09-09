import type { Clock, Id, SyncMeta } from '../domain/identity'
import { generateId, isDeleted } from '../domain/identity'
import type { Attitude, Bit, BitStatus, Punch, PunchTechnique, Topic } from '../domain/domain'
import { EMPTY_ELEMENTS, assertPassionScore } from '../domain/domain'
import { deservedStatus } from '../domain/lifecycle'
import type { MutationSink } from './sink'
import type { BitPerformance } from '../domain/domain'
import { get, getAll, put, type StoreName } from './db'

/**
 * Доступ к материалу. Возвращает предыдущее состояние записи там, где оно
 * нужно для отмены: кнопка «отменить» обязана уметь вернуть ровно то, что было,
 * а не пересобрать похожее.
 */
export class Repo {
  constructor(
    private readonly db: IDBDatabase,
    private readonly sink: MutationSink,
    private readonly clock: Clock,
  ) {}

  // --- темы -------------------------------------------------------------

  async topics(): Promise<Topic[]> {
    const rows = await getAll<Topic>(this.db, 'topics')
    return rows.filter((t) => !isDeleted(t.meta)).sort((a, b) => a.title.localeCompare(b.title, 'ru'))
  }

  async addTopic(title: string, passionScore = 0): Promise<Topic> {
    assertPassionScore(passionScore)
    const topic: Topic = {
      id: generateId(this.clock),
      title: title.trim(),
      passionScore,
      isCore: false,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'topics', topic)
    return topic
  }

  async deleteTopic(id: Id): Promise<Topic | null> {
    const prev = await get<Topic>(this.db, 'topics', id)
    if (!prev) return null
    await put(this.db, 'topics', { ...prev, meta: this.sink.tombstone() })
    return prev
  }

  // --- шутки ------------------------------------------------------------

  async bits(): Promise<Bit[]> {
    const rows = await getAll<Bit>(this.db, 'bits')
    return rows.filter((b) => !isDeleted(b.meta)).sort((a, b) => b.meta.updatedAt - a.meta.updatedAt)
  }

  async bit(id: Id): Promise<Bit | undefined> {
    return get<Bit>(this.db, 'bits', id)
  }

  async addBit(title: string, topicId: Id | null = null): Promise<Bit> {
    const bit: Bit = {
      id: generateId(this.clock),
      topicId,
      title: title.trim(),
      status: 'SEED',
      attitude: null,
      elements: EMPTY_ELEMENTS,
      durationSec: null,
      meta: this.sink.stamp(),
    }
    await put(this.db, 'bits', bit)
    return bit
  }

  /** Отметки зала по одной шутке. Нужны, чтобы правка не роняла её статус. */
  private async performancesFor(bitId: Id): Promise<BitPerformance[]> {
    const all = await getAll<BitPerformance>(this.db, 'performances')
    return all.filter((p) => p.bitId === bitId && !isDeleted(p.meta))
  }

  /**
   * Единственный путь изменения шутки. Статус пересчитывается здесь, а не в UI:
   * иначе «Мой акт» наполнялся бы по ощущениям, а не по содержимому.
   */
  private async update(id: Id, change: (b: Bit) => Bit): Promise<Bit | null> {
    const prev = await get<Bit>(this.db, 'bits', id)
    if (!prev) return null
    const changed = change(prev)
    // История зала обязательно участвует в пересчёте. Без неё правка опечатки
    // в обкатанной шутке роняла её обратно в черновик — и материал, уже
    // проверенный на сцене, исчезал из кандидатов в сет.
    const withStatus: Bit = {
      ...changed,
      status: deservedStatus(changed, await this.performancesFor(id)),
      meta: this.sink.stamp(),
    }
    await put(this.db, 'bits', withStatus)
    return prev
  }

  /**
   * Пересчёт статуса по свежей истории зала. Вызывается после отметки:
   * связка «выступление → отметки → пересборка акта» — это и есть смысл
   * всего приложения, и держаться она должна на данных, а не на ручном труде.
   */
  async refreshStatus(bitId: Id): Promise<void> {
    const bit = await get<Bit>(this.db, 'bits', bitId)
    if (!bit) return
    const status = deservedStatus(bit, await this.performancesFor(bitId))
    if (status === bit.status) return
    await put(this.db, 'bits', { ...bit, status, meta: this.sink.stamp() })
  }

  setTitle(id: Id, title: string) { return this.update(id, (b) => ({ ...b, title: title.trim() })) }

  setAttitude(id: Id, attitude: Attitude | null) {
    return this.update(id, (b) => ({ ...b, attitude }))
  }

  setPremise(id: Id, premise: string) {
    return this.update(id, (b) => ({
      ...b,
      elements: { ...b.elements, premise: premise.trim() || null },
    }))
  }

  setSetup(id: Id, setup: string) {
    return this.update(id, (b) => ({
      ...b,
      elements: { ...b.elements, setup: setup.trim() || null },
    }))
  }

  setPunch(id: Id, text: string, technique: PunchTechnique) {
    const punch: Punch | null = text.trim() ? { text: text.trim(), technique } : null
    return this.update(id, (b) => ({ ...b, elements: { ...b.elements, punch } }))
  }

  setActOut(id: Id, text: string, hasSpaceWork: boolean) {
    return this.update(id, (b) => ({
      ...b,
      elements: {
        ...b.elements,
        actOut: text.trim() ? { text: text.trim(), hasSpaceWork, audioHash: null } : null,
      },
    }))
  }

  setTags(id: Id, tags: readonly string[]) {
    return this.update(id, (b) => ({ ...b, elements: { ...b.elements, tags: [...tags] } }))
  }

  /** Хронометраж шутки. Без него сет-лист не считается, а значит и не нужен. */
  setDuration(id: Id, durationSec: number | null) {
    return this.update(id, (b) => ({ ...b, durationSec }))
  }

  setStatus(id: Id, status: BitStatus) {
    return this.update(id, (b) => ({ ...b, status }))
  }

  async deleteBit(id: Id): Promise<Bit | null> {
    const prev = await get<Bit>(this.db, 'bits', id)
    if (!prev) return null
    await put(this.db, 'bits', { ...prev, meta: this.sink.tombstone() })
    return prev
  }

  /**
   * Возврат записи к прежнему виду.
   *
   * Время, часы и устройство проставляются заново — отмена это тоже изменение,
   * и при слиянии она обязана победить то, что отменяет. А вот признак
   * удаления берётся из самой записи: отмена создания записывает надгробие,
   * и если брать deletedAt из свежего штампа, где он всегда пуст, надгробие
   * стирается и отменённая запись возвращается живой.
   */
  async restore(store: StoreName, row: { id: Id; meta: SyncMeta }): Promise<void> {
    const stamp = this.sink.stamp()
    await put(this.db, store, { ...row, meta: { ...stamp, deletedAt: row.meta.deletedAt } })
  }
}
