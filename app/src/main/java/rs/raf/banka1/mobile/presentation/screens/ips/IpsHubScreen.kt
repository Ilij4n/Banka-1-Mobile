package rs.raf.banka1.mobile.presentation.screens.ips

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.ScannerConfig
import rs.raf.banka1.mobile.data.local.IpsQrCodeEntity
import rs.raf.banka1.mobile.data.remote.responses.AccountDetailsResponseDto
import rs.raf.banka1.mobile.domain.ips.IpsFields
import rs.raf.banka1.mobile.presentation.components.ErrorDialog
import rs.raf.banka1.mobile.presentation.viewmodels.main.IpsHubContract
import rs.raf.banka1.mobile.presentation.viewmodels.main.IpsHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpsHubScreen(
    viewModel: IpsHubViewModel,
    onNavigateToPaymentWithIps: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fabExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is IpsHubContract.SideEffect.NavigateToPaymentWithIps ->
                    onNavigateToPaymentWithIps(effect.encodedPayload)
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanCustomCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val raw = result.content.rawValue ?: return@rememberLauncherForActivityResult
                viewModel.setEvent(IpsHubContract.UiEvent.QrScanned(raw))
            }
            else -> {}
        }
    }

    ErrorDialog(
        errorData = state.error,
        onClose = { viewModel.setEvent(IpsHubContract.UiEvent.ClearError) }
    )

    state.showingCode?.let { entity ->
        QrCodeDisplaySheet(
            entity = entity,
            onDismiss = { viewModel.setEvent(IpsHubContract.UiEvent.DismissCode) }
        )
    }

    if (state.isCreating) {
        IpsFormSheet(
            title = "Kreiraj IPS kod",
            initial = null,
            accounts = state.accounts,
            onConfirm = { viewModel.setEvent(IpsHubContract.UiEvent.ConfirmCreate(it)) },
            onDismiss = { viewModel.setEvent(IpsHubContract.UiEvent.DismissSheet) }
        )
    }

    state.editingEntity?.let { entity ->
        IpsFormSheet(
            title = "Izmeni IPS kod",
            initial = entity,
            accounts = state.accounts,
            onConfirm = { viewModel.setEvent(IpsHubContract.UiEvent.ConfirmEdit(entity.id, it)) },
            onDismiss = { viewModel.setEvent(IpsHubContract.UiEvent.DismissSheet) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — matches HistoryScreen / AccountsCardsScreen pattern
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "IPS Plaćanja",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Skenirajte ili kreirajte QR kodove za primanje plaćanja",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.qrCodes.isEmpty()) {
                EmptyStateContent(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.qrCodes, key = { it.id }) { entity ->
                        QrCodeListItem(
                            entity = entity,
                            onShow = { viewModel.setEvent(IpsHubContract.UiEvent.ShowCode(entity)) },
                            onEdit = { viewModel.setEvent(IpsHubContract.UiEvent.StartEdit(entity)) },
                            onDelete = { viewModel.setEvent(IpsHubContract.UiEvent.Delete(entity.id)) }
                        )
                    }
                    // bottom padding so last item isn't hidden under FAB
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // Expandable FAB column anchored to bottom-end
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            scanLauncher.launch(ScannerConfig.build {
                                setBarcodeFormats(
                                    listOf(io.github.g00fy2.quickie.config.BarcodeFormat.FORMAT_QR_CODE)
                                )
                            })
                        },
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        text = { Text("Skeniraj kod") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            viewModel.setEvent(IpsHubContract.UiEvent.StartCreate)
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Kreiraj kod") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                if (fabExpanded) {
                    Icon(Icons.Default.Close, contentDescription = "Zatvori")
                } else {
                    Icon(Icons.Default.QrCode, contentDescription = "IPS QR opcije")
                }
            }
        }
    }
}

@Composable
private fun EmptyStateContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nemate sačuvanih IPS kodova",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Koristite dugme ispod da skenirate ili kreirate QR kod za primanje plaćanja.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QrCodeListItem(
    entity: IpsQrCodeEntity,
    onShow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.recipientName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entity.purpose ?: entity.recipientAccount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entity.amount?.let { amount ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatAmount(amount)} RSD",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opcije",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Prikaži kod") },
                        leadingIcon = {
                            Icon(Icons.Default.QrCode, contentDescription = null)
                        },
                        onClick = { menuExpanded = false; onShow() }
                    )
                    DropdownMenuItem(
                        text = { Text("Izmeni") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Obriši", color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrCodeDisplaySheet(
    entity: IpsQrCodeEntity,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val original = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window?.attributes = window?.attributes?.also { it.screenBrightness = 1.0f }
        onDispose {
            window?.attributes = window?.attributes?.also { it.screenBrightness = original }
        }
    }

    val bitmap = remember(entity.rawIpsString) { generateQrBitmap(entity.rawIpsString) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = entity.recipientName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            entity.purpose?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            entity.amount?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatAmount(it)} RSD",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
            if (bitmap != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "IPS QR kod",
                        modifier = Modifier
                            .padding(12.dp)
                            .size(240.dp)
                    )
                }
            } else {
                Text("Greška pri generisanju QR koda", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = entity.recipientAccount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IpsFormSheet(
    title: String,
    initial: IpsQrCodeEntity?,
    accounts: List<AccountDetailsResponseDto>,
    onConfirm: (IpsFields) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Explicit focus requesters to ensure "Next" works reliably inside the bottom sheet
    val nameFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val purposeFocus = remember { FocusRequester() }
    val refFocus = remember { FocusRequester() }

    val nextTextOpts = KeyboardOptions(imeAction = ImeAction.Next)
    val nextNumberOpts = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    val nextDecimalOpts = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    val doneTextOpts = KeyboardOptions(imeAction = ImeAction.Done)

    var recipientAccount by remember { mutableStateOf(initial?.recipientAccount ?: "") }
    var recipientName by remember { mutableStateOf(initial?.recipientName ?: "") }
    var amountInput by remember {
        mutableStateOf(
            initial?.amount?.let {
                val i = it.toLong(); val d = Math.round((it - i) * 100)
                "$i,${d.toString().padStart(2, '0')}"
            } ?: ""
        )
    }
    var paymentCode by remember { mutableStateOf(initial?.paymentCode ?: "") }
    var purpose by remember { mutableStateOf(initial?.purpose ?: "") }
    var referenceNumber by remember { mutableStateOf(initial?.referenceNumber ?: "") }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Move FocusManager and KeyboardController INSIDE the ModalBottomSheet
        // so they act on the sheet's window instead of the background screen.
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        val doneActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            keyboardController?.hide()
        })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Account picker
            if (accounts.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = accountDropdownExpanded,
                    onExpandedChange = { accountDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.brojRacuna == recipientAccount }
                            ?.let { "${it.nazivRacuna ?: it.brojRacuna}" }
                            ?: recipientAccount.ifBlank { "Izaberite račun" },
                        onValueChange = {},
                        label = { Text("Račun primaoca") },
                        readOnly = true,
                        // menuAnchor() makes the entire field act as a click target
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = accountDropdownExpanded
                            )
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        isError = errors.containsKey("account"),
                        supportingText = errors["account"]?.let { { Text(it) } }
                    )

                    ExposedDropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            acc.nazivRacuna ?: acc.brojRacuna ?: "",
                                            fontWeight = FontWeight.Medium
                                        )
                                        acc.brojRacuna?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    recipientAccount = acc.brojRacuna ?: ""
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = recipientAccount,
                    onValueChange = { recipientAccount = it; errors = errors - "account" },
                    label = { Text("Broj računa primaoca") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = nextNumberOpts,
                    keyboardActions = KeyboardActions(onNext = { nameFocus.requestFocus() }),
                    isError = errors.containsKey("account"),
                    supportingText = errors["account"]?.let { { Text(it) } }
                )
            }

            OutlinedTextField(
                value = recipientName,
                onValueChange = { recipientName = it; errors = errors - "name" },
                label = { Text("Naziv primaoca") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus),
                singleLine = true,
                isError = errors.containsKey("name"),
                keyboardOptions = nextTextOpts,
                keyboardActions = KeyboardActions(onNext = { amountFocus.requestFocus() }),
                supportingText = errors["name"]?.let { { Text(it) } }
            )
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Iznos (opciono)") },
                placeholder = { Text("npr. 1500,00") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus),
                singleLine = true,
                keyboardOptions = nextDecimalOpts,
                keyboardActions = KeyboardActions(onNext = { codeFocus.requestFocus() })
            )
            OutlinedTextField(
                value = paymentCode,
                onValueChange = { paymentCode = it },
                label = { Text("Poziv na broj — SF (opciono)") },
                placeholder = { Text("npr. 289") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(codeFocus),
                singleLine = true,
                keyboardOptions = nextNumberOpts,
                keyboardActions = KeyboardActions(onNext = { purposeFocus.requestFocus() })
            )
            OutlinedTextField(
                value = purpose,
                onValueChange = { purpose = it },
                label = { Text("Svrha plaćanja (opciono)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(purposeFocus),
                singleLine = true,
                keyboardOptions = nextTextOpts,
                keyboardActions = KeyboardActions(onNext = { refFocus.requestFocus() })
            )
            OutlinedTextField(
                value = referenceNumber,
                onValueChange = { referenceNumber = it },
                label = { Text("Poziv na broj — RO (opciono)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(refFocus),
                singleLine = true,
                keyboardOptions = doneTextOpts,
                keyboardActions = doneActions // Uses the correctly scoped focusManager
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Otkaži") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val newErrors = mutableMapOf<String, String>()
                        if (!recipientAccount.matches(Regex("^\\d{18,19}$"))) {
                            newErrors["account"] = "Broj računa mora imati 18–19 cifara"
                        }
                        if (recipientName.isBlank()) {
                            newErrors["name"] = "Naziv primaoca je obavezan"
                        }
                        if (newErrors.isNotEmpty()) { errors = newErrors; return@TextButton }

                        // Clear focus and close keyboard on successful save
                        focusManager.clearFocus()
                        keyboardController?.hide()

                        onConfirm(
                            IpsFields(
                                recipientAccount = recipientAccount,
                                recipientName = recipientName,
                                amount = amountInput.replace(",", ".").toDoubleOrNull(),
                                paymentCode = paymentCode.ifBlank { null },
                                purpose = purpose.ifBlank { null },
                                referenceNumber = referenceNumber.ifBlank { null }
                            )
                        )
                    }
                ) {
                    Text("Sačuvaj", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? = try {
    val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
    }
    bitmap
} catch (_: Exception) { null }

private fun formatAmount(amount: Double): String {
    val i = amount.toLong()
    val d = Math.round((amount - i) * 100)
    return "$i,${d.toString().padStart(2, '0')}"
}
