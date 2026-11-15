package kr.autolog.vehicle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 비용과 영수증 inbox (F-18, F-19). */
@Dao
interface ExpenseDao {
    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE receiptSha256 = :sha256)")
    suspend fun hasReceipt(sha256: String): Boolean

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: TripExpenseLinkEntity)

    @Transaction
    suspend fun saveExpense(expense: ExpenseEntity, tripId: String?) {
        insertExpense(expense)
        tripId?.let { insertLink(TripExpenseLinkEntity(expense.expenseId, it)) }
    }

    @Query("SELECT * FROM expenses WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt")
    suspend fun expensesBetween(from: Long, to: Long): List<ExpenseEntity>

    @Query(
        "SELECT * FROM expenses WHERE kind = 'FUEL' AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC",
    )
    fun observeFuelExpenses(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM trip_expense_links WHERE expenseId IN (:expenseIds)")
    suspend fun linksForExpenses(expenseIds: List<String>): List<TripExpenseLinkEntity>

    @Query("SELECT * FROM trip_expense_links WHERE tripId = :tripId")
    suspend fun linksForTrip(tripId: String): List<TripExpenseLinkEntity>

    /** 화면에 보일 영수증: 아직 확정하지 않은 것, 최근 순. */
    @Query("SELECT * FROM receipt_inbox WHERE confirmedAt IS NULL ORDER BY receivedAt DESC")
    fun observePendingReceipts(): Flow<List<ReceiptInboxEntity>>

    /** 확정했지만 서버 ack가 아직인 것. InboxSyncWorker가 다시 ack한다. */
    @Query("SELECT * FROM receipt_inbox WHERE confirmedAt IS NOT NULL")
    suspend fun confirmedReceipts(): List<ReceiptInboxEntity>

    @Query("SELECT * FROM receipt_inbox WHERE receiptId = :receiptId")
    suspend fun receipt(receiptId: String): ReceiptInboxEntity?

    @Upsert
    suspend fun upsertReceipt(receipt: ReceiptInboxEntity)

    @Query("UPDATE receipt_inbox SET confirmedAt = :at WHERE receiptId = :receiptId")
    suspend fun markReceiptConfirmed(receiptId: String, at: Long)

    @Query("DELETE FROM receipt_inbox WHERE receiptId = :receiptId")
    suspend fun deleteReceipt(receiptId: String)
}
