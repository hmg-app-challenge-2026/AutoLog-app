package kr.htaxlog.vehicle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 이미 반영한 inbox 편지. ack가 실패해 같은 편지가 다시 와도 두 번 반영하지 않는다(F-11). */
@Dao
interface InboxDao {
    @Query("SELECT EXISTS(SELECT 1 FROM inbox_seen WHERE inboxId = :inboxId)")
    suspend fun isSeen(inboxId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(seen: InboxSeenEntity)
}
