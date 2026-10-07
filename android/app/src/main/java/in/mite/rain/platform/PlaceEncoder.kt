package `in`.mite.rain.platform

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import java.security.MessageDigest

class PlaceEncoder(context: Context) : AutoCloseable {
    companion object {
        const val MODEL_ID = "mobilenet-v3-small-bbbb4c51"
        const val SHA256 = "bbbb4c51a55a53905af1daec995ca1aae355046f8839bb8c9f5ce9271394bc40"
    }
    private val embedder: ImageEmbedder
    init {
        val hash = context.assets.open("place_embedder.tflite").use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArray(8192)
            while(true) { val count=stream.read(bytes); if(count<0) break; digest.update(bytes,0,count) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        require(hash == SHA256) { "Visual model checksum mismatch." }
        embedder = ImageEmbedder.createFromOptions(context, ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("place_embedder.tflite").build())
            .setL2Normalize(true).build())
    }
    fun encode(bitmap: Bitmap): List<Double> {
        // MPImage.close() recycles its owned bitmap. Keep the review/OCR source alive.
        val image = BitmapImageBuilder(bitmap.copy(Bitmap.Config.ARGB_8888,false)).build()
        try { return embedder.embed(image).embeddingResult().embeddings().first().floatEmbedding().map { it.toDouble() } }
        finally { image.close() }
    }
    override fun close() { embedder.close() }
}
