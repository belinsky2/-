package ru.punchline.model

/** Сводка по выступлению. */
data class GigStats(
    val bitCount: Int,
    val averageScore: Double,
    val laughsPerMinute: Double,
)

/** Сколько материала на каком этапе. Показывает, где именно затык. */
data class Funnel(val counts: Map<BitStatus, Int>) {
    fun count(status: BitStatus): Int = counts[status] ?: 0
}

object Metrics {

    /**
     * Laugh score выступления. Упражнение 35 у Картер: считать смех по записи,
     * а не по ощущению — ощущение врёт в обе стороны.
     */
    fun gigStats(performances: List<BitPerformance>, actualDurationSec: Int?): GigStats {
        if (performances.isEmpty()) return GigStats(0, 0.0, 0.0)
        val average = performances.sumOf { it.result.score }.toDouble() / performances.size
        val laughs = performances.count { it.result.score >= LaughResult.LAUGH.score }
        val minutes = actualDurationSec?.takeIf { it > 0 }?.let { it / 60.0 }
        return GigStats(
            bitCount = performances.size,
            averageScore = average,
            laughsPerMinute = if (minutes == null) 0.0 else laughs / minutes,
        )
    }

    /** Средний результат шутки по всем её выходам. */
    fun averageScoreByBit(performances: List<BitPerformance>): Map<Id, Double> =
        performances.groupBy { it.bitId }
            .mapValues { (_, list) -> list.sumOf { it.result.score }.toDouble() / list.size }

    fun funnel(bits: List<Bit>): Funnel =
        Funnel(bits.filterNot { it.meta.isDeleted }.groupingBy { it.status }.eachCount())

    /** Сколько минут готового материала набрано. */
    fun polishedMinutes(bits: List<Bit>): Double =
        bits.filter { it.status == BitStatus.POLISHED && !it.meta.isDeleted }
            .sumOf { it.durationSec ?: 0 } / 60.0

    /**
     * Доля шуток с act-out среди тех, что уже выносились на сцену.
     * Картер настаивает: играть, а не рассказывать, — и перекос стоит видеть.
     */
    fun actOutRatio(bits: List<Bit>): Double {
        val onStage = bits.filter {
            !it.meta.isDeleted && it.status in setOf(BitStatus.TESTED, BitStatus.POLISHED)
        }
        if (onStage.isEmpty()) return 0.0
        return onStage.count { it.elements.actOut != null }.toDouble() / onStage.size
    }

    /** Распределение по отношениям: показывает, что автор застрял на одном. */
    fun attitudeSpread(bits: List<Bit>): Map<Attitude, Int> =
        bits.filterNot { it.meta.isDeleted }
            .mapNotNull { it.attitude }
            .groupingBy { it }
            .eachCount()
}
