package kr.autolog.vehicle.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 차 안 원본 저장소. 스키마는 `vehicle-core/schemas/`에 내보내 커밋한다.
 * 버전을 올릴 때는 Migration을 쓴다 — 운행 기록은 원본이라 destructive migration을 쓰지 않는다.
 */
@Database(
    entities = [
        TripEntity::class,
        StopEntity::class,
        VehicleStateEntity::class,
        UserEntity::class,
        RecentUserEntity::class,
        PlaceEntity::class,
        ScheduleEntity::class,
        RecipientEntity::class,
        ExpenseEntity::class,
        TripExpenseLinkEntity::class,
        ReceiptInboxEntity::class,
        InboxSeenEntity::class,
        ReportEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // v2: receipt_inbox.inboxId·confirmedAt
        // v3: trips 출발·도착 주소와 출처(I-05) + 옛 내비 출발지 이름을 주소 칸으로
        AutoMigration(from = 2, to = 3, spec = AutoLogDb.MoveNaviOriginToAddress::class),
    ],
)
abstract class AutoLogDb : RoomDatabase() {
    /**
     * v2까지는 내비 현재 위치 주소("수정구 시흥동")를 출발지 **이름**(`originLabel`)에 넣었다(I-15). v3부터 이름과 주소를 나누므로
     * 그 값을 주소 칸(출처 NAVI_HELPER)으로 옮긴다 — 그대로 두면 "수정구 시흥동 / 경기 성남시 수정구 시흥동 335"처럼 겹쳐 보인다.
     * 좌표가 있으니 다음 동기화 때 서버(카카오) 주소로 바뀐다(TripSessionManager.fillMissingAddresses).
     * 경로 없이 받은 빈 내비 목적지(이름 "", 좌표 0,0)도 지운다 — 남기면 (0, 0)으로 주소를 찾다 나중 채우기가 멈춘다.
     */
    class MoveNaviOriginToAddress : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(MOVE_NAVI_ORIGIN_SQL)
            db.execSQL(CLEAR_EMPTY_NAVI_DESTINATION_SQL)
        }
    }

    abstract fun tripDao(): TripDao

    abstract fun referenceDao(): ReferenceDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun inboxDao(): InboxDao

    companion object {
        const val NAME = "autolog.db"

        internal const val MOVE_NAVI_ORIGIN_SQL =
            "UPDATE trips SET originAddress = originLabel, originAddressSource = 'NAVI_HELPER', originLabel = NULL " +
                "WHERE originSource = 'NAVI_CURRENT' AND originLabel IS NOT NULL AND originAddress IS NULL"

        internal const val CLEAR_EMPTY_NAVI_DESTINATION_SQL =
            "UPDATE trips SET destinationLabel = NULL, destinationLat = NULL, destinationLng = NULL, " +
                "destinationSource = NULL WHERE destinationSource = 'NAVI_HELPER' AND " +
                "(TRIM(COALESCE(destinationLabel, '')) = '' OR (destinationLat = 0 AND destinationLng = 0))"

        fun open(context: Context): AutoLogDb =
            Room.databaseBuilder(context.applicationContext, AutoLogDb::class.java, NAME).build()
    }
}
