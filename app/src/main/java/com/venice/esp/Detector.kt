package com.venice.esp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.net.URL

class Detector(private val ctx: Context) {

    private var od: ObjectDetector? = null
    private val lock = Mutex()
    @Volatile var ready = false
        private set
    @Volatile var backend = "none"

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        val f = modelFile() ?: return@withContext false

        for (delegate in listOf(BaseOptions.Delegate.NNAPI, BaseOptions.Delegate.CPU)) {
            try {
                val base = BaseOptions.builder()
                    .setNumThreads(4)
                    .setDelegate(delegate)
                    .build()
                val opts = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(base)
                    .setMaxResults(12)
                    .setScoreThreshold(0.30f)
                    .build()
                od = ObjectDetector.createFromFileAndOptions(f, opts)
                backend = delegate.name
                ready = true
                return@withContext true
            } catch (t: Throwable) {
                od?.close()
                od = null
            }
        }
        false
    }

    private fun modelFile(): File? = try {
        val f = File(ctx.filesDir, "det.tflite")
        if (!f.exists() || f.length() < 1_000_000L) {
            URL(MODEL).openConnection().apply {
                connectTimeout = 20000
                readTimeout = 60000
            }.getInputStream().use { input ->
                f.outputStream().use { out -> input.copyTo(out) }
            }
        }
        f
    } catch (t: Throwable) {
        null
    }

    suspend fun detect(bmp: Bitmap): List<RectF> = lock.withLock {
        val d = od ?: return emptyList()
        val img = TensorImage.fromBitmap(bmp)
        d.detect(img)
            .filter { it.categories.firstOrNull()?.label == "person" }
            .filter { (it.categories.firstOrNull()?.score ?: 0f) >= Cfg.threshold }
            .map { it.boundingBox }
    }

    fun close() {
        od?.close()
        od = null
        ready = false
        backend = "none"
    }

    companion object {
        private const val MODEL =
            "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite"
    }
}
