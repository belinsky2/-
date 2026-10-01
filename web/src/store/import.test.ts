import { describe, expect, it } from 'vitest'
import 'fake-indexeddb/auto'
import { buildVault, importVault, parseVault } from './backup'
import { get, openDb, put } from './db'
import { MutationSink } from './sink'
import { MaterialStore } from './material'
import type { Topic } from '../domain/domain'

let t = 1_700_000_000_000
const clock = () => t++
const fresh = async () => openDb(`imp-${Math.random()}`)

describe('восстановление из архива', () => {
  it('старый архив не затирает более свежую правку', async () => {
    const db = await fresh()
    const sink = new MutationSink(clock, 'phone')
    const repo = new MaterialStore(db, sink, clock)

    const topic = await repo.addTopic('Старое название')
    const oldVault = await buildVault(db, 'phone', sink.current(), clock())

    // После архива тема переименована — это свежая правка.
    await put(db, 'topics', { ...topic, title: 'Новое название', meta: sink.stamp() })

    const report = await importVault(db, sink, JSON.parse(JSON.stringify(oldVault)))
    expect(report.ok).toBe(true)
    expect((await get<Topic>(db, 'topics', topic.id))!.title).toBe('Новое название')
  })

  it('более свежая запись из архива побеждает местную', async () => {
    const db = await fresh()
    const sink = new MutationSink(clock, 'phone')
    const repo = new MaterialStore(db, sink, clock)
    const topic = await repo.addTopic('Было')

    const other = { ...topic, title: 'Стало на другом устройстве',
      meta: { ...topic.meta, lamport: topic.meta.lamport + 100, deviceId: 'mac' } }
    const vault = { format: 1, app: 'punchline', exportedAt: clock(), deviceId: 'mac', lamport: 999,
      data: { topics: [other] } }

    await importVault(db, sink, vault)
    expect((await get<Topic>(db, 'topics', topic.id))!.title).toBe('Стало на другом устройстве')
  })

  it('новые записи из архива добавляются', async () => {
    const db = await fresh()
    const sink = new MutationSink(clock, 'phone')
    const incoming: Topic = { id: 'x', title: 'Из архива', passionScore: 0, isCore: false,
      meta: { updatedAt: 1, lamport: 5, deviceId: 'mac', deletedAt: null } }
    const report = await importVault(db, sink,
      { format: 1, app: 'punchline', exportedAt: 1, deviceId: 'mac', lamport: 5, data: { topics: [incoming] } })
    expect(report.ok && report.added).toBe(1)
    expect((await get<Topic>(db, 'topics', 'x'))!.title).toBe('Из архива')
  })

  it('неизвестный раздел архива пропускается, а не обрывает импорт на середине', async () => {
    const db = await fresh()
    const sink = new MutationSink(clock, 'phone')
    const incoming: Topic = { id: 'y', title: 'Тема', passionScore: 0, isCore: false,
      meta: { updatedAt: 1, lamport: 1, deviceId: 'mac', deletedAt: null } }
    const report = await importVault(db, sink, { format: 1, app: 'punchline', exportedAt: 1,
      deviceId: 'mac', lamport: 1, data: { fromTheFuture: [{ id: 'z' }], topics: [incoming] } })
    expect(report.ok).toBe(true)
    expect(await get<Topic>(db, 'topics', 'y')).toBeDefined()
  })

  it('после импорта часы устройства обгоняют всё привезённое', async () => {
    const db = await fresh()
    const sink = new MutationSink(clock, 'phone')
    await importVault(db, sink, { format: 1, app: 'punchline', exportedAt: 1, deviceId: 'mac',
      lamport: 1, data: { topics: [{ id: 'q', title: 'т', passionScore: 0, isCore: false,
        meta: { updatedAt: 1, lamport: 777, deviceId: 'mac', deletedAt: null } }] } })
    expect(sink.current()).toBeGreaterThan(777)
  })

  it('битый файл даёт понятный отказ, а не исключение', () => {
    const r = parseVault('{ это не json')
    expect(r.ok).toBe(false)
  })
})
