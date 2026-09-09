import { useEffect, useRef, useState } from 'preact/hooks'
import type { Attitude, Bit, PunchTechnique } from '../../domain/domain'
import { ATTITUDES, PUNCH_TECHNIQUES } from '../../domain/domain'
import type { AudioClip } from '../../domain/domain'
import type { Id } from '../../domain/identity'
import { ATTITUDE_LABEL, ATTITUDE_PROMPT, STATUS_LABEL, T, TECHNIQUE_LABEL, UNDO_LABEL } from '../labels'
import { VoiceBlock } from './voice'

export const DURATION_CHOICES = [30, 45, 60, 90, 120, 180] as const

export interface WorkshopActions {
  setTitle: (v: string) => Promise<void>
  setAttitude: (a: Attitude | null) => Promise<void>
  setPremise: (v: string) => Promise<void>
  setSetup: (v: string) => Promise<void>
  setPunch: (v: string, tech: PunchTechnique) => Promise<void>
  setActOut: (v: string, space: boolean) => Promise<void>
  setTags: (tags: string[]) => Promise<void>
  setDuration: (sec: number | null) => Promise<void>
}

/**
 * Поле с отложенной записью.
 *
 * Черновик держится локально и сбрасывается только при смене шутки. В прошлой
 * версии состояние пересобиралось на каждой перерисовке, и введённая добивка
 * пропадала до того, как её успевали сохранить, — это и был баг «не
 * сохраняется».
 */
function useDraft<T>(stored: T, bitId: string): [T, (v: T) => void, boolean, () => void] {
  const [value, setValue] = useState<T>(stored)
  const owner = useRef(bitId)
  const known = useRef(stored)

  useEffect(() => {
    // Сброс только при смене шутки или при изменении значения снаружи
    // (например, отменой) — но не потому, что компонент перерисовался.
    if (owner.current !== bitId || known.current !== stored) {
      owner.current = bitId
      known.current = stored
      setValue(stored)
    }
  }, [bitId, stored])

  const changed = value !== stored
  return [value, setValue, changed, () => setValue(stored)]
}

function Section(props: { title: string; children: preact.ComponentChildren }) {
  return (
    <div class="card">
      <h2>{props.title}</h2>
      {props.children}
    </div>
  )
}

/**
 * Кнопка появляется только когда есть что сохранять.
 *
 * Постоянная серая плашка «Сохранено» под каждым полем — это шесть мёртвых
 * кнопок на экране: они не нажимаются, но занимают место и заставляют
 * проверять, не забыл ли ты чего. Подтверждением служит полоса отмены сверху:
 * она называет то, что только что записалось.
 */
function SaveButton(
  { changed, onSave, testid, filled }: { changed: boolean; onSave: () => void; testid: string; filled: boolean },
) {
  // Под пустым полем «Сохранено» — неправда: сохранять там нечего.
  if (!changed) {
    return filled ? <span class="saved" data-testid={testid} data-saved="1">{T.saved}</span> : null
  }
  return (
    <button class="btn" data-testid={testid} onClick={onSave} style="margin-top:10px">
      {T.save}
    </button>
  )
}

interface Props {
  bit: Bit
  actions: WorkshopActions
  clips: readonly AudioClip[]
  onRecord: (blob: Blob, mimeType: string, durationSec: number) => void
  onDeleteClip: (id: Id) => void
  onBack: () => void
}

export function WorkshopScreen({ bit, actions, clips, onRecord, onDeleteClip, onBack }: Props) {
  const id = bit.id
  const [title, setTitleDraft, titleChanged] = useDraft(bit.title, id)
  const [premise, setPremiseDraft, premiseChanged] = useDraft(bit.elements.premise ?? '', id)
  const [setup, setSetupDraft, setupChanged] = useDraft(bit.elements.setup ?? '', id)
  const [punch, setPunchDraft, punchChanged] = useDraft(bit.elements.punch?.text ?? '', id)
  const [actOut, setActOutDraft, actOutChanged] = useDraft(bit.elements.actOut?.text ?? '', id)
  const [tag, setTag] = useState('')

  const technique: PunchTechnique = bit.elements.punch?.technique ?? 'OTHER'
  const spaceWork = bit.elements.actOut?.hasSpaceWork ?? false
  const tags = bit.elements.tags

  return (
    <div class="scroll" data-screen="workshop">
      <Section title={`${T.fieldTitle} · ${STATUS_LABEL[bit.status]}`}>
        <input
          type="text" data-testid="title-input" value={title}
          onInput={(e) => setTitleDraft((e.target as HTMLInputElement).value)}
        />
        <SaveButton changed={titleChanged} filled={title.trim() !== ''} testid="title-save" onSave={() => void actions.setTitle(title)} />
      </Section>

      <Section title={T.fieldAttitude}>
        <div class="chips">
          {ATTITUDES.map((a) => (
            <button
              key={a}
              class={`chip${bit.attitude === a ? ' on' : ''}`}
              data-testid={`attitude-${a}`}
              onClick={() => void actions.setAttitude(bit.attitude === a ? null : a)}
            >
              {ATTITUDE_LABEL[a]}
            </button>
          ))}
        </div>
      </Section>

      <Section title={T.fieldPremise}>
        {bit.attitude && <p class="prompt" data-testid="premise-prompt">{ATTITUDE_PROMPT[bit.attitude]}</p>}
        <textarea
          rows={2} data-testid="premise-input" value={premise}
          onInput={(e) => setPremiseDraft((e.target as HTMLTextAreaElement).value)}
        />
        <SaveButton changed={premiseChanged} filled={premise.trim() !== ''} testid="premise-save" onSave={() => void actions.setPremise(premise)} />
      </Section>

      <Section title={T.fieldSetup}>
        <textarea
          rows={2} data-testid="setup-input" value={setup}
          onInput={(e) => setSetupDraft((e.target as HTMLTextAreaElement).value)}
        />
        <SaveButton changed={setupChanged} filled={setup.trim() !== ''} testid="setup-save" onSave={() => void actions.setSetup(setup)} />
      </Section>

      <Section title={T.fieldPunch}>
        <textarea
          rows={2} data-testid="punch-input" value={punch}
          onInput={(e) => setPunchDraft((e.target as HTMLTextAreaElement).value)}
        />
        <SaveButton
          changed={punchChanged} filled={punch.trim() !== ''} testid="punch-save"
          onSave={() => void actions.setPunch(punch, technique)}
        />
        <div class="chips" style="margin-top:12px">
          {PUNCH_TECHNIQUES.map((t) => (
            <button
              key={t}
              class={`chip small${technique === t ? ' on' : ''}`}
              data-testid={`technique-${t}`}
              onClick={() => void actions.setPunch(punch || bit.elements.punch?.text || '', t)}
            >
              {TECHNIQUE_LABEL[t]}
            </button>
          ))}
        </div>
      </Section>

      <Section title={T.fieldActOut}>
        <textarea
          rows={2} data-testid="actout-input" value={actOut}
          onInput={(e) => setActOutDraft((e.target as HTMLTextAreaElement).value)}
        />
        <SaveButton
          changed={actOutChanged} filled={actOut.trim() !== ''} testid="actout-save"
          onSave={() => void actions.setActOut(actOut, spaceWork)}
        />
        <label class="row" style="margin-top:10px">
          <input
            type="checkbox" checked={spaceWork}
            onChange={() => void actions.setActOut(actOut || bit.elements.actOut?.text || '', !spaceWork)}
          />
          <span class="hint" style="margin:0">{T.actOutSpaceWork}</span>
        </label>
      </Section>

      <Section title={T.voiceTitle}>
        <VoiceBlock clips={clips} onSave={onRecord} onDelete={onDeleteClip} hint={T.recHint} />
      </Section>

      <Section title={T.fieldDuration}>
        {/* Фишками, а не полем: на телефоне вбивать секунды клавиатурой —
            лишний шаг, а точность здесь всё равно приблизительная. */}
        <div class="chips">
          {DURATION_CHOICES.map((sec) => (
            <button
              key={sec}
              class={`chip small${bit.durationSec === sec ? ' on' : ''}`}
              data-testid={`duration-${sec}`}
              onClick={() => void actions.setDuration(bit.durationSec === sec ? null : sec)}
            >
              {sec < 60 ? `${sec} с` : `${sec / 60} мин`}
            </button>
          ))}
        </div>
        <p class="hint">{T.fieldDurationHint}</p>
      </Section>

      <Section title={T.fieldTags}>
        <div class="chips" style="margin-bottom:10px">
          {tags.map((x) => (
            <button
              key={x} class="chip small on" data-testid="tag-chip"
              onClick={() => void actions.setTags(tags.filter((y) => y !== x))}
            >
              {x} ×
            </button>
          ))}
        </div>
        <div class="row">
          <input
            class="grow" type="text" data-testid="tag-input" placeholder={T.tagPlaceholder} value={tag}
            onInput={(e) => setTag((e.target as HTMLInputElement).value)}
            onKeyDown={(e) => {
              if (e.key !== 'Enter' || !tag.trim()) return
              void actions.setTags([...tags, tag.trim()])
              setTag('')
            }}
          />
          <button
            class="btn" data-testid="tag-add" disabled={!tag.trim()}
            onClick={() => { void actions.setTags([...tags, tag.trim()]); setTag('') }}
          >
            +
          </button>
        </div>
      </Section>

      <div style="padding:0 12px 24px">
        <button class="btn ghost" onClick={onBack}>← {T.back}</button>
      </div>
    </div>
  )
}

export const workshopUndoLabels = UNDO_LABEL
