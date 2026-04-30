package com.novarehab.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class CommPageAdapter(
    private val context: Context,
    private val items: List<CommunicationItem>,
    private val pageSize: Int = 8,
    private val getLang: () -> String,
    private val onItemSelected: (CommunicationItem) -> Unit
) : RecyclerView.Adapter<CommPageAdapter.PageViewHolder>() {

    private val safePageSize = if (pageSize in setOf(6, 8, 9, 12)) pageSize else 9

    private val gridColumns = when (safePageSize) {
        6 -> 2
        8 -> 2
        9 -> 3
        else -> 3
    }

    private val gridRows = when (safePageSize) {
        6 -> 3
        8 -> 4
        9 -> 3
        else -> 4
    }

    val pageCount get() = maxOf(1, Math.ceil(items.size.toDouble() / safePageSize).toInt())

    override fun getItemCount() = pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val grid = GridLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            columnCount = gridColumns
            rowCount = gridRows
        }
        return PageViewHolder(grid)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val grid = holder.grid
        grid.removeAllViews()

        val start = position * safePageSize
        val end = minOf(start + safePageSize, items.size)

        items.subList(start, end).forEach { item ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(0xFF16213e.toInt())

                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(3, 3, 3, 3)
                }

                val img = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0
                    ).apply {
                        weight = 1f
                        setMargins(8, 8, 8, 4)
                    }

                    val customFile = File(context.getExternalFilesDir(null), "icons/${item.id}.png")
                    if (customFile.exists()) {
                        setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
                    } else {
                        setImageResource(item.iconRes)
                    }
                }
                addView(img)

                addView(TextView(context).apply {
                    text = displayLabel(item)
                    textSize = when (safePageSize) {
                        12 -> 13f
                        9 -> 14f
                        else -> 15f
                    }
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    maxLines = 2
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(6, 2, 6, 10)
                    }
                })

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onItemSelected(item)
                }
            }

            grid.addView(cell)
        }
    }

    private fun displayLabel(item: CommunicationItem): String {
        return item.label
            .replace(",", "")
            .replace(".", "")
            .replace(" SEM", "")
            .trim()
            .uppercase()
    }

    class PageViewHolder(val grid: GridLayout) : RecyclerView.ViewHolder(grid)
}
