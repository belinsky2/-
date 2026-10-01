import type { Clock, Id, SyncMeta } from '../domain/identity'
import { generateId, isDeleted } from '../domain/identity'
import type {
  Attitude, AudioClip, Bit, BitPerformance, BitStatus, BitVersion, Punch, PunchTechnique, Topic,
} from '../domain/domain'
import { EMPTY_ELEMENTS, assertPassionScore } from '../domain/domain'
import { deservedStatus } from '../domain/lifecycle'
import type { MutationSink } from './sink'
import { get, getAll, put } from './db'

const alive = <T extends { meta: SyncMeta }>(rows: T[]) => rows.filter((r) => !isDeleted(r.meta))

/**
 * Материал: темы, шутки, их версии и записи голоса.
 *
 * Методы изменения возвращают прежнее состояние записи: отмена обязана
 * вернуть ровно то, что было, а не пересобрать похожее.
 */
export class MaterialStore {
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

  /** Привязка шутки к теме. Тема без шуток бесполезна, шутка без темы теряется. */
  setTopic(id: Id, topicId: Id | null) {
    return this.update(id, (b) => ({ ...b, topicId }))
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
}
