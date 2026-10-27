package dev.magicthemer.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

data class VisualItem(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val imageKey: String = key,
    val comparisonKey: String? = null,
    val status: String = "",
    val needsIcon: Boolean = false,
)

/** Native list rows or flat icon tiles with images loaded only for visible cells. */
class VisualAdapter(
    private val context: Context,
    private val loader: ThumbnailLoader,
    private val grid: Boolean,
    private val onClick: (VisualItem) -> Unit,
) : RecyclerView.Adapter<VisualAdapter.Holder>() {
    class Holder(
        val card: MaterialCardView,
        val image: ImageView,
        val title: TextView,
        val subtitle: TextView,
        val comparison: ImageView?,
        val status: TextView,
    ) : RecyclerView.ViewHolder(card) {
        var currentItem: VisualItem? = null
    }

    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<VisualItem>() {
        override fun areItemsTheSame(oldItem: VisualItem, newItem: VisualItem): Boolean = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: VisualItem, newItem: VisualItem): Boolean = oldItem == newItem
    })

    /** [committed] runs once the new list is displayed, e.g. to show the best match first. */
    fun submit(items: List<VisualItem>, committed: (() -> Unit)? = null) {
        differ.submitList(items.toList()) { committed?.invoke() }
    }

    override fun getItemCount(): Int = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val card = MaterialCardView(context).apply {
            radius = dp(if (grid) 18 else 14).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            isClickable = true
            isFocusable = true
            minimumHeight = dp(if (grid) 128 else 64)
            setCardBackgroundColor(color(if (grid) com.google.android.material.R.attr.colorSurfaceContainerLow else com.google.android.material.R.attr.colorSurface))
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(if (grid) 5 else 0), dp(4), dp(if (grid) 5 else 0), dp(4))
            }
        }
        val image = icon(if (grid) 56 else 48)
        val title = text(if (grid) 15f else 16f, com.google.android.material.R.attr.colorOnSurface).apply {
            maxLines = if (grid) 2 else 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val subtitle = text(12f, com.google.android.material.R.attr.colorOnSurfaceVariant).apply {
            maxLines = if (grid) 3 else 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val status = text(12f, com.google.android.material.R.attr.colorOnSecondaryContainer).apply {
            maxLines = 2
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        val comparison: ImageView?
        if (grid) {
            comparison = null
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(12), dp(16), dp(12), dp(14))
            }
            body.addView(image, LinearLayout.LayoutParams(dp(56), dp(56)))
            title.gravity = Gravity.CENTER
            subtitle.gravity = Gravity.CENTER
            body.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            body.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
            card.addView(body)
        } else {
            comparison = icon(48)
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            body.addView(image, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
            val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(title)
            // Badge and package share a line so more rows fit on screen.
            val details = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            details.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
            details.addView(subtitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            labels.addView(details, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            body.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(comparison, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(12) })
            card.addView(body)
        }
        val holder = Holder(card, image, title, subtitle, comparison, status)
        card.setOnClickListener { holder.currentItem?.let(onClick) }
        ViewCompat.setAccessibilityDelegate(card, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        })
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = differ.currentList[position]
        holder.currentItem = item
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle
        holder.subtitle.visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
        holder.status.text = item.status
        holder.status.visibility = if (item.status.isBlank() || grid) View.GONE else View.VISIBLE
        holder.status.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(color(if (item.needsIcon) com.google.android.material.R.attr.colorErrorContainer else com.google.android.material.R.attr.colorSecondaryContainer))
        }
        holder.status.setTextColor(color(if (item.needsIcon) com.google.android.material.R.attr.colorOnErrorContainer else com.google.android.material.R.attr.colorOnSecondaryContainer))
        holder.card.contentDescription = buildString {
            append(item.title)
            if (item.status.isNotBlank()) append(", ").append(item.status)
            if (item.subtitle.isNotBlank()) append(", ").append(item.subtitle)
            if (item.comparisonKey != null && !grid) append(", original and themed icons")
        }
        loader.bind(holder.image, item.imageKey, 128)
        holder.comparison?.let { image ->
            val key = item.comparisonKey
            image.visibility = if (key == null) View.GONE else View.VISIBLE
            if (key == null) {
                loader.unbind(image)
                image.setImageDrawable(null)
            } else loader.bind(image, key, 128)
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.currentItem = null
        loader.unbind(holder.image)
        holder.comparison?.let(loader::unbind)
        super.onViewRecycled(holder)
    }

    private fun icon(size: Int): ImageView = ImageView(context).apply {
        minimumWidth = dp(size)
        minimumHeight = dp(size)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun text(size: Float, attribute: Int): TextView = TextView(context).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        textSize = size
        setTextColor(color(attribute))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun color(attribute: Int): Int = MaterialColors.getColor(context, attribute, Color.GRAY)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
