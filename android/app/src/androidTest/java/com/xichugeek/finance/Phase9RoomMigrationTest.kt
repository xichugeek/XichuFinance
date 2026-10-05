package com.xichugeek.finance

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xichugeek.finance.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class Phase9RoomMigrationTest {
    @Test fun upgradesOriginalSchemaWithoutLosingMoneyDatesOrRelations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "phase9-migration-${UUID.randomUUID()}.db"
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.xichugeek.finance.data.FinanceDatabase/1.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject.getValue("database").jsonObject
        }
        val date = LocalDate.of(2026, 10, 1)
        val oldTimestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (item in schema.getValue("entities").jsonArray) {
                val entity = item.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                for (index in entity.getValue("indices").jsonArray) db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO accounts VALUES (7, '虚构迁移钱包', 'cash', 12345)")
            db.execSQL("INSERT INTO categories VALUES (8, '餐饮', 'expense')")
            db.execSQL("INSERT INTO transactions VALUES (9,7,8,'expense',31,'CNY','虚构迁移午餐',?,'demo',NULL,100,200)", arrayOf(oldTimestamp))
            db.version = 1
        }
        fun open() = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceDatabase.MIGRATION_1_2, FinanceDatabase.MIGRATION_2_3).build()
        val migrated = open()
        try {
            val dao = migrated.dao()
            val transaction = dao.observeTransactions().first().single()
            assertEquals(31L, transaction.amountMinor)
            assertEquals(9L, transaction.id)
            assertEquals(date, LedgerDates.decode(transaction.transactionDate))
            assertEquals(12345L, dao.observeAccounts().first().single().openingBalanceMinor)
            assertEquals(200L, transaction.updatedAt)
            assertEquals(3, migrated.openHelper.readableDatabase.version)
            assertTrue(dao.observeRules().first().isEmpty())
            dao.insertRule(RuleEntity(keyword = "午餐", categoryId = 8, type = "expense"))
            try { dao.deleteAccount(7); fail("Linked account deletion must fail") } catch (_: SQLiteConstraintException) { }
        } finally { migrated.close() }
        val reopened = open()
        try {
            assertEquals(31L, reopened.dao().observeTransactions().first().single().amountMinor)
            assertEquals(1, reopened.dao().observeRules().first().size)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
