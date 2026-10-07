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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "classification_rules",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId"), Index(value = ["type", "keyword"], unique = true)])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String, val categoryId: Long, val type: String,
    val priority: Int = 100, val enabled: Boolean = true,
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM classification_rules ORDER BY priority, id") fun observeRules(): Flow<List<RuleEntity>>
    @Upsert suspend fun cacheRule(item: RuleEntity)
    @Insert suspend fun insertRule(item: RuleEntity): Long
    @Insert suspend fun insertRules(items: List<RuleEntity>)
    @Query("DELETE FROM classification_rules WHERE id = :id") suspend fun deleteRule(id: Long): Int
    @Query("DELETE FROM classification_rules") suspend fun clearRules()
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

    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: Long): CategoryEntity?
    @Query("UPDATE categories SET name = :name WHERE id = :id") suspend fun updateCategoryName(id: Long, name: String): Int
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id") suspend fun categoryTransactionCount(id: Long): Int
    @Query("SELECT COUNT(*) FROM classification_rules WHERE categoryId = :id") suspend fun categoryRuleCount(id: Long): Int

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
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, RuleEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val updates = mutableListOf<Pair<Long, Long>>()
                db.query("SELECT id, transactionDate FROM transactions").use { cursor ->
                    while (cursor.moveToNext()) {
                        // Preserve the date displayed in the device's current zone
                        // at upgrade, then store it independently of future zones.
                        val date = java.time.Instant.ofEpochMilli(cursor.getLong(1)).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        updates.add(cursor.getLong(0) to LedgerDates.encode(date))
                    }
                }
                updates.forEach { (id, day) -> db.execSQL("UPDATE transactions SET transactionDate = ? WHERE id = ?", arrayOf(day, id)) }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS classification_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, keyword TEXT NOT NULL, categoryId INTEGER NOT NULL, type TEXT NOT NULL, priority INTEGER NOT NULL, enabled INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_classification_rules_categoryId ON classification_rules (categoryId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_classification_rules_type_keyword ON classification_rules (type, keyword)")
            }
        }
        @Volatile private var instance: FinanceDatabase? = null
        private val userInstances = ConcurrentHashMap<String, FinanceDatabase>()

        fun forUser(context: Context, server: String, userId: Long): FinanceDatabase {
            require(userId > 0)
            val serverHash = MessageDigest.getInstance("SHA-256").digest(server.toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
            val name = "cloud_${serverHash}_user_$userId.db"
            return userInstances.getOrPut(name) {
                Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
            }
        }

        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                "xichu_finance.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
