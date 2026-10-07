package com.xichugeek.finance

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xichugeek.finance.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CategoryManagementTest {
    @Test fun localRenamePreservesRelationsAndDeletionRequiresNoTransactionsOrRules() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, FinanceDatabase::class.java).build()
        try {
            val dao = db.dao()
            val repo = FinanceRepository(db)
            val account = dao.insertAccount(AccountEntity(name = "虚构钱包", kind = "cash"))
            val category = dao.insertCategory(CategoryEntity(name = "虚构分类", type = "expense"))
            val transaction = dao.insertTransaction(TransactionEntity(accountId = account, categoryId = category,
                type = "expense", amountMinor = 10, description = "虚构午餐", transactionDate = 1))
            val rule = dao.insertRule(RuleEntity(keyword = "虚构", categoryId = category, type = "expense", enabled = false))
            repo.updateCategory(category, " 改名分类 ")
            assertEquals(CategoryEntity(category, "改名分类", "expense"), dao.category(category))
            assertEquals(category, dao.observeTransactions().first().single().categoryId)
            assertEquals(10L, dao.observeTransactions().first().single().amountMinor)
            assertEquals(category, dao.observeRules().first().single().categoryId)
            try { repo.deleteCategory(category); fail("Referenced category was deleted") } catch (_: IllegalArgumentException) { }
            repo.deleteTransaction(transaction)
            try { repo.deleteCategory(category); fail("Disabled rule reference was lost") } catch (_: IllegalArgumentException) { }
            repo.deleteRule(rule)
            repo.deleteCategory(category)
            assertNull(dao.category(category))
        } finally { db.close() }
    }
}
