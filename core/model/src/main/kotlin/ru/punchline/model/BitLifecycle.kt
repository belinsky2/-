package ru.punchline.model

/**
 * Правила жизненного цикла шутки.
 *
 * Ключевая идея: продвижение вперёд не назначается вручную, а следует из того,
 * что у шутки появилось (премиса, панч) и как она отработала в зале. Иначе
 * раздел «Мой акт» наполняется по ощущениям, а не по данным.
 */
object BitLifecycle {

    /** Сколько выступлений с результатом не ниже [MIN_RESULT] нужно, чтобы шутка стала отшлифованной. */
    const val PERFORMANCES_TO_POLISH: Int = 2

    /** Планка «зашло». Хмык не считается. */
    val MIN_RESULT: LaughResult = LaughResult.LAUGH

    /** Сколько подряд провалов, прежде чем предложить отложить шутку. */
    const val SILENCES_TO_SUGGEST_PARKING: Int = 3

    private val forward: Map<BitStatus, Set<BitStatus>> = mapOf(
        BitStatus.SEED to setOf(BitStatus.PREMISE),
        BitStatus.PREMISE to setOf(BitStatus.DRAFT),
        BitStatus.DRAFT to setOf(BitStatus.TESTED),
        BitStatus.TESTED to setOf(BitStatus.POLISHED),
        BitStatus.POLISHED to emptySet(),
        BitStatus.PARKED to emptySet(),
        BitStatus.RETIRED to emptySet(),
    )

    private val archived = setOf(BitStatus.PARKED, BitStatus.RETIRED)

    /**
     * Разрешён ли переход. Отложить или списать можно с любого состояния,
     * вернуть из архива — тоже: «архив» это состояние, а не помойка.
     */
    fun canTransition(from: BitStatus, to: BitStatus): Boolean = when {
        from == to -> false
        to in archived -> true
        from in archived -> true
        else -> to in forward.getValue(from)
    }

    /**
     * Состояние, которого шутка заслуживает по своему содержимому и истории зала.
     * Никогда не понижает статус сама: решение отложить шутку остаётся за автором.
     */
    fun deservedStatus(bit: Bit, performances: List<BitPerformance>): BitStatus {
        if (bit.status in archived) return bit.status

        val successes = performances.count { it.result.score >= MIN_RESULT.score }
        val content = contentStatus(bit)

        return when {
            successes >= PERFORMANCES_TO_POLISH && content == BitStatus.DRAFT -> BitStatus.POLISHED
            performances.isNotEmpty() && content == BitStatus.DRAFT -> BitStatus.TESTED
            else -> content
        }
    }

    /** До какого состояния шутка дотягивает по одному только своему содержимому. */
    private fun contentStatus(bit: Bit): BitStatus {
        val e = bit.elements
        val hasPremise = !e.premise.isNullOrBlank() && bit.attitude != null
        val hasPunch = !e.punch?.text.isNullOrBlank()
        return when {
            hasPremise && hasPunch -> BitStatus.DRAFT
            hasPremise -> BitStatus.PREMISE
            else -> BitStatus.SEED
        }
    }

    /**
     * Подсказки, которые приложение показывает автору. Именно подсказки:
     * приложение не переписывает материал само.
     */
    fun hints(
        bit: Bit,
        performances: List<BitPerformance>,
        nowMillis: Long,
    ): List<BitHint> {
        val hints = mutableListOf<BitHint>()

        // Разбирая выступление, автор отмечает десяток шуток подряд — все они
        // получают одну и ту же миллисекунду. Логические часы разрывают ничью,
        // иначе «свежая серия провалов» определяется порядком в списке.
        val recent = performances.sortedWith(
            compareByDescending<BitPerformance> { it.meta.updatedAt }.thenByDescending { it.meta.lamport }
        )
        val leadingSilences = recent.takeWhile { it.result == LaughResult.SILENCE }.size
        if (leadingSilences >= SILENCES_TO_SUGGEST_PARKING) {
            hints += BitHint.RewriteOrPark(leadingSilences)
        }

        if (bit.status == BitStatus.TESTED && bit.elements.actOut == null) {
            hints += BitHint.MissingActOut
        }

        val ageDays = (nowMillis - bit.meta.updatedAt) / MILLIS_PER_DAY
        if (bit.status == BitStatus.DRAFT && ageDays >= STUCK_DRAFT_DAYS) {
            hints += BitHint.StuckInDraft(ageDays)
        }
        if (bit.status == BitStatus.POLISHED && performances.isEmpty() && ageDays >= UNUSED_POLISHED_DAYS) {
            hints += BitHint.UnusedPolished(ageDays)
        }

        return hints
    }

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    private const val STUCK_DRAFT_DAYS = 30L
    private const val UNUSED_POLISHED_DAYS = 90L
}

/** Подсказка по конкретной шутке. Текст формулирует UI, здесь только повод. */
sealed interface BitHint {
    data class RewriteOrPark(val silencesInARow: Int) : BitHint
    data object MissingActOut : BitHint
    data class StuckInDraft(val days: Long) : BitHint
    data class UnusedPolished(val days: Long) : BitHint
}
