package dev.magicthemer.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.MainThread
import com.google.android.material.color.MaterialColors
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Lazy, bounded thumbnails. UI binding methods run on the main thread. */
class ThumbnailLoader(private val render: (String, Int) -> Bitmap?) {
    private data class Request(val key: String, val size: Int)
    private data class Binding(val request: Request, val token: Long)
    private data class Waiter(val view: WeakReference<ImageView>, val token: Long)
    private data class Pending(val token: Long, val waiters: MutableList<Waiter>, var future: Future<*>? = null)

    private val handler = Handler(Looper.getMainLooper())
    private val executor = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(96),
        { runnable -> Thread(runnable, "magic-thumbnail").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val cache = object : LruCache<Request, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: Request, value: Bitmap): Int = value.allocationByteCount
        // Eviction releases only the cache reference: visible ImageViews may
        // still own these bitmaps, so recycling here would break their drawing.
    }
    private val bindings = WeakHashMap<ImageView, Binding>()
    private val pending = hashMapOf<Request, Pending>()
    private var nextToken = 0L
    @Volatile private var closed = false

    @MainThread
    fun bind(view: ImageView, key: String, size: Int = 128) {
        checkMainThread()
        require(size in 32..1024) { "Thumbnail size must be between 32 and 1024 pixels." }
        val request = Request(key, size)
        if (bindings[view]?.request == request && !closed) return
        unbind(view)
        placeholder(view)
        if (closed || key.isBlank()) return
        val token = ++nextToken
        bindings[view] = Binding(request, token)
        cache.get(request)?.let { bitmap ->
            if (!bitmap.isRecycled) {
                show(view, bitmap)
                return
            }
            cache.remove(request)
        }
        val waiter = Waiter(WeakReference(view), token)
        pending[request]?.let {
            it.waiters += waiter
            return
        }
        val job = Pending(token, mutableListOf(waiter))
        pending[request] = job
        try {
            job.future = executor.submit {
                val bitmap = try { render(key, size) } catch (_: Exception) { null }
                if (!closed) handler.post { complete(request, job.token, bitmap) }
            }
        } catch (_: RejectedExecutionException) {
            pending.remove(request)
            // Rapid scrolling can fill the bounded queue; keep the visible
            // placeholder and permit the next binding to retry this thumbnail.
            bindings.remove(view)
        }
    }

    /** Stop an offscreen cell from receiving the result of its previous binding. */
    @MainThread
    fun unbind(view: ImageView) {
        checkMainThread()
        val binding = bindings.remove(view) ?: return
        val job = pending[binding.request] ?: return
        job.waiters.removeAll { it.view.get() == null || it.view.get() === view }
        if (job.waiters.isEmpty()) {
            pending.remove(binding.request)
            job.future?.cancel(false)
            executor.purge()
        }
    }

    @MainThread
    fun clear() {
        checkMainThread()
        for (job in pending.values) job.future?.cancel(false)
        pending.clear()
        executor.purge()
        for (view in bindings.keys.toList()) placeholder(view)
        bindings.clear()
        cache.evictAll()
        handler.removeCallbacksAndMessages(null)
    }

    @MainThread
    fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        clear()
        executor.shutdownNow()
    }

    private fun complete(request: Request, token: Long, bitmap: Bitmap?) {
        if (closed) return
        val job = pending[request] ?: return
        if (job.token != token) return
        pending.remove(request)
        if (bitmap == null || bitmap.isRecycled) return
        cache.put(request, bitmap)
        for (waiter in job.waiters) {
            val view = waiter.view.get() ?: continue
            val binding = bindings[view] ?: continue
            if (binding.request == request && binding.token == waiter.token) show(view, bitmap)
        }
    }

    private fun placeholder(view: ImageView) {
        val context = view.context
        val density = context.resources.displayMetrics.density
        val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHighest, Color.LTGRAY)
        val ink = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * density
            setColor(surface)
        }
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        val inset = (12 * density).toInt()
        view.setPadding(inset, inset, inset, inset)
        view.setImageResource(android.R.drawable.ic_menu_gallery)
        view.imageTintList = ColorStateList.valueOf(ink)
    }

    private fun show(view: ImageView, bitmap: Bitmap) {
        view.background = null
        view.imageTintList = null
        view.setPadding(0, 0, 0, 0)
        view.setImageBitmap(bitmap)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "Thumbnail binding must run on the main thread." }
    }
}
