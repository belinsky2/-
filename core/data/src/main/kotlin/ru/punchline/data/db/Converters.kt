package ru.punchline.data.db

import androidx.room.TypeConverter
import ru.punchline.model.Attitude
import ru.punchline.model.BitStatus
import ru.punchline.model.GigType
import ru.punchline.model.LaughResult
import ru.punchline.model.PunchTechnique
import ru.punchline.model.SetListRole

/**
 * Перечисления хранятся строками, а не порядковыми номерами: порядок констант
 * в коде однажды поменяется, и база молча начнёт означать другое.
 */
class Converters {
    @TypeConverter fun attitudeTo(v: Attitude?): String? = v?.name
    @TypeConverter fun attitudeFrom(v: String?): Attitude? = v?.let(Attitude::valueOf)

    @TypeConverter fun statusTo(v: BitStatus): String = v.name
    @TypeConverter fun statusFrom(v: String): BitStatus = BitStatus.valueOf(v)

    @TypeConverter fun techniqueTo(v: PunchTechnique?): String? = v?.name
    @TypeConverter fun techniqueFrom(v: String?): PunchTechnique? = v?.let(PunchTechnique::valueOf)

    @TypeConverter fun roleTo(v: SetListRole): String = v.name
    @TypeConverter fun roleFrom(v: String): SetListRole = SetListRole.valueOf(v)

    @TypeConverter fun gigTypeTo(v: GigType): String = v.name
    @TypeConverter fun gigTypeFrom(v: String): GigType = GigType.valueOf(v)

    @TypeConverter fun laughTo(v: LaughResult): String = v.name
    @TypeConverter fun laughFrom(v: String): LaughResult = LaughResult.valueOf(v)

    @TypeConverter fun tagsTo(v: List<String>): String = v.joinToString(TAG_SEPARATOR)
    @TypeConverter fun tagsFrom(v: String): List<String> =
        if (v.isEmpty()) emptyList() else v.split(TAG_SEPARATOR)

    private companion object {
        /** Разделитель единиц U+001F: такого символа не бывает в тексте добивки. */
        const val TAG_SEPARATOR = "\u001F"
    }
}
