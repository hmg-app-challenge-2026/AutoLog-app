package kr.htaxlog.vehicle.report

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** 월말 자동 발송 확인 (F-21). 하루 두 번 [MonthlySender.runOnce]를 부른다. 보낼 게 없으면 바로 끝난다. */
class MonthlyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        host?.runOnce()
        return Result.success()
    }

    companion object {
        private const val NAME = "htaxlog-monthly-report"

        /** 조립 지점이 넣는다. */
        @Volatile var host: MonthlySender? = null

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MonthlyReportWorker>(12, TimeUnit.HOURS).build(),
            )
        }
    }
}
