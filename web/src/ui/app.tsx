import { useCallback, useEffect, useMemo, useRef, useState } from 'preact/hooks'
import type { Attitude, Bit, PunchTechnique, Topic } from '../domain/domain'
import { systemClock, type DeviceId } from '../domain/identity'
import { openDb, putAll, requestPersistence, type StoreName } from '../store/db'
import { MutationSink } from '../store/sink'
import { Repo } from '../store/repo'
import { backupDue, buildVault, downloadVault, inspectVault } from '../store/backup'
import { T, UNDO_LABEL } from './labels'
import { useUndo } from './undo'
import { InboxScreen } from './screens/inbox'
import { TopicsScreen } from './screens/topics'
import { WorkshopScreen } from './screens/workshop'
import { BackupScreen } from './screens/backup'

type Tab = 'inbox' | 'topics' | 'backup'

const DEVICE_KEY = 'punchline.deviceId'
const LAMPORT_KEY = 'punchline.lamport'
const BACKUP_KEY = 'punchline.lastBackupAt'
const CHANGES_KEY = 'punchline.changesSinceBackup'

function deviceId(): DeviceId {
  let v = localStorage.getItem(DEVICE_KEY)
  if (!v) {
    v = crypto.randomUUID()
    localStorage.setItem(DEVICE_KEY, v)
  }
  return v
}

export function App() {
  const [repo, setRepo] = useState<Repo | null>(null)
  const [db, setDb] = useState<IDBDatabase | null>(null)
  const [sink, setSink] = useState<MutationSink | null>(null)
  const [bits, setBits] = useState<Bit[]>([])
  const [topics, setTopics] = useState<Topic[]>([])
  const [tab, setTab] = useState<Tab>('inbox')
  const [openBitId, setOpenBitId] = useState<string | null>(null)
  const [persistent, setPersistent] = useState(false)
  const [lastBackupAt, setLastBackupAt] = useState<number | null>(
    Number(localStorage.getItem(BACKUP_KEY)) || null,
  )
  const changesSinceBackup = useRef(Number(localStorage.getItem(CHANGES_KEY)) || 0)
  const undo = useUndo()

  useEffect(() => {
    void (async () => {
      const database = await openDb()
      const s = new MutationSink(systemClock, deviceId(), Number(localStorage.getItem(LAMPORT_KEY)) || 0)
      setDb(database)
      setSink(s)
      setRepo(new Repo(database, s, systemClock))
      setPersistent(await requestPersistence())
    })()
  }, [])

  const reload = useCallback(async (r: Repo, s: MutationSink) => {
    const [b, t] = await Promise.all([r.bits(), r.topics()])
    setBits(b)
    setTopics(t)
    // Логические часы обязаны пережить закрытие вкладки, иначе после
    // перезапуска правки начнут проигрывать собственной старой истории.
    localStorage.setItem(LAMPORT_KEY, String(s.current()))
  }, [])

  useEffect(() => {
    if (repo && sink) void reload(repo, sink)
  }, [repo, sink, reload])

  const topicTitle = useCallback(
    (id: string | null) => topics.find((t) => t.id === id)?.title ?? T.noTopic,
    [topics],
  )

  /** Любое изменение проходит здесь: перечитать список и подписать отмену. */
  const commit = useCallback(
    async (label: string, run: () => Promise<{ store: StoreName; row: { id: string } } | null>) => {
      if (!repo || !sink) return
      const prev = await run()
      await reload(repo, sink)

      changesSinceBackup.current += 1
      localStorage.setItem(CHANGES_KEY, String(changesSinceBackup.current))
      if (backupDue(changesSinceBackup.current, lastBackupAt, Date.now())) {
        await exportVault()
      }

      if (prev) {
        undo.push(label, async () => {
          await repo.restore(prev.store, prev.row)
          await reload(repo, sink)
        })
      }
    },
    // exportVault намеренно не в зависимостях: он читает те же db/sink и
    // пересоздаётся на каждой перерисовке, а лишние пересоздания commit
    // сбрасывали бы черновики полей.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [repo, sink, reload, undo, lastBackupAt],
  )

  const openBit = useMemo(() => bits.find((b) => b.id === openBitId) ?? null, [bits, openBitId])

  const actions = useMemo(() => {
    if (!repo) return null
    const bitEdit =
      (label: string, fn: (id: string) => Promise<Bit | null>) =>
      async () => {
        if (!openBitId) return
        await commit(label, async () => {
          const prev = await fn(openBitId)
          return prev ? { store: 'bits' as const, row: prev } : null
        })
      }
    return {
      setTitle: (v: string) => bitEdit(UNDO_LABEL.titleSet, (id) => repo.setTitle(id, v))(),
      setAttitude: (a: Attitude | null) =>
        bitEdit(a ? UNDO_LABEL.attitudeSet(a) : UNDO_LABEL.attitudeCleared, (id) =>
          repo.setAttitude(id, a),
        )(),
      setPremise: (v: string) => bitEdit(UNDO_LABEL.premiseSet, (id) => repo.setPremise(id, v))(),
      setSetup: (v: string) => bitEdit(UNDO_LABEL.setupSet, (id) => repo.setSetup(id, v))(),
      setPunch: (v: string, t: PunchTechnique) =>
        bitEdit(UNDO_LABEL.punchSet, (id) => repo.setPunch(id, v, t))(),
      setActOut: (v: string, space: boolean) =>
        bitEdit(UNDO_LABEL.actOutSet, (id) => repo.setActOut(id, v, space))(),
      setTags: (tags: string[]) => {
        const before = openBit?.elements.tags ?? []
        const added = tags.find((x) => !before.includes(x))
        const removed = before.find((x) => !tags.includes(x))
        const label = added
          ? UNDO_LABEL.tagAdded(added)
          : removed
            ? UNDO_LABEL.tagRemoved(removed)
            : UNDO_LABEL.titleSet
        return bitEdit(label, (id) => repo.setTags(id, tags))()
      },
    }
  }, [repo, openBitId, openBit, commit])

  async function exportVault() {
    if (!db || !sink) return
    const now = Date.now()
    downloadVault(await buildVault(db, deviceId(), sink.current(), now))
    localStorage.setItem(BACKUP_KEY, String(now))
    changesSinceBackup.current = 0
    localStorage.setItem(CHANGES_KEY, '0')
    setLastBackupAt(now)
  }

  async function importVault(file: File) {
    if (!db || !repo || !sink) return
    const parsed: unknown = JSON.parse(await file.text())
    const check = inspectVault(parsed)
    // Проверка до записи: битый файл не должен стереть то, что уже есть.
    if (!check.ok) {
      alert(check.reason)
      return
    }
    const data = (parsed as { data: Record<string, unknown[]> }).data
    for (const [store, rows] of Object.entries(data)) {
      await putAll(db, store as StoreName, rows)
      for (const r of rows) {
        const m = (r as { meta?: { lamport?: number } }).meta
        if (m?.lamport !== undefined) sink.observe(m.lamport)
      }
    }
    await reload(repo, sink)
  }

  if (!repo || !actions) return <div class="empty">…</div>

  const title = openBit ? T.workshopTitle : T.appName

  return (
    <div class="app">
      <div class="topbar">
        {openBit && <button data-testid="back" onClick={() => setOpenBitId(null)}>←</button>}
        <span class="grow">{title}</span>
      </div>

      {undo.pending && (
        <div class="undobar" data-testid="undobar">
          <span class="grow">{T.undoPrefix} {undo.pending.label}</span>
          <button data-testid="undo" onClick={() => void undo.run()}>↶</button>
        </div>
      )}

      {openBit ? (
        <WorkshopScreen bit={openBit} actions={actions} onBack={() => setOpenBitId(null)} />
      ) : tab === 'inbox' ? (
        <InboxScreen
          bits={bits}
          topicTitle={topicTitle}
          onAdd={(t) =>
            commit(UNDO_LABEL.bitCreated, async () => {
              const b = await repo.addBit(t)
              // Отмена создания — это надгробие, а не удаление строки.
              return { store: 'bits', row: { ...b, meta: { ...b.meta, deletedAt: b.meta.updatedAt } } }
            })
          }
          onOpen={setOpenBitId}
        />
      ) : tab === 'topics' ? (
        <TopicsScreen
          topics={topics}
          bitCount={(id) => bits.filter((b) => b.topicId === id).length}
          onAdd={(t) =>
            commit(UNDO_LABEL.topicAdded, async () => {
              const x = await repo.addTopic(t)
              return { store: 'topics', row: { ...x, meta: { ...x.meta, deletedAt: x.meta.updatedAt } } }
            })
          }
          onDelete={(id) =>
            commit(UNDO_LABEL.topicDeleted, async () => {
              const prev = await repo.deleteTopic(id)
              return prev ? { store: 'topics', row: prev } : null
            })
          }
        />
      ) : (
        <BackupScreen
          bitCount={bits.length}
          topicCount={topics.length}
          persistent={persistent}
          lastBackupAt={lastBackupAt}
          onExport={() => void exportVault()}
          onImport={importVault}
        />
      )}

      {undo.toast && (
        <div class="toast" data-testid="toast" onClick={undo.clearToast}>
          {T.undoneToast}: {undo.toast}
        </div>
      )}

      {!openBit && (
        <nav class="tabs">
          <button class={tab === 'inbox' ? 'on' : ''} data-testid="tab-inbox" onClick={() => setTab('inbox')}>
            {T.tabInbox}
          </button>
          <button class={tab === 'topics' ? 'on' : ''} data-testid="tab-topics" onClick={() => setTab('topics')}>
            {T.tabTopics}
          </button>
          <button class={tab === 'backup' ? 'on' : ''} data-testid="tab-backup" onClick={() => setTab('backup')}>
            {T.tabBackup}
          </button>
        </nav>
      )}
    </div>
  )
}
