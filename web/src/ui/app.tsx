import { useCallback, useEffect, useMemo, useRef, useState } from 'preact/hooks'
import type {
  Attitude, Bit, BitPerformance, ExerciseRecord, Gig, GigType, JournalEntry,
  LaughResult, PunchTechnique, SetList, SetListRole, Settings, Topic,
} from '../domain/domain'
import { systemClock, type DeviceId, type Id, type SyncMeta } from '../domain/identity'
import { averageScoreByBit, actOutRatio, attitudeSpread, funnel, polishedMinutes, streakDays, type Progress } from '../domain/metrics'
import { exportMarkdown, searchBits } from '../domain/markdown'
import { openDb, putAll, requestPersistence, type StoreName } from '../store/db'
import { MutationSink } from '../store/sink'
import { Repo } from '../store/repo'
import { Repo2 } from '../store/repo2'
import { backupDue, buildVault, downloadVault, inspectVault } from '../store/backup'
import { loadExercises, type Exercise } from '../store/exercises'
import { ATTITUDE_LABEL, ROLE_LABEL, STATUS_LABEL, T, TECHNIQUE_LABEL, UNDO_LABEL } from './labels'
import { useUndo } from './undo'
import { InboxScreen } from './screens/inbox'
import { TopicsScreen } from './screens/topics'
import { WorkshopScreen } from './screens/workshop'
import { BackupScreen } from './screens/backup'
import { TodayScreen } from './screens/today'
import { PracticeScreen } from './screens/practice'
import { SetListsScreen, SetListEditor } from './screens/setlists'
import { StageScreen } from './screens/stage'
import { GigsScreen, GigReviewScreen } from './screens/gigs'
import { JournalScreen } from './screens/journal'

type Tab = 'today' | 'practice' | 'material' | 'sets' | 'journal'
type Overlay =
  | { kind: 'none' }
  | { kind: 'bit'; id: Id }
  | { kind: 'set'; id: Id }
  | { kind: 'stage'; id: Id }
  | { kind: 'review'; id: Id }
  | { kind: 'settings' }

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

interface Snapshot {
  bits: Bit[]
  topics: Topic[]
  setLists: SetList[]
  gigs: Gig[]
  performances: BitPerformance[]
  journal: JournalEntry[]
  exerciseRecords: ExerciseRecord[]
  settings: Settings | null
}

const EMPTY: Snapshot = {
  bits: [], topics: [], setLists: [], gigs: [], performances: [],
  journal: [], exerciseRecords: [], settings: null,
}

export function App() {
  const [db, setDb] = useState<IDBDatabase | null>(null)
  const [sink, setSink] = useState<MutationSink | null>(null)
  const [repo, setRepo] = useState<Repo | null>(null)
  const [repo2, setRepo2] = useState<Repo2 | null>(null)
  const [data, setData] = useState<Snapshot>(EMPTY)
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [tab, setTab] = useState<Tab>('today')
  const [overlay, setOverlay] = useState<Overlay>({ kind: 'none' })
  const [query, setQuery] = useState('')
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
      setRepo2(new Repo2(database, s, systemClock))
      setPersistent(await requestPersistence())
      // Каталог упражнений — статический файл, а не часть базы: он от книги,
      // а не от автора, и обновляется вместе с приложением.
      setExercises(await loadExercises().catch(() => []))
    })()
  }, [])

  const reload = useCallback(async (r: Repo, r2: Repo2, s: MutationSink) => {
    const [bits, topics, setLists, gigs, performances, journal, exerciseRecords, settings] =
      await Promise.all([
        r.bits(), r.topics(), r2.setLists(), r2.gigs(), r2.performances(),
        r2.journal(), r2.exercises(), r2.settings(),
      ])
    setData({ bits, topics, setLists, gigs, performances, journal, exerciseRecords, settings })
    localStorage.setItem(LAMPORT_KEY, String(s.current()))
  }, [])

  useEffect(() => {
    if (repo && repo2 && sink) void reload(repo, repo2, sink)
  }, [repo, repo2, sink, reload])

  async function exportVault() {
    if (!db || !sink) return
    const now = Date.now()
    downloadVault(await buildVault(db, deviceId(), sink.current(), now))
    localStorage.setItem(BACKUP_KEY, String(now))
    changesSinceBackup.current = 0
    localStorage.setItem(CHANGES_KEY, '0')
    setLastBackupAt(now)
  }

  /** Любое изменение проходит здесь: перечитать данные и подписать отмену. */
  const commit = useCallback(
    async (
      label: string,
      run: () => Promise<{ store: StoreName; row: { id: Id; meta: SyncMeta } } | null>,
    ) => {
      if (!repo || !repo2 || !sink) return
      const prev = await run()
      await reload(repo, repo2, sink)

      changesSinceBackup.current += 1
      localStorage.setItem(CHANGES_KEY, String(changesSinceBackup.current))
      if (backupDue(changesSinceBackup.current, lastBackupAt, Date.now())) await exportVault()

      if (prev) {
        undo.push(label, async () => {
          await repo.restore(prev.store, prev.row)
          await reload(repo, repo2, sink)
        })
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [repo, repo2, sink, reload, undo, lastBackupAt],
  )

  /** Создание: отмена кладёт надгробие поверх свежей записи. */
  const commitCreate = useCallback(
    <R extends { id: Id; meta: SyncMeta }>(label: string, store: StoreName, make: () => Promise<R>) =>
      commit(label, async () => {
        const row = await make()
        return { store, row: { ...row, meta: { ...row.meta, deletedAt: row.meta.updatedAt } } }
      }),
    [commit],
  )

  const scores = useMemo(() => averageScoreByBit(data.performances), [data.performances])

  const progress: Progress = useMemo(() => {
    const activity = [
      ...data.bits.map((b) => b.meta.updatedAt),
      ...data.journal.map((j) => j.dayMillis),
      ...data.gigs.map((g) => g.dateMillis),
    ]
    return {
      funnel: funnel(data.bits),
      polishedMinutes: polishedMinutes(data.bits),
      goalMinutes: data.settings?.goalMinutes ?? 5,
      actOutRatio: actOutRatio(data.bits),
      attitudeSpread: attitudeSpread(data.bits),
      gigsLast30Days: repo2 ? repo2.gigsLast30Days(data.gigs) : 0,
      streakDays: streakDays(activity, Date.now()),
    }
  }, [data, repo2])

  const openBit = useMemo(
    () => (overlay.kind === 'bit' ? data.bits.find((b) => b.id === overlay.id) ?? null : null),
    [overlay, data.bits],
  )
  const openSet = useMemo(
    () => (overlay.kind === 'set' || overlay.kind === 'stage'
      ? data.setLists.find((s) => s.id === overlay.id) ?? null
      : null),
    [overlay, data.setLists],
  )
  const openGig = useMemo(
    () => (overlay.kind === 'review' ? data.gigs.find((g) => g.id === overlay.id) ?? null : null),
    [overlay, data.gigs],
  )

  const bitActions = useMemo(() => {
    if (!repo || !repo2) return null
    const edit = (label: string, fn: (id: Id) => Promise<Bit | null>) => async () => {
      if (overlay.kind !== 'bit') return
      const before = data.bits.find((b) => b.id === overlay.id)
      if (before) await repo2.snapshot(before)
      await commit(label, async () => {
        const prev = await fn(overlay.id)
        return prev ? { store: 'bits' as const, row: prev } : null
      })
    }
    return {
      setTitle: (v: string) => edit(UNDO_LABEL.titleSet, (id) => repo.setTitle(id, v))(),
      setAttitude: (a: Attitude | null) =>
        edit(a ? UNDO_LABEL.attitudeSet(a) : UNDO_LABEL.attitudeCleared, (id) => repo.setAttitude(id, a))(),
      setPremise: (v: string) => edit(UNDO_LABEL.premiseSet, (id) => repo.setPremise(id, v))(),
      setSetup: (v: string) => edit(UNDO_LABEL.setupSet, (id) => repo.setSetup(id, v))(),
      setPunch: (v: string, t: PunchTechnique) => edit(UNDO_LABEL.punchSet, (id) => repo.setPunch(id, v, t))(),
      setActOut: (v: string, space: boolean) =>
        edit(UNDO_LABEL.actOutSet, (id) => repo.setActOut(id, v, space))(),
      setDuration: (sec: number | null) =>
        edit(UNDO_LABEL.durationOfBit, (id) => repo.setDuration(id, sec))(),
      setTags: (tags: string[]) => {
        const before = openBit?.elements.tags ?? []
        const added = tags.find((x) => !before.includes(x))
        const removed = before.find((x) => !tags.includes(x))
        const label = added ? UNDO_LABEL.tagAdded(added)
          : removed ? UNDO_LABEL.tagRemoved(removed)
          : UNDO_LABEL.titleSet
        return edit(label, (id) => repo.setTags(id, tags))()
      },
    }
  }, [repo, repo2, overlay, openBit, commit, data.bits])

  async function importVault(file: File) {
    if (!db || !repo || !repo2 || !sink) return
    const parsed: unknown = JSON.parse(await file.text())
    const check = inspectVault(parsed)
    if (!check.ok) {
      alert(check.reason)
      return
    }
    const rows = (parsed as { data: Record<string, unknown[]> }).data
    for (const [store, list] of Object.entries(rows)) {
      await putAll(db, store as StoreName, list)
      for (const r of list) {
        const m = (r as { meta?: { lamport?: number } }).meta
        if (m?.lamport !== undefined) sink.observe(m.lamport)
      }
    }
    await reload(repo, repo2, sink)
  }

  function doExport() {
    const md = exportMarkdown(
      {
        title: T.appName, topics: T.tabTopics, material: T.tabMaterial, act: 'Мой акт',
        setLists: T.setsTitle, journal: T.journalTitle, noTopic: T.noTopic,
        status: (s) => STATUS_LABEL[s], attitude: (a) => ATTITUDE_LABEL[a],
        technique: (t) => TECHNIQUE_LABEL[t], role: (r: SetListRole) => ROLE_LABEL[r],
      },
      data.topics, data.bits, data.setLists, data.journal,
    )
    const url = URL.createObjectURL(new Blob([md], { type: 'text/markdown' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'punchline.md'
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
  }

  if (!repo || !repo2 || !bitActions) return <div class="empty">…</div>

  // --- полноэкранные режимы --------------------------------------------

  if (overlay.kind === 'stage' && openSet) {
    return (
      <StageScreen
        setList={openSet}
        bits={data.bits}
        onExit={() => setOverlay({ kind: 'set', id: openSet.id })}
        onFinish={async (elapsed) => {
          const gig = await repo2.addGig(openSet.id, 'OPEN_MIC', openSet.title)
          await repo2.setGigDuration(gig.id, elapsed)
          await reload(repo, repo2, sink!)
          setOverlay({ kind: 'review', id: gig.id })
        }}
      />
    )
  }

  const found = query.trim() ? searchBits(data.bits, query) : null

  return (
    <div class="app">
      <div class="topbar">
        {overlay.kind !== 'none' && (
          <button data-testid="back" onClick={() => setOverlay({ kind: 'none' })}>←</button>
        )}
        <span class="grow">{T.appName}</span>
      </div>

      {undo.pending && (
        <div class="undobar" data-testid="undobar">
          <span class="grow">{T.undoPrefix} {undo.pending.label}</span>
          <button data-testid="undo" onClick={() => void undo.run()}>↶</button>
        </div>
      )}

      {overlay.kind === 'bit' && openBit ? (
        <WorkshopScreen bit={openBit} actions={bitActions} onBack={() => setOverlay({ kind: 'none' })} />
      ) : overlay.kind === 'set' && openSet ? (
        <SetListEditor
          setList={openSet}
          bits={data.bits}
          scores={scores}
          onAddBit={(bitId, dur) =>
            void commit(UNDO_LABEL.setChanged, async () => {
              const prev = await repo2.addToSetList(openSet.id, bitId, dur)
              return prev ? { store: 'setLists', row: prev } : null
            })}
          onRemove={(itemId) =>
            void commit(UNDO_LABEL.setChanged, async () => {
              const prev = await repo2.removeFromSetList(openSet.id, itemId)
              return prev ? { store: 'setLists', row: prev } : null
            })}
          onRole={(itemId, role) =>
            void commit(UNDO_LABEL.roleSet(role), async () => {
              const prev = await repo2.setItemRole(openSet.id, itemId, role)
              return prev ? { store: 'setLists', row: prev } : null
            })}
          onMove={(itemId, delta) =>
            void commit(UNDO_LABEL.orderChanged, async () => {
              const prev = await repo2.moveItem(openSet.id, itemId, delta)
              return prev ? { store: 'setLists', row: prev } : null
            })}
          onTarget={(sec) =>
            void commit(UNDO_LABEL.targetSet, async () => {
              const prev = await repo2.setTargetDuration(openSet.id, sec)
              return prev ? { store: 'setLists', row: prev } : null
            })}
          onStage={() => setOverlay({ kind: 'stage', id: openSet.id })}
          onDelete={() => {
            void commit(UNDO_LABEL.setDeleted, async () => {
              const prev = await repo2.deleteSetList(openSet.id)
              return prev ? { store: 'setLists', row: prev } : null
            })
            setOverlay({ kind: 'none' })
          }}
        />
      ) : overlay.kind === 'review' && openGig ? (
        <GigReviewScreen
          gig={openGig}
          setList={data.setLists.find((s) => s.id === openGig.setListId) ?? null}
          bits={data.bits}
          performances={data.performances}
          onMark={(bitId, result: LaughResult) =>
            void commit(UNDO_LABEL.marked(result), async () => {
              const prev = await repo2.mark(openGig.id, bitId, result)
              // Отметка меняет судьбу шутки: обкатанная становится кандидатом
              // в сет, дважды рассмешившая — отшлифованной.
              await repo.refreshStatus(bitId)
              return prev ? { store: 'performances', row: prev } : null
            })}
          onDuration={(sec) =>
            void commit(UNDO_LABEL.durationSet, async () => {
              const prev = await repo2.setGigDuration(openGig.id, sec)
              return prev ? { store: 'gigs', row: prev } : null
            })}
          onBack={() => setOverlay({ kind: 'none' })}
        />
      ) : overlay.kind === 'settings' ? (
        <BackupScreen
          bitCount={data.bits.length}
          topicCount={data.topics.length}
          persistent={persistent}
          lastBackupAt={lastBackupAt}
          onExport={() => void exportVault()}
          onImport={importVault}
          onExportMarkdown={doExport}
        />
      ) : tab === 'today' ? (
        <TodayScreen
          progress={progress}
          settings={data.settings ?? { id: 'settings', goalMinutes: 5, comedyVision: '', meta: { updatedAt: 0, lamport: 0, deviceId: '', deletedAt: null } }}
          hasAnything={data.bits.length + data.topics.length > 0}
          onGoal={(m) =>
            void commit(UNDO_LABEL.goalSet, async () => {
              const prev = await repo2.saveSettings({ goalMinutes: m })
              return { store: 'settings', row: prev }
            })}
          onVision={(v) =>
            void commit(UNDO_LABEL.visionSet, async () => {
              const prev = await repo2.saveSettings({ comedyVision: v })
              return { store: 'settings', row: prev }
            })}
          onOpenSettings={() => setOverlay({ kind: 'settings' })}
        />
      ) : tab === 'practice' ? (
        <PracticeScreen
          exercises={exercises}
          records={data.exerciseRecords}
          onToggle={(n, done) =>
            void commit(UNDO_LABEL.exerciseToggled(n), async () => {
              const prev = await repo2.toggleExercise(n, done)
              return prev ? { store: 'exercises', row: prev } : null
            })}
          onNote={(n, note) =>
            void commit(UNDO_LABEL.exerciseNote(n), async () => {
              const prev = await repo2.setExerciseNote(n, note)
              return prev ? { store: 'exercises', row: prev } : null
            })}
        />
      ) : tab === 'material' ? (
        <div class="scroll" data-screen="material">
          <div class="card">
            <input
              type="text" data-testid="search" placeholder={T.searchPlaceholder}
              value={query} onInput={(e) => setQuery((e.target as HTMLInputElement).value)}
            />
          </div>
          {found ? (
            found.length === 0 ? (
              <p class="empty">{T.searchEmpty}</p>
            ) : (
              <ul class="list" data-testid="search-results">
                {found.map((b) => (
                  <li key={b.id} class="item" onClick={() => setOverlay({ kind: 'bit', id: b.id })}>
                    <div class="grow"><div class="title">{b.title}</div></div>
                    <span class="badge">{STATUS_LABEL[b.status]}</span>
                  </li>
                ))}
              </ul>
            )
          ) : (
            <>
              <InboxScreen
                bits={data.bits}
                topicTitle={(id) => data.topics.find((t) => t.id === id)?.title ?? T.noTopic}
                onAdd={(title) => commitCreate(UNDO_LABEL.bitCreated, 'bits', () => repo.addBit(title))}
                onOpen={(id) => setOverlay({ kind: 'bit', id })}
              />
              <TopicsScreen
                topics={data.topics}
                bitCount={(id) => data.bits.filter((b) => b.topicId === id).length}
                onAdd={(title) => commitCreate(UNDO_LABEL.topicAdded, 'topics', () => repo.addTopic(title))}
                onDelete={(id) =>
                  commit(UNDO_LABEL.topicDeleted, async () => {
                    const prev = await repo.deleteTopic(id)
                    return prev ? { store: 'topics', row: prev } : null
                  })}
              />
            </>
          )}
        </div>
      ) : tab === 'sets' ? (
        <div class="scroll" data-screen="sets">
          <SetListsScreen
            setLists={data.setLists}
            onAdd={(title, target) =>
              void commitCreate(UNDO_LABEL.setCreated, 'setLists', () => repo2.addSetList(title, target))}
            onOpen={(id) => setOverlay({ kind: 'set', id })}
          />
          <GigsScreen
            gigs={data.gigs}
            setLists={data.setLists}
            onAdd={(setId, type: GigType, venue) =>
              void commitCreate(UNDO_LABEL.gigCreated, 'gigs', () => repo2.addGig(setId, type, venue))}
            onOpen={(id) => setOverlay({ kind: 'review', id })}
          />
        </div>
      ) : (
        <JournalScreen
          entries={data.journal}
          streakDays={progress.streakDays}
          onSave={(text, sec) =>
            void commitCreate(UNDO_LABEL.journalSaved, 'journal', () => repo2.addJournalEntry(text, sec))}
          onHarvest={(text) => {
            void commitCreate(UNDO_LABEL.bitCreated, 'bits', () => repo.addBit(text))
            setTab('material')
          }}
          onDelete={(id) =>
            void commit(UNDO_LABEL.journalDeleted, async () => {
              const prev = await repo2.deleteJournalEntry(id)
              return prev ? { store: 'journal', row: prev } : null
            })}
        />
      )}

      {undo.toast && (
        <div class="toast" data-testid="toast" onClick={undo.clearToast}>
          {T.undoneToast}: {undo.toast}
        </div>
      )}

      {overlay.kind === 'none' && (
        <nav class="tabs">
          {([
            ['today', T.tabToday],
            ['practice', T.tabPractice],
            ['material', T.tabMaterial],
            ['sets', T.tabSets],
            ['journal', T.tabJournal],
          ] as [Tab, string][]).map(([id, label]) => (
            <button
              key={id} class={tab === id ? 'on' : ''} data-testid={`tab-${id}`}
              onClick={() => { setTab(id); setQuery('') }}
            >
              {label}
            </button>
          ))}
        </nav>
      )}
    </div>
  )
}
