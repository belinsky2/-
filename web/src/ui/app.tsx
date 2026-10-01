import { useEffect, useState } from 'preact/hooks'
import type { AudioClip } from '../domain/domain'
import type { Id } from '../domain/identity'
import { HELP } from './help'
import { T } from './labels'
import { useAppData } from './state/data'
import { useBackup } from './state/backup'
import { useCommands } from './state/commands'
import { useUndo } from './state/undo'
import { BackupScreen } from './screens/backup'
import { GigReviewScreen, GigsScreen } from './screens/gigs'
import { JournalScreen } from './screens/journal'
import { MaterialScreen } from './screens/material'
import { PracticeScreen } from './screens/practice'
import { SetListEditor, SetListsScreen } from './screens/setlists'
import { StageScreen } from './screens/stage'
import { TodayScreen } from './screens/today'
import { WorkshopScreen } from './screens/workshop'

type Tab = 'today' | 'practice' | 'material' | 'sets' | 'journal'

/** Экран поверх вкладок. Вкладки — разделы тетради, оверлеи — работа внутри них. */
type Overlay =
  | { kind: 'none' }
  | { kind: 'bit'; id: Id }
  | { kind: 'set'; id: Id }
  | { kind: 'stage'; id: Id }
  | { kind: 'review'; id: Id }
  | { kind: 'settings' }

const TABS: readonly [Tab, string][] = [
  ['today', T.tabToday],
  ['practice', T.tabPractice],
  ['material', T.tabMaterial],
  ['sets', T.tabSets],
  ['journal', T.tabJournal],
]

const HELP_KEY: Record<Overlay['kind'], string | null> = {
  none: null, bit: 'workshop', set: 'sets', stage: 'stage', review: 'review', settings: 'backup',
}

/**
 * Каркас приложения: шапка, справка, отмена, вкладки и переходы между экранами.
 * Данные живут в state/data, изменения — в state/commands, архив — в state/backup.
 */
export function App() {
  const app = useAppData()
  const backup = useBackup(app)
  const undo = useUndo()
  const cmd = useCommands(app, backup, undo)

  const [tab, setTab] = useState<Tab>('today')
  const [overlay, setOverlay] = useState<Overlay>({ kind: 'none' })
  const [helpOpen, setHelpOpen] = useState(false)
  const [clips, setClips] = useState<AudioClip[]>([])

  const { data } = app
  const openBit = overlay.kind === 'bit' ? data.bits.find((b) => b.id === overlay.id) ?? null : null
  const openSet = overlay.kind === 'set' || overlay.kind === 'stage'
    ? data.setLists.find((s) => s.id === overlay.id) ?? null
    : null
  const openGig = overlay.kind === 'review' ? data.gigs.find((g) => g.id === overlay.id) ?? null : null

  useEffect(() => {
    // Записи тяжёлые, поэтому подтягиваются только для открытой шутки.
    // Список не обнуляется перед запросом: иначе при каждой правке записи
    // на мгновение пропадали бы с экрана.
    if (!app.stores || overlay.kind !== 'bit') { setClips([]); return }
    let cancelled = false
    void app.stores.material.audioFor(overlay.id, null).then((l) => { if (!cancelled) setClips(l) })
    return () => { cancelled = true }
  }, [app.stores, overlay, data.bits])

  if (!cmd) return <div class="empty">…</div>

  const go = (o: Overlay) => { setOverlay(o); setHelpOpen(false) }

  if (overlay.kind === 'stage' && openSet) {
    return (
      <StageScreen
        setList={openSet}
        bits={data.bits}
        onExit={() => go({ kind: 'set', id: openSet.id })}
        onFinish={async (elapsed) => {
          const gigId = await cmd.stage.finishStage(openSet.id, openSet.title, elapsed)
          if (gigId) go({ kind: 'review', id: gigId })
        }}
      />
    )
  }

  const help = HELP[HELP_KEY[overlay.kind] ?? tab]

  return (
    <div class="app">
      <div class="topbar">
        {overlay.kind !== 'none' && (
          <button data-testid="back" onClick={() => go({ kind: 'none' })}>←</button>
        )}
        <span class="grow">{help?.title ?? T.appName}</span>
        <button
          class="help-btn" data-testid="help-toggle"
          aria-label={T.helpOpen} aria-expanded={helpOpen}
          onClick={() => setHelpOpen((v) => !v)}
        >
          {helpOpen ? '×' : '?'}
        </button>
      </div>

      {helpOpen && help && (
        <div class="help" data-testid="help">
          {help.lines.map((line, i) => <p key={i}>{line}</p>)}
          <button class="btn ghost" data-testid="help-close" onClick={() => setHelpOpen(false)}>
            {T.helpClose}
          </button>
        </div>
      )}

      {undo.pending && (
        <div class="undobar" data-testid="undobar">
          <span class="grow">{T.undoPrefix} {undo.pending.label}</span>
          <button data-testid="undo" onClick={() => void undo.run()}>↶</button>
        </div>
      )}

      {openBit ? (
        (() => {
          const b = cmd.material.bit(openBit)
          return (
            <WorkshopScreen
              bit={openBit}
              topics={data.topics}
              actions={b}
              clips={clips}
              onRecord={(blob, mime, sec) => void b.addAudio(blob, mime, sec)}
              onDeleteClip={(id) => void b.deleteAudio(id)}
              onBack={() => go({ kind: 'none' })}
            />
          )
        })()
      ) : overlay.kind === 'set' && openSet ? (
        (() => {
          const s = cmd.stage.setList(openSet.id)
          return (
            <SetListEditor
              setList={openSet}
              bits={data.bits}
              scores={app.scores}
              onAddBit={(bitId, dur) => void s.addBit(bitId, dur)}
              onRemove={(itemId) => void s.remove(itemId)}
              onRole={(itemId, role) => void s.setRole(itemId, role)}
              onMove={(itemId, delta) => void s.move(itemId, delta)}
              onTarget={(sec) => void s.setTarget(sec)}
              onStage={() => go({ kind: 'stage', id: openSet.id })}
              onDelete={() => { void s.deleteSet(); go({ kind: 'none' }) }}
            />
          )
        })()
      ) : openGig ? (
        (() => {
          const g = cmd.stage.gig(openGig.id)
          return (
            <GigReviewScreen
              gig={openGig}
              setList={data.setLists.find((s) => s.id === openGig.setListId) ?? null}
              bits={data.bits}
              performances={data.performances}
              onMark={(bitId, result) => void g.mark(bitId, result)}
              onDuration={(sec) => void g.setDuration(sec)}
              onBack={() => go({ kind: 'none' })}
            />
          )
        })()
      ) : overlay.kind === 'settings' ? (
        <BackupScreen
          bitCount={data.bits.length}
          topicCount={data.topics.length}
          persistent={app.persistent}
          lastBackupAt={backup.lastBackupAt}
          onExport={() => void backup.exportNow()}
          onImport={backup.importFile}
          onExportMarkdown={backup.exportMarkdownFile}
        />
      ) : tab === 'today' ? (
        <TodayScreen
          progress={app.progress}
          settings={data.settings}
          hasAnything={data.bits.length + data.topics.length > 0}
          onGoal={(m) => void cmd.practice.setGoal(m)}
          onVision={(v) => void cmd.practice.setVision(v)}
          onOpenSettings={() => go({ kind: 'settings' })}
        />
      ) : tab === 'practice' ? (
        <PracticeScreen
          exercises={app.exercises}
          records={data.exerciseRecords}
          onToggle={(n, done) => void cmd.practice.toggleExercise(n, done)}
          onNote={(n, note) => void cmd.practice.setExerciseNote(n, note)}
        />
      ) : tab === 'material' ? (
        <MaterialScreen
          bits={data.bits}
          topics={data.topics}
          onAddBit={cmd.material.addBit}
          onAddTopic={cmd.material.addTopic}
          onDeleteTopic={cmd.material.deleteTopic}
          onOpenBit={(id) => go({ kind: 'bit', id })}
        />
      ) : tab === 'sets' ? (
        <div class="scroll" data-screen="sets">
          <SetListsScreen
            setLists={data.setLists}
            onAdd={(title, target) => void cmd.stage.addSetList(title, target)}
            onOpen={(id) => go({ kind: 'set', id })}
          />
          <GigsScreen
            gigs={data.gigs}
            setLists={data.setLists}
            onAdd={(setId, type, venue) => void cmd.stage.addGig(setId, type, venue)}
            onOpen={(id) => go({ kind: 'review', id })}
          />
        </div>
      ) : (
        <JournalScreen
          entries={data.journal}
          streakDays={app.progress.streakDays}
          onSave={(text, sec) => void cmd.practice.saveJournal(text, sec)}
          onHarvest={(text) => { void cmd.material.addBit(text); setTab('material') }}
          onDelete={(id) => void cmd.practice.deleteJournal(id)}
        />
      )}

      {undo.toast && (
        <div class="toast" data-testid="toast" onClick={undo.clearToast}>
          {T.undoneToast}: {undo.toast}
        </div>
      )}

      {overlay.kind === 'none' && (
        <nav class="tabs">
          {TABS.map(([id, label]) => (
            <button
              key={id} class={tab === id ? 'on' : ''} data-testid={`tab-${id}`}
              onClick={() => { setTab(id); setHelpOpen(false) }}
            >
              {label}
            </button>
          ))}
        </nav>
      )}
    </div>
  )
}
