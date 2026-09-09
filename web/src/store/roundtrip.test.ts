import { beforeEach, describe, expect, it } from 'vitest'
import 'fake-indexeddb/auto'
import { buildVault, inspectVault } from './backup'
import { getAll, openDb, putAll, type StoreName } from './db'
import { MutationSink } from './sink'
import { Repo } from './repo'
import type { Bit, Topic } from '../domain/domain'
import { isDeleted } from '../domain/identity'

/** Смена телефона: выгрузили здесь, залили там, ничего не потеряли. */
async function freshDb(name: string) {
  return openDb(name)
}

let n = 0
const clock = () => 1_700_000_000_000 + n++

describe('перенос материала между устройствами', () => {
  beforeEach(() => { n = 0 })

  it('архив переносит шутки, темы и удаления', async () => {
    const oldPhone = await freshDb(`old-${Math.random()}`)
    const sinkA = new MutationSink(clock, 'phone-a')
    const repoA = new Repo(oldPhone, sinkA, clock)

    const topic = await repoA.addTopic('Лифты')
    const kept = await repoA.addBit('В лифте все смотрят вверх', topic.id)
    await repoA.setAttitude(kept.id, 'HARD')
    await repoA.setPremise(kept.id, 'Самое тяжёлое в лифте — тишина')
    await repoA.setPunch(kept.id, 'Этаж — это очень интересно', 'LIST_OF_THREE')

    const dropped = await repoA.addBit('Лишняя')
    await repoA.deleteBit(dropped.id)

    const vault = await buildVault(oldPhone, 'phone-a', sinkA.current(), clock())
    expect(inspectVault(vault).ok).toBe(true)

    // --- новый телефон ---
    const newPhone = await freshDb(`new-${Math.random()}`)
    const sinkB = new MutationSink(clock, 'phone-b')
    const repoB = new Repo(newPhone, sinkB, clock)

    for (const [store, rows] of Object.entries(vault.data)) {
      await putAll(newPhone, store as StoreName, rows)
      for (const r of rows) sinkB.observe((r as Bit).meta.lamport)
    }

    const bits = await repoB.bits()
    const topics = await repoB.topics()

    expect(topics.map((t) => t.title)).toEqual(['Лифты'])
    expect(bits.map((b) => b.title)).toEqual(['В лифте все смотрят вверх'])
    expect(bits[0]?.elements.punch?.text).toBe('Этаж — это очень интересно')
    expect(bits[0]?.elements.punch?.technique).toBe('LIST_OF_THREE')
    expect(bits[0]?.status).toBe('DRAFT')

    // Надгробие уехало вместе с материалом: иначе удалённая шутка воскресла бы.
    const raw = await getAll<Bit>(newPhone, 'bits')
    expect(raw).toHaveLength(2)
    expect(raw.filter((b) => isDeleted(b.meta))).toHaveLength(1)
  })

  it('отмена создания убирает запись, а не воскрешает её', async () => {
    const db = await freshDb(`undo-${Math.random()}`)
    const sink = new MutationSink(clock, 'phone-a')
    const repo = new Repo(db, sink, clock)

    const topic = await repo.addTopic('Лишняя тема')
    expect(await repo.topics()).toHaveLength(1)

    // Отмена создания — это надгробие поверх свежесозданной записи.
    await repo.restore('topics', { ...topic, meta: { ...topic.meta, deletedAt: topic.meta.updatedAt } })

    expect(await repo.topics()).toHaveLength(0)
    const raw = (await getAll<Topic>(db, 'topics'))[0]!
    expect(isDeleted(raw.meta)).toBe(true)
    // Часы всё равно двигаются вперёд: отмена обязана победить то, что отменяет.
    expect(raw.meta.lamport).toBeGreaterThan(topic.meta.lamport)
  })

  it('отмена правки возвращает прежнее содержимое и не хоронит запись', async () => {
    const db = await freshDb(`undo2-${Math.random()}`)
    const sink = new MutationSink(clock, 'phone-a')
    const repo = new Repo(db, sink, clock)

    const b = await repo.addBit('Шутка')
    const before = await repo.setPunch(b.id, 'первая добивка', 'TURN')
    await repo.setPunch(b.id, 'вторая добивка', 'MIX')

    await repo.restore('bits', before!)

    const restored = (await repo.bits())[0]!
    expect(restored.elements.punch).toBeNull()
    expect(isDeleted(restored.meta)).toBe(false)
  })

  it('логические часы нового устройства обгоняют привезённые', async () => {
    const db = await freshDb(`lam-${Math.random()}`)
    const sink = new MutationSink(clock, 'phone-b')
    sink.observe(500)
    expect(sink.current()).toBe(501)

    const repo = new Repo(db, sink, clock)
    const t = await repo.addTopic('После импорта')
    // Свежая правка обязана победить всё, что приехало в архиве.
    expect(t.meta.lamport).toBeGreaterThan(500)
  })

  it('правка после импорта не проигрывает привезённой записи', async () => {
    const db = await freshDb(`win-${Math.random()}`)
    const sink = new MutationSink(clock, 'phone-b')
    const repo = new Repo(db, sink, clock)

    const foreign: Topic = {
      id: 'topic-x', title: 'Привезённая', passionScore: 0, isCore: false,
      meta: { updatedAt: 9_999_999_999_999, lamport: 42, deviceId: 'phone-a', deletedAt: null },
    }
    await putAll(db, 'topics', [foreign])
    sink.observe(foreign.meta.lamport)

    const prev = await repo.deleteTopic('topic-x')
    expect(prev).not.toBeNull()
    const after = (await getAll<Topic>(db, 'topics'))[0]!
    expect(after.meta.lamport).toBeGreaterThan(42)
    expect(isDeleted(after.meta)).toBe(true)
  })
})
