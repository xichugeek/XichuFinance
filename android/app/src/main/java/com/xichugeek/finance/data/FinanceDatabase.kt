package com.xichugeek.finance.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Entity(tableName = "accounts", indices = [Index(value = ["name"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val openingBalanceMinor: Long = 0,
)

@Entity(tableName = "categories", indices = [Index(value = ["name", "type"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("transactionDate")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long,
    val type: String,
    val amountMinor: Long,
    val currency: String = "CNY",
    val description: String,
    val transactionDate: Long,
    val source: String = "manual",
    val externalId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM categories ORDER BY type, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert suspend fun insertAccounts(items: List<AccountEntity>)
    @Insert suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert suspend fun insertTransactions(items: List<TransactionEntity>)
    @Upsert suspend fun cacheAccount(item: AccountEntity)
    @Upsert suspend fun cacheCategory(item: CategoryEntity)
    @Upsert suspend fun cacheTransaction(item: TransactionEntity)

    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("UPDATE accounts SET name = :name, kind = :kind, openingBalanceMinor = :openingBalance WHERE id = :id")
    suspend fun updateAccount(id: Long, name: String, kind: String, openingBalance: Long): Int
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: Long): Int

    @Query("UPDATE transactions SET accountId = :accountId, categoryId = :categoryId, type = :type, amountMinor = :amountMinor, description = :description, transactionDate = :transactionDate, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTransaction(
        id: Long,
        accountId: Long,
        categoryId: Long,
        type: String,
        amountMinor: Long,
        description: String,
        transactionDate: Long,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long): Int
}

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        @Volatile private var instance: FinanceDatabase? = null
        private val userInstances = ConcurrentHashMap<String, FinanceDatabase>()

        fun forUser(context: Context, server: String, userId: Long): FinanceDatabase {
            require(userId > 0)
            val serverHash = MessageDigest.getInstance("SHA-256").digest(server.toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
            val name = "cloud_${serverHash}_user_$userId.db"
            return userInstances.getOrPut(name) {
                Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, name).build()
            }
        }

        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "xichu_finance.db",
            ).build().also { instance = it }
        }
    }
}
