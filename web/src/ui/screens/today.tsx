import type { Attitude, BitStatus, Settings } from '../../domain/domain'
import { GOAL_CHOICES } from '../../domain/domain'
import { bottleneck, goalRatio, type Progress } from '../../domain/metrics'
import { ATTITUDE_LABEL, STATUS_LABEL, T } from '../labels'
import { plural } from '../plural'

interface Props {
  progress: Progress
  settings: Settings
  hasAnything: boolean
  onGoal: (minutes: number) => void
  onVision: (text: string) => void
  onOpenSettings: () => void
}

function Bar({ ratio }: { ratio: number }) {
  return (
    <div class="bar" role="presentation">
      <div class="bar-fill" style={`width:${Math.round(ratio * 100)}%`} />
    </div>
  )
}

/**
 * Главный экран показывает три числа из методики: цепочка, минуты готового
 * материала и выходы на сцену. Остальное — вторично и живёт ниже, чтобы
 * ежедневный взгляд не тонул в статистике.
 */
export function TodayScreen({ progress, settings, hasAnything, onGoal, onVision, onOpenSettings }: Props) {
  const stuck: BitStatus | null = bottleneck(progress)
  const spread = Object.entries(progress.attitudeSpread) as [Attitude, number][]
  const spreadTotal = spread.reduce((s, [, n]) => s + n, 0)

  return (
    <div class="scroll" data-screen="today">
      <div class="card">
        <h2>{T.todayChain}</h2>
        {progress.streakDays > 0 ? (
          <p class="big" data-testid="streak">
            {progress.streakDays}{' '}
            <span class="big-unit">{plural(progress.streakDays, 'день', 'дня', 'дней')} подряд</span>
          </p>
        ) : (
          <p class="hint" style="margin-top:0">{T.todayChainEmpty}</p>
        )}
      </div>

      <div class="card">
        <h2>{T.todayReady}</h2>
        <p class="big" data-testid="ready-minutes">
          {progress.polishedMinutes.toFixed(0)}{' '}
          <span class="big-unit">{T.todayOf} {settings.goalMinutes} {T.todayMinutes}</span>
        </p>
        <Bar ratio={goalRatio(progress)} />
        <p class="hint">{T.todayGoal}</p>
        <div class="chips">
          {GOAL_CHOICES.map((m) => (
            <button
              key={m}
              class={`chip small${settings.goalMinutes === m ? ' on' : ''}`}
              data-testid={`goal-${m}`}
              onClick={() => onGoal(m)}
            >
              {m} {T.todayMinutes}
            </button>
          ))}
        </div>
      </div>

      <div class="card">
        <h2>{T.todayGigs}</h2>
        <p class="big" data-testid="gigs-30">{progress.gigsLast30Days}</p>
      </div>

      {stuck && (
        <div class="card">
          <h2>{T.todayBottleneck}</h2>
          <p class="big" data-testid="bottleneck">{STATUS_LABEL[stuck]}</p>
          <p class="hint">{T.todayBottleneckHint}</p>
        </div>
      )}

      {spreadTotal > 0 && (
        <div class="card">
          <h2>{T.todayAttitude}</h2>
          {spread.map(([a, n]) => (
            <div key={a} class="spread-row">
              <span class="spread-name">{ATTITUDE_LABEL[a]}</span>
              <Bar ratio={n / spreadTotal} />
              <span class="spread-num">{n}</span>
            </div>
          ))}
          <p class="hint">
            {T.todayActOut}: {Math.round(progress.actOutRatio * 100)}%
          </p>
        </div>
      )}

      <div class="card">
        <h2>{T.todayVision}</h2>
        <input
          type="text" data-testid="vision" placeholder={T.todayVisionPlaceholder}
          value={settings.comedyVision}
          onChange={(e) => onVision((e.target as HTMLInputElement).value)}
        />
      </div>

      {!hasAnything && <p class="empty">{T.todayNothing}</p>}

      <div style="padding:0 12px 24px">
        <button class="btn ghost" data-testid="open-settings" onClick={onOpenSettings}>
          {T.todaySettings}
        </button>
      </div>
    </div>
  )
}
