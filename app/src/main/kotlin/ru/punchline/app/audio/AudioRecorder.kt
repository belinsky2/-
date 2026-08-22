package ru.punchline.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import ru.punchline.vault.BlobStore
import ru.punchline.vault.StoredBlob

/**
 * Диктофон. Стендап пишется вслух, поэтому запись — первичный ввод, а текст
 * лишь производная от неё. Аудио сохраняется всегда, даже если распознавание
 * не сработало: потерять голосовую заготовку хуже, чем остаться без текста.
 */
class AudioRecorder(
    private val context: Context,
    private val blobs: BlobStore,
) {
    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt: Long = 0

    val isRecording: Boolean get() = recorder != null

    fun start() {
        if (isRecording) return
        val file = File.createTempFile("rec", ".m4a", context.cacheDir)
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        created.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(BITRATE)
            setAudioSamplingRate(SAMPLE_RATE)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = created
        target = file
        startedAt = System.currentTimeMillis()
    }

    /**
     * Останавливает запись и кладёт её в хранилище по хешу содержимого.
     * Возвращает null, если писать было нечего или запись сорвалась, —
     * вызывающий код обязан этот случай обработать, а не считать успехом.
     */
    fun stop(): Recording? {
        val active = recorder ?: return null
        val file = target
        recorder = null
        target = null

        val stoppedCleanly = runCatching { active.stop() }.isSuccess
        active.release()

        if (file == null || !stoppedCleanly || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }

        val stored = blobs.put(file)
        file.delete()
        return Recording(stored, ((System.currentTimeMillis() - startedAt) / 1000).toInt())
    }

    /** Сброс без сохранения — например, когда экран закрыли, не дописав. */
    fun cancel() {
        val active = recorder ?: return
        recorder = null
        runCatching { active.stop() }
        active.release()
        target?.delete()
        target = null
    }

    private companion object {
        const val BITRATE = 128_000
        const val SAMPLE_RATE = 44_100
    }
}

data class Recording(val blob: StoredBlob, val durationSec: Int)
