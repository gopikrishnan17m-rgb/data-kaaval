package com.datakaaval.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlertActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val title = intent.getStringExtra("title") ?: ""
        val body = intent.getStringExtra("body") ?: ""
        val nums = intent.getStringExtra("nums") ?: ""
        val over = intent.getBooleanExtra("over", true)
        val col = if (over) Color.parseColor("#FF5D6C") else Color.parseColor("#FBBF24")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0F1C"))
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        val badge = TextView(this).apply {
            text = "!"
            textSize = 56f
            setTextColor(col)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(8), col)
            }
            layoutParams = LinearLayout.LayoutParams(dp(130), dp(130)).apply { bottomMargin = dp(24) }
        }

        val t = TextView(this).apply {
            text = title
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val b = TextView(this).apply {
            text = body
            textSize = 17f
            setTextColor(Color.parseColor("#B5C0DA"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        val n = TextView(this).apply {
            text = nums
            textSize = 24f
            setTextColor(col)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(28))
        }

        val ok = Button(this).apply {
            text = "OK"
            setOnClickListener { finish() }
        }
        val open = Button(this).apply {
            text = "Data Kaaval"
            setOnClickListener {
                startActivity(Intent(this@AlertActivity, MainActivity::class.java))
                finish()
            }
        }
        val lp = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) } }

        root.addView(badge)
        root.addView(t)
        root.addView(b)
        root.addView(n)
        root.addView(ok, lp())
        root.addView(open, lp())
        setContentView(root)
    }
}
