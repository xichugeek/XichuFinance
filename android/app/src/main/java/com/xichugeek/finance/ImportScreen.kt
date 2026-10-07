package com.xichugeek.finance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ImportScreen(state: FinanceUiState, model: FinanceViewModel, onBack: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.previewCsv(it) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("CSV 账单导入", style = MaterialTheme.typography.headlineSmall) }
        item { Text("正式支持西楚记账标准 CSV：UTF-8，最多 512 KiB / 500 行。微信、支付宝和银行原始账单格式尚未验证。") }
        item { Text("必填表头：date,description,amount,type,account\n可选表头：category\n日期 YYYY-MM-DD；type 为 income 或 expense；账户名称需与已创建账户一致。", style = MaterialTheme.typography.bodySmall) }
        if (state.localMode) {
            item { Text("CSV 导入需要登录云端账本并联网。选择文件后会上传至 Backend 进行预览，确认前不会写入交易。") }
            item { Button(onClick = { model.logout() }, enabled = !state.busy) { Text("前往云端登录") } }
        } else {
            item { Text("选择文件会上传至你的 Backend 进行校验。先预览，再确认；错误行和重复行不会导入。", style = MaterialTheme.typography.bodyMedium) }
            item { Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.busy && !state.loginExpired, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "正在处理…" else "选择 CSV 文件") } }
            state.csvResult?.let { result ->
                item { Card(Modifier.fillMaxWidth()) { Text("导入完成：${result.imported} 笔，重复跳过 ${result.duplicates} 笔", Modifier.padding(16.dp)) } }
            }
            state.csvPreview?.let { preview ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("导入预览", style = MaterialTheme.typography.titleLarge)
                            Text("总行数 ${preview.totalRows} · 有效 ${preview.validRows}")
                            Text("错误 ${preview.errorRows} · 重复 ${preview.duplicateRows}")
                            Text("预览 15 分钟后过期。确认只导入 ${preview.validRows} 笔有效记录。")
                            Button(onClick = { model.commitCsv() }, enabled = !state.busy && preview.validRows > 0 && !state.loginExpired, modifier = Modifier.fillMaxWidth()) { Text("确认导入") }
                        }
                    }
                }
                items(preview.rows, key = { it.rowNumber }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("第 ${row.rowNumber} 行 · ${when (row.status) { "valid" -> "有效"; "duplicate" -> "重复"; else -> "错误" }}", color = if (row.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Text(row.description, maxLines = 3)
                            Text("${row.amount} · ${row.account} · ${row.category}", maxLines = 2)
                            Text(row.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") } }
    }
}
