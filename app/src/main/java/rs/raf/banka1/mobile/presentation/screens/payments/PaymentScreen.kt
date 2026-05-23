package rs.raf.banka1.mobile.presentation.screens.payments

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import rs.raf.banka1.mobile.data.remote.responses.AccountDetailsResponseDto
import rs.raf.banka1.mobile.presentation.navigation.LocalTopBarScanAction
import rs.raf.banka1.mobile.data.remote.responses.NewPaymentResponseDto
import rs.raf.banka1.mobile.presentation.components.ErrorData
import rs.raf.banka1.mobile.presentation.components.ErrorDialog
import rs.raf.banka1.mobile.presentation.components.InputField
import rs.raf.banka1.mobile.presentation.components.OtpInputField
import rs.raf.banka1.mobile.presentation.components.SuccessDialog
import rs.raf.banka1.mobile.presentation.viewmodels.main.PaymentContract
import rs.raf.banka1.mobile.presentation.viewmodels.main.PaymentViewModel

@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToVerification: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.step == PaymentContract.Step.OTP) {
        viewModel.setEvent(PaymentContract.UiEvent.BackToForm)
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanCustomCode()) { result ->
        if (result is QRResult.QRSuccess) {
            result.content.rawValue?.let {
                viewModel.setEvent(PaymentContract.UiEvent.IpsQrScanned(it))
            }
        }
    }

    val topBarScanAction = LocalTopBarScanAction.current
    DisposableEffect(scanLauncher, state.step) {
        topBarScanAction.value = if (state.step == PaymentContract.Step.FORM) {
            {
                scanLauncher.launch(
                    ScannerConfig.build { setBarcodeFormats(listOf(BarcodeFormat.FORMAT_QR_CODE)) }
                )
            }
        } else null
        onDispose { topBarScanAction.value = null }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PaymentContract.SideEffect.OpenVerificationCodes -> onNavigateToVerification()
            }
        }
    }

    if (state.error != null) {
        ErrorDialog(errorData = state.error) {
            viewModel.setEvent(PaymentContract.UiEvent.ClearError)
        }
    }

    if (state.paymentResult != null) {
        PaymentResultDialog(
            result = state.paymentResult!!,
            onClose = {
                viewModel.setEvent(PaymentContract.UiEvent.DismissResult)
                onNavigateBack()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                if (targetState == PaymentContract.Step.OTP) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "payment_step"
        ) { step ->
            when (step) {
                PaymentContract.Step.FORM -> PaymentFormStep(
                    state = state,
                    onEvent = viewModel::setEvent
                )
                PaymentContract.Step.OTP -> PaymentOtpStep(
                    state = state,
                    onEvent = viewModel::setEvent
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PaymentFormStep(
    state: PaymentContract.UiState,
    onEvent: (PaymentContract.UiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Podaci o plaćanju",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        AccountPicker(
            accounts = state.accounts,
            selected = state.fromAccount,
            onSelect = { onEvent(PaymentContract.UiEvent.FromAccountSelected(it)) }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        InputField(
            value = state.toAccountNumber,
            onValueChange = { onEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.ToAccount, it)) },
            label = "Broj računa primaoca",
            error = state.fieldErrors[PaymentContract.Field.ToAccount],
            customKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )

        InputField(
            value = state.recipientName,
            onValueChange = { onEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Recipient, it)) },
            label = "Naziv primaoca",
            error = state.fieldErrors[PaymentContract.Field.Recipient],
            customKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        InputField(
            value = state.amountInput,
            onValueChange = { onEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Amount, it)) },
            label = "Iznos",
            error = state.fieldErrors[PaymentContract.Field.Amount],
            customKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
        )

        InputField(
            value = state.paymentCode,
            onValueChange = { onEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Code, it)) },
            label = "Poziv na broj (npr. 289)",
            error = state.fieldErrors[PaymentContract.Field.Code],
            customKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        )

        InputField(
            value = state.referenceNumber,
            onValueChange = { onEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Reference, it)) },
            label = "Referentni broj (opciono)",
            required = false,
            error = state.fieldErrors[PaymentContract.Field.Reference],
            customKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        InputField(
            value = state.paymentPurpose,
            onValueChange = { onEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Purpose, it)) },
            label = "Svrha plaćanja",
            error = state.fieldErrors[PaymentContract.Field.Purpose],
            customKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onEvent(PaymentContract.UiEvent.RequestOtp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !state.isLoading && state.fromAccount != null,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "Nastavi",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AccountPicker(
    accounts: List<AccountDetailsResponseDto>,
    selected: AccountDetailsResponseDto?,
    onSelect: (AccountDetailsResponseDto) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sa računa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.medium
                        )
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (selected != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selected.brojRacuna ?: "—",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatBalance(selected.raspolozivoStanje)} ${selected.currency ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = if (accounts.isEmpty()) "Učitavanje računa..." else "Izaberite račun",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = account.brojRacuna ?: "—",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "${formatBalance(account.raspolozivoStanje)} ${account.currency ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                onSelect(account)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentOtpStep(
    state: PaymentContract.UiState,
    onEvent: (PaymentContract.UiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Potvrda plaćanja",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PaymentSummaryRow("Sa računa", state.fromAccount?.brojRacuna ?: "—")
                PaymentSummaryRow("Na račun", state.toAccountNumber)
                PaymentSummaryRow("Primalac", state.recipientName)
                PaymentSummaryRow("Iznos", "${state.amountInput} ${state.fromAccount?.currency ?: ""}")
                PaymentSummaryRow("Svrha", state.paymentPurpose)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OtpInputField(
            value = state.otpCode,
            onValueChange = { onEvent(PaymentContract.UiEvent.FieldChanged(PaymentContract.Field.Otp, it)) },
            label = "6-cifreni kod",
            error = state.fieldErrors[PaymentContract.Field.Otp],
            onDone = { if (state.otpCode.length == 6) onEvent(PaymentContract.UiEvent.SubmitPayment) }
        )

        TextButton(
            onClick = { onEvent(PaymentContract.UiEvent.OpenVerificationCodes) }
        ) {
            Text(
                text = "Kod stiže kao notifikacija. Pogledaj sve kodove →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = { onEvent(PaymentContract.UiEvent.SubmitPayment) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !state.isLoading && state.otpCode.length == 6,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "Plati",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        OutlinedButton(
            onClick = { onEvent(PaymentContract.UiEvent.ResendOtp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !state.isLoading && !state.isResending,
            shape = MaterialTheme.shapes.medium
        ) {
            if (state.isResending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "Pošalji kod ponovo",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PaymentSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PaymentResultDialog(
    result: NewPaymentResponseDto,
    onClose: () -> Unit
) {
    val isCompleted = result.status == "COMPLETED"
    if (isCompleted) {
        SuccessDialog(
            title = "Plaćanje uspešno",
            message = result.message ?: "Transakcija je uspešno izvršena.",
            onClose = onClose
        )
    } else {
        ErrorDialog(
            errorData = ErrorData(
                title = "Plaćanje odbijeno",
                message = result.message ?: "Transakcija je odbijena."
            ),
            onClose = onClose
        )
    }
}

private fun formatBalance(amount: Double?): String {
    if (amount == null) return "0.00"
    return java.text.NumberFormat.getNumberInstance(java.util.Locale("sr", "RS")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}
