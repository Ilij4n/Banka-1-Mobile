package rs.raf.banka1.mobile.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import rs.raf.banka1.mobile.data.remote.responses.OrderResponseDto
import rs.raf.banka1.mobile.presentation.components.ErrorDialog
import rs.raf.banka1.mobile.presentation.viewmodels.main.MyOrdersContract
import rs.raf.banka1.mobile.presentation.viewmodels.main.MyOrdersViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

private val statusOptions = listOf(
    "ALL" to "Sve",
    "PENDING" to "Na cekanju",
    "APPROVED" to "Odobreno",
    "DECLINED" to "Odbijeno",
    "DONE" to "Izvrseno",
    "CANCELLED" to "Otkazano"
)

private val typeOptions = listOf<Pair<String?, String>>(
    null to "Sve vrste",
    "STOCK" to "Akcije",
    "FUTURES" to "Fjucersi",
    "FOREX" to "Forex",
    "OPTION" to "Opcije"
)

private val statusColors = mapOf(
    "DONE" to Color(0xFF34A853),
    "APPROVED" to Color(0xFF34A853),
    "PENDING" to Color(0xFFFBBC04),
    "PENDING_CONFIRMATION" to Color(0xFFFBBC04),
    "DECLINED" to Color(0xFFEA4335),
    "CANCELLED" to Color(0xFF9E9E9E)
)

private val statusLabels = mapOf(
    "DONE" to "Izvrseno",
    "APPROVED" to "Odobreno",
    "PENDING" to "Na cekanju",
    "PENDING_CONFIRMATION" to "Nepotvrdjeno",
    "DECLINED" to "Odbijeno",
    "CANCELLED" to "Otkazano"
)

@Composable
fun MyOrdersScreen(
    viewModel: MyOrdersViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onEvent = viewModel::setEvent
    val orders = viewModel.pagingData.collectAsLazyPagingItems()

    if (state.error != null) {
        ErrorDialog(errorData = state.error) {
            onEvent(MyOrdersContract.UiEvent.ClearError)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Status filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statusOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = state.status == value,
                        onClick = { onEvent(MyOrdersContract.UiEvent.SelectStatus(value)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Listing-type filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                typeOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = state.listingType == value,
                        onClick = { onEvent(MyOrdersContract.UiEvent.SelectType(value)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            DateRangeRow(
                dateFrom = state.dateFrom,
                dateTo = state.dateTo,
                onRangeChange = { from, to ->
                    onEvent(MyOrdersContract.UiEvent.SelectDateRange(from, to))
                }
            )
        }

        val refresh = orders.loadState.refresh
        when {
            refresh is LoadState.Loading -> CenteredBox {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            refresh is LoadState.Error -> CenteredBox {
                Text(
                    text = refresh.error.localizedMessage ?: "Greska pri ucitavanju naloga",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            orders.itemCount == 0 -> EmptyState("Nemate naloga za izabrane filtere")

            else -> {
                val formatter = remember {
                    NumberFormat.getNumberInstance(Locale("sr", "RS")).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = orders.itemCount,
                        key = orders.itemKey { it.id ?: it.hashCode().toLong() }
                    ) { index ->
                        orders[index]?.let { order ->
                            OrderCard(order = order, formatter = formatter)
                        }
                    }

                    if (orders.loadState.append is LoadState.Loading) {
                        item {
                            CenteredBox(modifier = Modifier.height(56.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeRow(
    dateFrom: String?,
    dateTo: String?,
    onRangeChange: (String?, String?) -> Unit
) {
    var picking by remember { mutableStateOf<DateField?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateFieldButton(
            label = dateFrom ?: "Od",
            modifier = Modifier.weight(1f),
            onClick = { picking = DateField.FROM }
        )
        DateFieldButton(
            label = dateTo ?: "Do",
            modifier = Modifier.weight(1f),
            onClick = { picking = DateField.TO }
        )
        if (dateFrom != null || dateTo != null) {
            TextButton(onClick = { onRangeChange(null, null) }) {
                Text("Ponisti")
            }
        }
    }

    picking?.let { field ->
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val iso = pickerState.selectedDateMillis?.let { millis ->
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    when (field) {
                        DateField.FROM -> onRangeChange(iso, dateTo)
                        DateField.TO -> onRangeChange(dateFrom, iso)
                    }
                    picking = null
                }) { Text("Potvrdi") }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text("Otkazi") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private enum class DateField { FROM, TO }

@Composable
private fun DateFieldButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: OrderResponseDto,
    formatter: NumberFormat
) {
    val isBuy = order.direction.equals("BUY", ignoreCase = true)
    val directionColor = if (isBuy) Color(0xFF34A853) else Color(0xFFEA4335)
    val status = order.status ?: ""
    val statusColor = statusColors[status] ?: MaterialTheme.colorScheme.primary
    val priceToShow = order.executionPrice ?: order.pricePerUnit

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(directionColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBuy) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = directionColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.ticker?.takeIf { it.isNotBlank() } ?: "Nalog #${order.id ?: ""}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = order.securityName?.takeIf { it.isNotBlank() }
                        ?: (order.orderType ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${if (isBuy) "Kupovina" else "Prodaja"} • kolicina ${order.quantity ?: 0}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatTimestamp(order.executedAt ?: order.createdAt),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (priceToShow != null) {
                    Text(
                        text = formatter.format(priceToShow),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (order.fee != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Provizija ${formatter.format(order.fee)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusLabels[status] ?: status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val datePart = raw.take(10)
    val timePart = raw.drop(11).take(5)
    return if (timePart.isNotBlank()) "$datePart $timePart" else datePart
}
