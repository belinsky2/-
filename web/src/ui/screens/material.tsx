import { useState } from 'preact/hooks'
import type { Bit, Topic } from '../../domain/domain'
import type { Id } from '../../domain/identity'
import { searchBits } from '../../domain/markdown'
import { STATUS_LABEL, T } from '../labels'
import { InboxScreen } from './inbox'
import { TopicsScreen } from './topics'

interface Props {
  bits: readonly Bit[]
  topics: readonly Topic[]
  onAddBit: (title: string) => Promise<void>
  onAddTopic: (title: string) => Promise<void>
  onDeleteTopic: (id: Id) => Promise<void>
  onOpenBit: (id: Id) => void
}

/**
 * Вкладка «Материал»: поиск сверху, под ним входящие и темы.
 * Пока в поиске что-то набрано, вместо списков показываются находки.
 */
export function MaterialScreen(
  { bits, topics, onAddBit, onAddTopic, onDeleteTopic, onOpenBit }: Props,
) {
  const [query, setQuery] = useState('')
  const found = query.trim() ? searchBits(bits, query) : null

  return (
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
              <li key={b.id} class="item" onClick={() => onOpenBit(b.id)}>
                <div class="grow"><div class="title">{b.title}</div></div>
                <span class="badge">{STATUS_LABEL[b.status]}</span>
              </li>
            ))}
          </ul>
        )
      ) : (
        <>
          <InboxScreen
            bits={bits}
            topicTitle={(id) => topics.find((t) => t.id === id)?.title ?? T.noTopic}
            onAdd={onAddBit}
            onOpen={onOpenBit}
          />
          <TopicsScreen
            topics={topics}
            bitCount={(id) => bits.filter((b) => b.topicId === id).length}
            onAdd={onAddTopic}
            onDelete={onDeleteTopic}
          />
        </>
      )}
    </div>
  )
}
