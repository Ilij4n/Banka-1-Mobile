package rs.raf.banka1.mobile.presentation.screens.pricealerts

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.raf.banka1.mobile.data.remote.responses.ListingSummaryDto
import rs.raf.banka1.mobile.presentation.components.ErrorDialog
import rs.raf.banka1.mobile.presentation.viewmodels.main.PriceAlertsContract
import rs.raf.banka1.mobile.presentation.viewmodels.main.PriceAlertsViewModel

private val conditionOptions = listOf(
    "ABOVE" to "Cena preko",
    "BELOW" to "Cena ispod",
    "PCT_DROP_INTRADAY" to "Intraday pad %"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertsScreen(
    viewModel: PriceAlertsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onEvent = viewModel::setEvent

    ErrorDialog(errorData = state.error) {
        onEvent(PriceAlertsContract.UiEvent.ClearError)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(PriceAlertsContract.UiEvent.OpenCreate) }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Dodaj alarm")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Nazad",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Cenovni alarmi",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                state.alerts.isEmpty() -> AlertsEmptyState()

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = state.alerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            onToggle = { onEvent(PriceAlertsContract.UiEvent.Toggle(alert.id)) },
                            onDelete = { onEvent(PriceAlertsContract.UiEvent.Delete(alert.id)) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (state.showCreateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onEvent(PriceAlertsContract.UiEvent.DismissCreate) },
            sheetState = sheetState
        ) {
            CreateAlertSheet(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun AlertCard(
    alert: PriceAlertsContract.AlertUiModel,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val conditionLabel = conditionOptions.find { it.first == alert.condition }?.second ?: alert.condition
    val isPct = alert.condition == "PCT_DROP_INTRADAY"
    val thresholdText = if (isPct) "${alert.threshold}%" else alert.threshold.toString()

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.ticker?.takeIf { it.isNotBlank() } ?: "Listing #${alert.listingId}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!alert.name.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = alert.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$conditionLabel: $thresholdText",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!alert.createdAt.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Kreiran: ${alert.createdAt.take(10)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = alert.active,
                    onCheckedChange = { onToggle() }
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDelete) {
                    Text("Obrisi", color = Color(0xFFEA4335), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CreateAlertSheet(
    state: PriceAlertsContract.UiState,
    onEvent: (PriceAlertsContract.UiEvent) -> Unit
) {
    val isPct = state.condition == "PCT_DROP_INTRADAY"
    val thresholdLabel = if (isPct) "Pad (%)" else "Prag (cena)"
    val isValid = state.selectedListing != null &&
            state.thresholdInput.toDoubleOrNull().let { it != null && it > 0.0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Novi cenovni alarm",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(PriceAlertsContract.UiEvent.Search(it)) },
            label = { Text("Pretrazi akcije") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (state.searchResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    state.searchResults.take(6).forEach { listing ->
                        ListingResultRow(listing = listing) {
                            onEvent(PriceAlertsContract.UiEvent.SelectListing(listing))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        state.selectedListing?.let { listing ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${listing.ticker ?: ""} — ${listing.name ?: ""}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Uslov",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            conditionOptions.forEach { (value, label) ->
                FilterChip(
                    selected = state.condition == value,
                    onClick = { onEvent(PriceAlertsContract.UiEvent.SetCondition(value)) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.thresholdInput,
            onValueChange = { onEvent(PriceAlertsContract.UiEvent.SetThreshold(it)) },
            label = { Text(thresholdLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onEvent(PriceAlertsContract.UiEvent.DismissCreate) }) {
                Text("Otkazi")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { onEvent(PriceAlertsContract.UiEvent.Submit) },
                enabled = isValid && !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sacuvaj", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ListingResultRow(
    listing: ListingSummaryDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listing.ticker ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!listing.name.isNullOrBlank()) {
                Text(
                    text = listing.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (listing.price != null) {
            Text(
                text = "${listing.price} ${listing.currency ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlertsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Nemate cenovnih alarma",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
