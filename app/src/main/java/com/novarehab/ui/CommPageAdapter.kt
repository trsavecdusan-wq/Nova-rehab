package com.novarehab.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.novarehab.R
import com.novarehab.utils.IconTextManager
import java.io.File

class CommPageAdapter(
    private val context: Context,
    private val items: List<Triple<String, Int, Pair<String, String>>>,
    private val getLang: () -> String,
    private val onSpeak: (String) -> Unit
) : RecyclerView.Adapter<CommPageAdapter.PageViewHolder>() {

    private val iconMgr = IconTextManager(context)
    private val pageSize = 12
    val pageCount get() = maxOf(1, Math.ceil(items.size.toDouble() / pageSize).toInt())

    override fun getItemCount() = pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val grid = GridLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            columnCount = 3
            rowCount = 4
        }
        return PageViewHolder(grid)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val grid = holder.grid
        grid.removeAllViews()
        val start = position * pageSize
        val end = minOf(start + pageSize, items.size)

        items.subList(start, end).forEach { (id, iconRes, speeches) ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(0xFF16213e.toInt())
                val lp = GridLayout.LayoutParams().apply {
                    width = 0; height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(3, 3, 3, 3)
                }
                layoutParams = lp

                val img = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val imgLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0
                    ).apply { weight = 1f; setMargins(8, 8, 8, 4) }
                    layoutParams = imgLp
                    val customFile = File(context.getExternalFilesDir(null), "icons/$id.png")
                    if (customFile.exists())
                        setImageBitmap(BitmapFactory.decodeFile(customFile.absolutePath))
                    else
                        setImageResource(iconRes)
                }
                addView(img)

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val custom = iconMgr.getText(id, getLang())
                    val speech = if (custom.isNotEmpty()) custom
                        else if (getLang() == "uk") speeches.second else speeches.first
                    onSpeak(speech)
                }
            }
            grid.addView(cell)
        }
    }

    class PageViewHolder(val grid: GridLayout) : RecyclerView.ViewHolder(grid)
}
