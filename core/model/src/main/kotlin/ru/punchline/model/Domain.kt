package ru.punchline.model

/**
 * Отношение к теме — то, с чего по Картер начинается шутка. Именно оно
 * превращает тему в премису: «самое [attitude] в [теме] — это...».
 */
enum class Attitude { HARD, WEIRD, SCARY, STUPID }

/** Техника, которой сделан панч. */
enum class PunchTechnique { MIX, TURN, LIST_OF_THREE, SELF_MOCKING, OTHER }

/**
 * Состояние шутки. Цифровой эквивалент перекладывания листка из секции
 * «Jokes in Progress» в секцию «My Act».
 *
 * Состояния «стоит в сет-листе» здесь нет намеренно: оно выводится из связи
 * с сет-листом, а хранимая копия связи неизбежно с ней разойдётся.
 */
enum class BitStatus { SEED, PREMISE, DRAFT, TESTED, POLISHED, PARKED, RETIRED }

/** Реакция зала на конкретную шутку на конкретном выступлении. */
enum class LaughResult(val score: Int) {
    SILENCE(0),
    CHUCKLE(1),
    LAUGH(2),
    BIG_LAUGH(3),
    APPLAUSE_BREAK(4),
}

/** Роль номера в сет-листе. */
enum class SetListRole { OPENER, BODY, CLOSER, CALLBACK }

/** Тип выхода: от репетиции дома до платного концерта. */
enum class GigType { REHEARSAL, OPEN_MIC, SHOWCASE, PAID }

/** Тема, из которой растут шутки. */
data class Topic(
    val id: Id,
    val title: String,
    val passionScore: Int = 0,
    val isCore: Boolean = false,
    val meta: SyncMeta,
) {
    init { require(passionScore in 0..10) { "passionScore вне 0..10: $passionScore" } }
}

/** Панч: текст плюс техника, которой он сделан. */
data class Punch(val text: String, val technique: PunchTechnique)

/** Act-out: шутку надо сыграть, а не рассказать. */
data class ActOut(
    val text: String,
    val hasSpaceWork: Boolean = false,
    val audioHash: String? = null,
)

/** Структурированное тело шутки. */
data class BitElements(
    val premise: String? = null,
    val setup: String? = null,
    val punch: Punch? = null,
    val actOut: ActOut? = null,
    val tags: List<String> = emptyList(),
    val callbackTo: Id? = null,
)

/** Единица материала. */
data class Bit(
    val id: Id,
    val topicId: Id?,
    val title: String,
    val status: BitStatus,
    val attitude: Attitude? = null,
    val elements: BitElements = BitElements(),
    val durationSec: Int? = null,
    val meta: SyncMeta,
)

/** Позиция в сет-листе. */
data class SetListItem(
    val id: Id,
    val bitId: Id,
    val order: Int,
    val role: SetListRole,
    val plannedDurationSec: Int?,
)

/** Сет-лист под конкретный хронометраж. */
data class SetList(
    val id: Id,
    val title: String,
    val targetDurationSec: Int,
    val items: List<SetListItem> = emptyList(),
    val meta: SyncMeta,
)

/** Выступление или прогон. */
data class Gig(
    val id: Id,
    val setListId: Id?,
    val type: GigType,
    val venue: String,
    val dateMillis: Long,
    val actualDurationSec: Int? = null,
    val meta: SyncMeta,
)

/** Как конкретная шутка отработала на конкретном выступлении. */
data class BitPerformance(
    val id: Id,
    val gigId: Id,
    val bitId: Id,
    val result: LaughResult,
    val note: String? = null,
    val meta: SyncMeta,
)
