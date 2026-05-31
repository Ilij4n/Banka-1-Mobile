package rs.raf.banka1.mobile.presentation.screens.exchange

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.raf.banka1.mobile.data.remote.responses.ExchangeRateDto
import rs.raf.banka1.mobile.presentation.components.ErrorDialog
import rs.raf.banka1.mobile.presentation.viewmodels.main.ExchangeContract
import rs.raf.banka1.mobile.presentation.viewmodels.main.ExchangeViewModel
import java.text.NumberFormat
import java.util.Locale

private val currencyFlags = mapOf(
    "EUR" to "🇪🇺",
    "USD" to "🇺🇸",
    "CHF" to "🇨🇭",
    "GBP" to "🇬🇧",
    "JPY" to "🇯🇵",
    "CAD" to "🇨🇦",
    "AUD" to "🇦🇺"
)

private val currencyNames = mapOf(
    "EUR" to "Evro",
    "USD" to "Americki dolar",
    "CHF" to "Svajcarski franak",
    "GBP" to "Britanska funta",
    "JPY" to "Japanski jen",
    "CAD" to "Kanadski dolar",
    "AUD" to "Australijski dolar"
)

private val chartLineColor = Color(0xFF00897B)

@Composable
fun ExchangeScreen(
    viewModel: ExchangeViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.error != null) {
        ErrorDialog(errorData = state.error) {
            viewModel.setEvent(ExchangeContract.UiEvent.Refresh)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CurrencyExchange,
                    contentDescription = null,
                    tint = chartLineColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Kursna lista",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kupovni i prodajni kursevi Banke 1",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.isLoading && state.rates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Valuta",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Kupovni",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = "Prodajni",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(80.dp)
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    items = state.rates,
                    key = { _, rate -> rate.currencyCode ?: "" }
                ) { index, rate ->
                    ExchangeRateRow(
                        rate = rate,
                        isLast = index == state.rates.lastIndex,
                        isSelected = rate.currencyCode == state.selectedCurrency,
                        onClick = {
                            rate.currencyCode?.let {
                                viewModel.setEvent(ExchangeContract.UiEvent.SelectCurrency(it))
                            }
                        }
                    )
                }

                item {
                    val selected = state.selectedCurrency
                    if (selected != null) {
                        Spacer(Modifier.height(16.dp))
                        RateHistorySection(
                            currencyCode = selected,
                            points = state.historyPoints,
                            isLoading = state.isLoadingHistory
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ExchangeRateRow(
    rate: ExchangeRateDto,
    isLast: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val code = rate.currencyCode ?: ""
    val rateFormatter = remember {
        NumberFormat.getNumberInstance(Locale("sr", "RS")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 4
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = if (isLast) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        else RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                chartLineColor.copy(alpha = 0.07f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(chartLineColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(chartLineColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currencyFlags[code] ?: code.take(2),
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = currencyNames[code] ?: code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = rateFormatter.format(rate.buyingRate ?: 0.0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(80.dp)
            )
            Text(
                text = rateFormatter.format(rate.sellingRate ?: 0.0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(80.dp)
            )
        }
    }
}

@Composable
private fun RateHistorySection(
    currencyCode: String,
    points: List<ExchangeRateDto>,
    isLoading: Boolean
) {
    val sortedPoints = remember(points) {
        points.sortedBy { it.date ?: "" }
    }
    val values = remember(sortedPoints) {
        sortedPoints.mapNotNull { it.sellingRate?.toFloat() }
    }

    val animProgress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        animProgress.snapTo(0f)
        if (values.isNotEmpty()) {
            animProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    val rateFormatter = remember {
        NumberFormat.getNumberInstance(Locale("sr", "RS")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 4
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currencyFlags[currencyCode] ?: ""} $currencyCode",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                if (values.size >= 2) {
                    val delta = values.last() - values.first()
                    val deltaPercent = if (values.first() != 0f) delta / values.first() * 100f else 0f
                    val deltaColor = if (delta >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                    Text(
                        text = "${if (delta >= 0) "+" else ""}${"%.2f".format(deltaPercent)}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = deltaColor
                    )
                }
            }
            Text(
                text = "Prodajni kurs — poslednjih 30 dana",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = chartLineColor
                        )
                    }
                }
                values.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nema podataka",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val minVal = values.min()
                    val maxVal = values.max()
                    val range = (maxVal - minVal).coerceAtLeast(0.001f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val padTop = 8.dp.toPx()
                        val padBottom = 8.dp.toPx()
                        val chartH = h - padTop - padBottom
                        val n = values.size
                        val stepX = if (n > 1) w / (n - 1).toFloat() else w

                        fun xOf(i: Int) = i * stepX
                        fun yOf(v: Float): Float {
                            val normalized = (v - minVal) / range
                            return padTop + chartH * (1f - normalized * animProgress.value)
                        }

                        // Grid lines
                        val gridColor = Color.Gray.copy(alpha = 0.12f)
                        for (g in 0..2) {
                            val gy = padTop + g * chartH / 2f
                            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 0.5f)
                        }

                        // Fill area
                        val fillPath = Path()
                        fillPath.moveTo(xOf(0), h)
                        values.forEachIndexed { i, v -> fillPath.lineTo(xOf(i), yOf(v)) }
                        fillPath.lineTo(xOf(values.lastIndex), h)
                        fillPath.close()
                        drawPath(fillPath, color = chartLineColor.copy(alpha = 0.10f))

                        // Line
                        val linePath = Path()
                        values.forEachIndexed { i, v ->
                            val x = xOf(i)
                            val y = yOf(v)
                            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                        }
                        drawPath(
                            linePath,
                            color = chartLineColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // End dot (white border + teal fill)
                        val lastX = xOf(values.lastIndex)
                        val lastY = yOf(values.last())
                        drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
                        drawCircle(chartLineColor, radius = 3.5f.dp.toPx(), center = Offset(lastX, lastY))
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rateFormatter.format(values.min()),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFD32F2F)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Poslednja vrednost",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rateFormatter.format(values.last()),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Max",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rateFormatter.format(values.max()),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF388E3C)
                            )
                        }
                    }

                    if (sortedPoints.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sortedPoints.first().date?.take(10) ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = sortedPoints.last().date?.take(10) ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
