package com.datakaaval.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class AlertActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val p = Pop(
            intent.getStringExtra("title") ?: "",
            intent.getStringExtra("body") ?: "",
            intent.getStringExtra("nums") ?: "",
            intent.getBooleanExtra("over", true),
            intent.getFloatExtra("pct", 100f),
            intent.getBooleanExtra("ta", false)
        )
        val view = PopupUi.build(this, p, { finish() }) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        setContentView(view)
    }
}
