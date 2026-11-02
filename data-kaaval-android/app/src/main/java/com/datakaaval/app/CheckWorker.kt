package com.datakaaval.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class CheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        try {
            AlertChecker.check(applicationContext)
        } catch (e: Exception) {
            // try again at the next run
        }
        return Result.success()
    }
}
