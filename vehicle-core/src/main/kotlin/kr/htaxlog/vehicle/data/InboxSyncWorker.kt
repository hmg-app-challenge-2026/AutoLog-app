package kr.htaxlog.vehicle.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kr.htaxlog.vehicle.core.SyncResult
import java.util.concurrent.TimeUnit

/**
 * inbox 수신 백그라운드 작업 (F-11). 15분 주기(네트워크 있을 때) + 필요할 때 즉시 1회.
 * 실패는 조용히 넘기고 다음 주기에 다시 한다 — 서버 불가가 로컬 기능을 막지 않는다.
 */
class InboxSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = host ?: return Result.success() // 아직 조립 전(앱 미실행) — 다음 주기에
        return when (sync.syncNow()) {
            is SyncResult.Ok -> Result.success()
            is SyncResult.Failed -> if (tags.contains(TAG_NOW) &&
                runAttemptCount < 3
            ) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }

    companion object {
        private const val PERIODIC = "htaxlog-inbox-periodic"
        private const val NOW = "htaxlog-inbox-now"
        private const val TAG_NOW = "now"

        /** 조립 지점이 넣는다. */
        @Volatile var host: InboxSync? = null

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 15분 주기 등록(이미 있으면 유지). 앱 시작 때 한 번. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<InboxSyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }

        /** 지금 한 번(앱 시작·정차 전환 때). 겹치면 앞의 것을 그대로 둔다. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<InboxSyncWorker>().setConstraints(network).addTag(TAG_NOW).build(),
            )
        }
    }
}
