package com.xichugeek.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xichugeek.finance.data.AccountEntity
import com.xichugeek.finance.data.ApiClient
import com.xichugeek.finance.data.CategoryEntity
import com.xichugeek.finance.data.Credentials
import com.xichugeek.finance.data.FinanceDatabase
import com.xichugeek.finance.data.FinanceApi
import com.xichugeek.finance.data.FinanceRepository
import com.xichugeek.finance.data.SessionStore
import com.xichugeek.finance.data.TransactionEntity
import com.xichugeek.finance.data.UserSession
import com.xichugeek.finance.data.CsvPreview
import com.xichugeek.finance.data.CsvCommitResult
import com.xichugeek.finance.data.StandardCsvParser
import com.xichugeek.finance.data.RuleEntity
import com.xichugeek.finance.data.KeywordRule
import com.xichugeek.finance.data.ClassificationResult
import com.xichugeek.finance.data.AskResult
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import retrofit2.HttpException
import java.io.IOException

data class FinanceUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val localMode: Boolean = false,
    val userId: Long? = null,
    val email: String = "",
    val syncStatus: String = "",
    val loginExpired: Boolean = false,
    val csvPreview: CsvPreview? = null,
    val csvResult: CsvCommitResult? = null,
    val askResult: AskResult? = null,
) {
    val hasLedger: Boolean get() = localMode || userId != null
}

class FinanceViewModel @JvmOverloads constructor(
    application: Application,
    private val api: FinanceApi = ApiClient.create(),
) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private var repository: FinanceRepository? = null
    private var collection: Job? = null
    private val operations = Mutex()
    private val _state = MutableStateFlow(FinanceUiState())
    val state: StateFlow<FinanceUiState> = _state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        runAction {
            val session = store.load()
            when {
                session != null -> { activate(session); refreshCache() }
                store.isLocalMode() -> activate(null)
            }
        }
    }

    fun clearError() { _error.value = null }
    fun showError(message: String) { _error.value = message }

    private fun friendlyError(error: Exception): String = when (error) {
        is HttpException -> when (error.code()) {
            401 -> if (_state.value.hasLedger) "登录已过期，请在设置中重新登录" else "邮箱或密码不正确"
            400 -> "请求无效或导入预览已过期，请重新选择 CSV"
            413 -> "CSV 超过大小或行数限制，请拆分文件"
            404 -> "记录已不存在，请刷新账本"
            409 -> "记录重复，或账户仍有关联交易"
            422 -> "请检查输入格式、名称长度和金额"
            else -> "服务暂时不可用，请稍后重试"
        }
        is IOException -> "连接失败：可继续查看缓存，联网后再同步或保存"
        is kotlinx.serialization.SerializationException -> "服务返回格式异常，请稍后重试"
        is android.database.sqlite.SQLiteConstraintException -> "名称重复，或账户仍有关联交易"
        is IllegalArgumentException -> error.message ?: "请检查输入"
        else -> "操作失败，请稍后重试"
    }

    private fun runAction(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!operations.tryLock()) return@launch
            _error.value = null
            _state.update { it.copy(busy = true) }
            try {
                block(); onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is HttpException && error.code() == 401 && _state.value.hasLedger) {
                    _state.update { it.copy(loginExpired = true, syncStatus = "登录已过期，缓存可查看") }
                }
                _error.value = friendlyError(error)
            } finally {
                _state.update { it.copy(busy = false, loading = false) }
                operations.unlock()
            }
        }
    }

    private suspend fun activate(session: UserSession?) {
        collection?.cancelAndJoin()
        _state.value = FinanceUiState(loading = false, busy = true,
            localMode = session == null, userId = session?.id, email = session?.email ?: "",
            syncStatus = if (session == null) "本地账本，仅保存在此设备" else "正在同步；缓存可查看")
        val database = if (session == null) FinanceDatabase.get(getApplication())
        else FinanceDatabase.forUser(getApplication(), BuildConfig.API_BASE_URL, session.id)
        val keywords = withContext(Dispatchers.IO) {
            getApplication<Application>().assets.open("classification_keywords.json").bufferedReader().use {
                ApiClient.json.decodeFromString<List<KeywordRule>>(it.readText())
            }
        }
        val activeRepository = FinanceRepository(database, if (session == null) null else api, session, keywords)
        repository = activeRepository
        if (session == null) activeRepository.seedIfEmpty()
        collection = viewModelScope.launch {
            combine(activeRepository.accounts, activeRepository.categories, activeRepository.transactions, activeRepository.rules) { a, c, t, r ->
                FinanceUiState(accounts = a, categories = c, transactions = t, rules = r)
            }.collect { cache -> _state.update { it.copy(accounts = cache.accounts, categories = cache.categories, transactions = cache.transactions, rules = cache.rules) } }
        }
    }

    private suspend fun refreshCache() {
        _state.update { it.copy(syncStatus = "正在同步；缓存可查看") }
        try {
            checkNotNull(repository).refresh()
            _state.update { it.copy(syncStatus = "已同步到此设备", loginExpired = false) }
        } catch (error: IOException) {
            _state.update { it.copy(syncStatus = "连接失败，当前显示已缓存数据") }
            throw error
        }
    }

    fun authenticate(email: String, password: String, register: Boolean) = runAction {
        require(email.isNotBlank()) { "请输入邮箱" }
        require(password.length in 8..128) { "密码长度应为 8–128 个字符" }
        val credentials = Credentials(email.trim(), password)
        if (register) api.register(credentials)
        val token = api.login(credentials).token
        val user = api.me("Bearer $token")
        val session = UserSession(user.id, user.email, token)
        store.save(session)
        activate(session)
        refreshCache()
    }

    fun useLocalMode() = runAction { store.useLocalMode(); activate(null) }
    fun logout() = runAction {
        store.clear()
        collection?.cancelAndJoin(); collection = null; repository = null
        _error.value = null
        _state.value = FinanceUiState(loading = false, busy = true)
    }
    fun refresh() = runAction { if (!_state.value.localMode) refreshCache() }
    fun previewCsv(uri: Uri) = runAction {
        _state.update { it.copy(csvPreview = null, csvResult = null) }
        val bytes = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { StandardCsvParser.readLimited(it) }
                ?: throw IOException("Cannot read selected file")
        }
        _state.update { it.copy(csvPreview = checkNotNull(repository).previewCsv(bytes)) }
    }
    fun commitCsv() = runAction {
        val preview = checkNotNull(_state.value.csvPreview)
        require(preview.validRows > 0) { "没有可导入的有效行" }
        val result = checkNotNull(repository).commitCsv(preview)
        _state.update { it.copy(csvPreview = null, csvResult = result) }
        refreshCache()
    }
    fun addAccount(name: String, kind: String, onSuccess: () -> Unit = {}) =
        runAction(onSuccess) { checkNotNull(repository).addAccount(name, kind) }
    fun classify(description: String, type: String, onResult: (ClassificationResult) -> Unit) =
        runAction { onResult(checkNotNull(repository).classify(description, type)) }
    fun saveRule(rule: RuleEntity, onSuccess: () -> Unit = {}) = runAction(onSuccess) { checkNotNull(repository).saveRule(rule) }
    fun deleteRule(id: Long) = runAction { checkNotNull(repository).deleteRule(id) }
    fun ask(question: String, month: String) = runAction {
        _state.update { it.copy(askResult = null) }
        val result = checkNotNull(repository).ask(question, month)
        _state.update { it.copy(askResult = result) }
    }
    fun updateAccount(account: AccountEntity, onSuccess: () -> Unit = {}) =
        runAction(onSuccess) { checkNotNull(repository).updateAccount(account) }
    fun deleteAccount(id: Long) = runAction { checkNotNull(repository).deleteAccount(id) }
    fun addCategory(name: String, type: String, onSuccess: () -> Unit = {}) =
        runAction(onSuccess) { checkNotNull(repository).addCategory(name, type) }
    fun saveTransaction(transaction: TransactionEntity, onSuccess: () -> Unit) = runAction(onSuccess) {
        val snapshot = state.value
        require(snapshot.accounts.any { it.id == transaction.accountId }) { "请先添加并选择账户" }
        require(snapshot.categories.any { it.id == transaction.categoryId && it.type == transaction.type }) { "请选择对应的分类" }
        checkNotNull(repository).saveTransaction(transaction)
    }
    fun deleteTransaction(id: Long, onSuccess: () -> Unit) =
        runAction(onSuccess) { checkNotNull(repository).deleteTransaction(id) }
}
