package kr.htaxlog.vehicle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 기준 정보(직원·장소·배차·수신자). SERVER 분은 inbox 수신 때 통째로 바꾸고 LOCAL 분은 남긴다(F-11, F-43). */
@Dao
interface ReferenceDao {
    @Query("SELECT * FROM users ORDER BY name")
    fun observeUsers(): Flow<List<UserEntity>>

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("SELECT * FROM recent_users ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun recentUsers(limit: Int): List<RecentUserEntity>

    @Query("SELECT * FROM places ORDER BY label")
    suspend fun places(): List<PlaceEntity>

    @Upsert
    suspend fun upsertPlace(place: PlaceEntity)

    /** [from]~[to]와 겹치는 이 차의 배차. */
    @Query(
        "SELECT * FROM schedules WHERE vehicleId = :vehicleId AND startsAt <= :to AND endsAt >= :from ORDER BY startsAt",
    )
    suspend fun schedules(vehicleId: String, from: Long, to: Long): List<ScheduleEntity>

    /** 서버 배포분 먼저, 그다음 이메일순. */
    @Query("SELECT * FROM recipients ORDER BY CASE origin WHEN 'SERVER' THEN 0 ELSE 1 END, email")
    fun observeRecipients(): Flow<List<RecipientEntity>>

    @Upsert
    suspend fun upsertRecipient(recipient: RecipientEntity)

    @Query("DELETE FROM recipients WHERE email = :email AND origin = 'LOCAL'")
    suspend fun deleteLocalRecipient(email: String)

    // ---- 서버 배포분 전체 교체 (InboxSyncWorker) ----

    @Query("DELETE FROM users WHERE origin = 'SERVER'")
    suspend fun deleteServerUsers()

    @Query("DELETE FROM places WHERE origin = 'SERVER'")
    suspend fun deleteServerPlaces()

    @Query("DELETE FROM schedules")
    suspend fun deleteSchedules()

    @Query("DELETE FROM recipients WHERE origin = 'SERVER'")
    suspend fun deleteServerRecipients()

    /** LOCAL과 id가 겹치면 LOCAL을 남긴다(차 안 입력 보존). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaces(places: List<PlaceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipients(recipients: List<RecipientEntity>)

    @Transaction
    suspend fun replaceServerUsers(users: List<UserEntity>) {
        deleteServerUsers()
        insertUsers(users)
    }

    @Transaction
    suspend fun replaceServerPlaces(places: List<PlaceEntity>) {
        deleteServerPlaces()
        insertPlaces(places)
    }

    @Transaction
    suspend fun replaceSchedules(schedules: List<ScheduleEntity>) {
        deleteSchedules()
        insertSchedules(schedules)
    }

    /** 같은 이메일은 서버 배포분 하나만 남긴다(SettingsApi 계약). */
    @Transaction
    suspend fun replaceServerRecipients(recipients: List<RecipientEntity>) {
        deleteServerRecipients()
        insertRecipients(recipients)
    }
}
