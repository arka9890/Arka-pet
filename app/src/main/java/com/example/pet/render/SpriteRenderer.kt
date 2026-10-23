package com.example.pet.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.pet.model.Pet
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** A frame's source rectangle inside the sprite sheet, in pixels. */
data class FrameRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** Frame timing, kept free of Android types so it can be unit-tested on the JVM. */
data class SpriteAnimation(
    val frames: List<FrameRect>,
    val fps: Float,
    val loop: Boolean = true,
) {
    init {
        require(frames.isNotEmpty()) { "animation needs at least one frame" }
        require(fps > 0f) { "fps must be positive, was $fps" }
    }

    /** One full pass through the frames, in seconds. */
    val duration: Float get() = frames.size / fps

    /** Index of the frame visible [elapsedSeconds] into the animation. */
    fun frameAt(elapsedSeconds: Float): Int {
        val raw = (maxOf(0f, elapsedSeconds) * fps).toInt()
        return if (loop) raw % frames.size else raw.coerceAtMost(frames.lastIndex)
    }
}

/**
 * Parsed `<pets>/<id>/sprite.json`:
 * ```
 * {"image": "sheet.png", "fps": 8, "loop": true,
 *  "frames": [{"x": 0, "y": 0, "w": 32, "h": 32}, ...]}
 * ```
 * `loop` defaults to true; all other fields are required. Frame rects are in sheet pixels.
 */
data class SpriteSheetConfig(
    val image: String,
    val frames: List<FrameRect>,
    val fps: Float,
    val loop: Boolean,
) {
    fun animation(): SpriteAnimation = SpriteAnimation(frames, fps, loop)

    companion object {
        const val FILE_NAME = "sprite.json"

        fun parse(json: String): SpriteSheetConfig {
            val root: JsonObject = try {
                JsonParser.parseString(json).asJsonObject
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("sprite config must be a JSON object", e)
            }
            val image = root.get("image")?.asString
                ?: throw IllegalArgumentException("sprite config needs an 'image'")
            require(image.isNotBlank()) { "sprite config 'image' must not be blank" }
            require(!File(image).isAbsolute && ".." !in image.split('/', '\\')) {
                "sprite config 'image' must stay inside the pet directory: $image"
            }

            val frameArray = root.get("frames")?.asJsonArray
                ?: throw IllegalArgumentException("sprite config needs a 'frames' array")
            val frames = frameArray.map { element ->
                val frame = element.asJsonObject
                FrameRect(
                    x = frame.get("x").asInt,
                    y = frame.get("y").asInt,
                    width = frame.get("w").asInt,
                    height = frame.get("h").asInt,
                )
            }
            require(frames.isNotEmpty()) { "sprite config needs at least one frame" }
            require(frames.all { it.x >= 0 && it.y >= 0 && it.width > 0 && it.height > 0 }) {
                "sprite frames need x/y >= 0 and w/h > 0"
            }

            val fps = root.get("fps")?.asFloat
                ?: throw IllegalArgumentException("sprite config needs 'fps'")
            val loop = root.get("loop")?.asBoolean ?: true
            return SpriteSheetConfig(image, frames, fps, loop)
        }
    }
}

/**
 * Draws frame-based sprite pets from `<petsDir>/<pet.id>/sprite.json` plus the image it names.
 * The sheet is scaled to fill the canvas; the overlay window already has the pet's aspect ratio.
 */
class SpriteRenderer(private val petsDir: File) : PetRenderer {

    private var sheet: Bitmap? = null
    private var animation: SpriteAnimation? = null
    private var elapsed = 0f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()

    override fun load(pet: Pet) {
        val petDir = File(petsDir, pet.id).canonicalFile
        val configFile = File(petDir, SpriteSheetConfig.FILE_NAME)
        check(configFile.isFile) { "sprite pet '${pet.id}' is missing ${configFile.path}" }
        val config = SpriteSheetConfig.parse(configFile.readText())

        val imageFile = File(petDir, config.image).canonicalFile
        check(imageFile.path.startsWith(petDir.path + File.separator)) {
            "sprite config 'image' points outside the pet directory: ${config.image}"
        }
        val bitmap = BitmapFactory.decodeFile(imageFile.path)
            ?: throw IllegalStateException("cannot decode sprite sheet ${imageFile.path}")
        config.frames.forEach { frame ->
            check(frame.x + frame.width <= bitmap.width && frame.y + frame.height <= bitmap.height) {
                "frame ${frame.x},${frame.y} ${frame.width}x${frame.height} is outside " +
                    "the ${bitmap.width}x${bitmap.height} sheet"
            }
        }

        sheet?.recycle()
        sheet = bitmap
        animation = config.animation()
        elapsed = 0f
    }

    override fun update(dt: Float) {
        val anim = animation ?: return
        if (dt <= 0f) return
        elapsed = if (anim.loop) {
            (elapsed + dt) % anim.duration
        } else {
            minOf(elapsed + dt, anim.duration)
        }
    }

    override fun draw(canvas: Canvas) {
        val bitmap = sheet ?: return
        val anim = animation ?: return
        val frame = anim.frames[anim.frameAt(elapsed)]
        src.set(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height)
        dst.set(0, 0, canvas.width, canvas.height)
        canvas.drawBitmap(bitmap, src, dst, paint)
    }

    override fun onTap() = Unit // Tap reactions belong to the idle state machine (Task 8).
}
