package com.xichugeek.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object FinancePalette {
    val Accent = Color(0xFFF43F5E)
    val Blush = Color(0xFFFFEDF1)
    val Ink = Color(0xFF252B36)
    val Muted = Color(0xFF687284)
    val Canvas = Color(0xFFF7F8FA)
    val Border = Color(0xFFEBEDF2)
    val Income = Color(0xFF21886C)
    val Mint = Color(0xFFEAF7F1)
}

@Composable
internal fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FinancePalette.Accent, onPrimary = Color.White,
            primaryContainer = FinancePalette.Blush, onPrimaryContainer = FinancePalette.Accent,
            secondary = FinancePalette.Income, onSecondary = Color.White,
            secondaryContainer = FinancePalette.Mint, onSecondaryContainer = FinancePalette.Income,
            background = FinancePalette.Canvas, onBackground = FinancePalette.Ink,
            surface = Color.White, onSurface = FinancePalette.Ink,
            surfaceVariant = FinancePalette.Canvas, onSurfaceVariant = FinancePalette.Muted,
            surfaceContainer = Color.White, surfaceContainerLow = Color.White,
            surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
            outline = Color(0xFFCDD2DC), outlineVariant = FinancePalette.Border,
            error = FinancePalette.Accent,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp)),
        content = content,
    )
}

// Small original vector assets; no text glyphs or bitmap/font dependencies.
internal object FinanceIcons {
    private fun icon(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()
    private fun PathBuilder.circle(x: Float, y: Float, r: Float) {
        val c = r * .5523f
        moveTo(x + r, y); curveTo(x + r, y + c, x + c, y + r, x, y + r)
        curveTo(x - c, y + r, x - r, y + c, x - r, y)
        curveTo(x - r, y - c, x - c, y - r, x, y - r)
        curveTo(x + c, y - r, x + r, y - c, x + r, y); close()
    }
    val Home = icon("Home") { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 9f) }
    val Transactions = icon("Transactions") { moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f); lineTo(15f, 19f); lineTo(12f, 21f); lineTo(9f, 19f); lineTo(6f, 21f); close(); moveTo(9f, 8f); lineTo(15f, 8f); moveTo(9f, 12f); lineTo(15f, 12f) }
    val Wallet = icon("Wallet") { moveTo(4f, 6f); lineTo(17f, 3f); lineTo(17f, 7f); moveTo(20f, 7f); lineTo(4f, 7f); lineTo(4f, 20f); lineTo(20f, 20f); close(); moveTo(20f, 11f); lineTo(15f, 11f); lineTo(15f, 16f); lineTo(20f, 16f); circle(17.5f, 13.5f, .4f) }
    val Categories = icon("Categories") { moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close(); moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close(); moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close(); moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close() }
    val Settings = icon("Settings") { moveTo(10f, 3f); lineTo(14f, 3f); lineTo(15f, 6f); lineTo(18f, 6f); lineTo(21f, 10f); lineTo(19f, 12f); lineTo(21f, 14f); lineTo(18f, 18f); lineTo(15f, 18f); lineTo(14f, 21f); lineTo(10f, 21f); lineTo(9f, 18f); lineTo(6f, 18f); lineTo(3f, 14f); lineTo(5f, 12f); lineTo(3f, 10f); lineTo(6f, 6f); lineTo(9f, 6f); close(); circle(12f, 12f, 3f) }
    val Plus = icon("Plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Arrow = icon("Arrow") { moveTo(8f, 5f); lineTo(15f, 12f); lineTo(8f, 19f) }
    val Expense = icon("Expense") { moveTo(7f, 5f); lineTo(19f, 17f); moveTo(19f, 7f); lineTo(19f, 17f); lineTo(9f, 17f) }
    val Income = icon("Income") { moveTo(7f, 19f); lineTo(19f, 7f); moveTo(9f, 7f); lineTo(19f, 7f); lineTo(19f, 17f) }
    val Chart = icon("Chart") { moveTo(4f, 4f); lineTo(4f, 20f); lineTo(21f, 20f); moveTo(7f, 15f); lineTo(11f, 10f); lineTo(15f, 13f); lineTo(20f, 6f) }
    val Import = icon("Import") { moveTo(5f, 4f); lineTo(15f, 4f); lineTo(20f, 9f); lineTo(20f, 21f); lineTo(5f, 21f); close(); moveTo(15f, 4f); lineTo(15f, 9f); lineTo(20f, 9f); moveTo(12f, 11f); lineTo(12f, 17f); moveTo(9f, 14f); lineTo(12f, 17f); lineTo(15f, 14f) }
    val Ask = icon("Ask") { moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 17f); lineTo(10f, 17f); lineTo(4f, 21f); close(); moveTo(8f, 9f); lineTo(16f, 9f); moveTo(8f, 13f); lineTo(13f, 13f) }
    val Food = icon("Food") { moveTo(5f, 3f); lineTo(5f, 9f); lineTo(11f, 9f); lineTo(11f, 3f); moveTo(8f, 3f); lineTo(8f, 21f); moveTo(18f, 21f); lineTo(18f, 3f); curveTo(14f, 5f, 14f, 12f, 18f, 12f) }
    val Transport = icon("Transport") { moveTo(5f, 4f); lineTo(19f, 4f); lineTo(19f, 17f); lineTo(5f, 17f); close(); moveTo(5f, 11f); lineTo(19f, 11f); moveTo(12f, 4f); lineTo(12f, 11f); circle(8f, 20f, 1f); circle(16f, 20f, 1f); moveTo(8f, 14f); lineTo(8.1f, 14f); moveTo(16f, 14f); lineTo(16.1f, 14f) }
}

@Composable
internal fun FinanceBrandMark(modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFF74766), Color(0xFFCF2247))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun FinanceTopBar() {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FinanceBrandMark(Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.app_name), Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun FinanceBottomNavigation(route: String?, onSelect: (String) -> Unit) {
    val entries = listOf(Triple("home", "概览", FinanceIcons.Home), Triple("transactions", "交易", FinanceIcons.Transactions),
        Triple("accounts", "账户", FinanceIcons.Wallet), Triple("categories", "分类", FinanceIcons.Categories), Triple("settings", "设置", FinanceIcons.Settings))
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 6.dp, border = BorderStroke(1.dp, FinancePalette.Border)) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 4.dp, vertical = 6.dp)) {
                entries.forEach { (destination, label, icon) ->
                    val selected = route == destination
                    Column(
                        Modifier.weight(1f).heightIn(min = 64.dp).selectable(selected, role = Role.Tab, onClick = { onSelect(destination) }).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Box(Modifier.size(44.dp, 30.dp).background(if (selected) FinancePalette.Blush else Color.Transparent, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, modifier = Modifier.size(23.dp), tint = if (selected) FinancePalette.Accent else FinancePalette.Muted)
                        }
                        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) FinancePalette.Accent else FinancePalette.Muted, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FinancePalette.Muted) }
    }
}

@Composable
internal fun AmountText(value: String, modifier: Modifier = Modifier, color: Color = FinancePalette.Ink, large: Boolean = false, textAlign: TextAlign = TextAlign.Start) {
    BoxWithConstraints(modifier) {
        val scale = LocalDensity.current.fontScale
        var font by remember(value, maxWidth, scale) { mutableFloatStateOf(if (large) 32f else 22f) }
        Text(value, modifier = Modifier.fillMaxWidth(), textAlign = textAlign, fontSize = font.sp, lineHeight = (font * 1.2f).sp, fontWeight = FontWeight.Bold, color = color,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.hasVisualOverflow && font > 14f) font = (font - 2f).coerceAtLeast(14f) })
    }
}

@Composable
internal fun IconBadge(icon: ImageVector, color: Color = FinancePalette.Accent, background: Color = FinancePalette.Blush) {
    Box(Modifier.size(44.dp).background(background, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
    }
}
