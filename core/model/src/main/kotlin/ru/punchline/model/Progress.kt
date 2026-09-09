package ru.punchline.model

/**
 * Сводка прогресса. Считается из данных, а не из ощущений: в этом весь смысл
 * связки «выступление → отметки → пересборка акта».
 */
data class Progress(
    val funnel: Funnel,
    val polishedMinutes: Double,
    val goalMinutes: Int,
    val actOutRatio: Double,
    val attitudeSpread: Map<Attitude, Int>,
    val gigsLast30Days: Int,
    val streakDays: Int,
) {
    val goalRatio: Double
        get() = if (goalMinutes <= 0) 0.0 else (polishedMinutes / goalMinutes).coerceAtMost(1.0)

    /**
     * Где именно затык. Показывать все проценты воронки бессмысленно —
     * полезен один вывод: на каком шаге материал застревает.
     */
    val bottleneck: BitStatus?
        get() = listOf(BitStatus.SEED, BitStatus.PREMISE, BitStatus.DRAFT, BitStatus.TESTED)
            .maxByOrNull { funnel.count(it) }
            ?.takeIf { funnel.count(it) > 0 }
}
