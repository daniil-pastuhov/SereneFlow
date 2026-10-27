package com.sereneflow.timer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneflow.timer.platform.TipProduct
import com.sereneflow.timer.platform.TipResult
import com.sereneflow.timer.platform.TipStore
import com.sereneflow.timer.platform.rememberTipStore
import com.sereneflow.timer.resources.*
import com.sereneflow.timer.theme.LocalSereneColors
import com.sereneflow.timer.ui.platform.ConfirmDialog
import com.sereneflow.timer.ui.platform.PlatformIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface TipJarState {
    data object Loading : TipJarState
    data object Unavailable : TipJarState
    data class Offer(val products: List<TipProduct>) : TipJarState
    data object Thanked : TipJarState
    data object Pending : TipJarState
}

/**
 * "Buy me a coffee": tips for the developer in cups of coffee, sold as the stores' in-app
 * purchases. Opened from the routine list's banner and from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipJarScreen(onBack: () -> Unit, modifier: Modifier = Modifier, store: TipStore = rememberTipStore()) {
    val colors = LocalSereneColors.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TipJarState>(TipJarState.Loading) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    // The middle size, which most people pick.
    var selectedCups by remember { mutableIntStateOf(3) }
    var purchasing by remember { mutableStateOf(false) }
    var showFailed by remember { mutableStateOf(false) }

    LaunchedEffect(loadAttempt) {
        state = TipJarState.Loading
        val products = store.products()
        state = if (products.isEmpty()) TipJarState.Unavailable else TipJarState.Offer(products)
    }

    if (showFailed) {
        ConfirmDialog(
            title = stringResource(Res.string.tip_failed_title),
            message = stringResource(Res.string.tip_failed_message),
            confirmLabel = stringResource(Res.string.action_ok),
            destructive = false,
            onConfirm = { showFailed = false },
            onDismiss = { showFailed = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.tip_title),
                        modifier = Modifier.semantics { heading() },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = PlatformIcons.Back, contentDescription = stringResource(Res.string.action_back), tint = colors.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { paddingValues ->
        ReadableWidth(modifier = Modifier.fillMaxSize().padding(paddingValues)) { sidePadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Icon(
                        imageVector = Icons.Default.LocalCafe,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer.copy(alpha = 0.45f))
                            .padding(20.dp)
                    )
                }
                item {
                    Text(
                        text = stringResource(Res.string.tip_message),
                        fontSize = 15.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                when (val current = state) {
                    TipJarState.Loading -> item { TipNote(stringResource(Res.string.loading)) }
                    TipJarState.Unavailable -> item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TipNote(stringResource(Res.string.tip_unavailable))
                            TextButton(onClick = { loadAttempt++ }) {
                                Text(stringResource(Res.string.tip_retry), color = colors.primary)
                            }
                        }
                    }
                    TipJarState.Thanked -> item { TipOutcome(stringResource(Res.string.tip_thanks)) }
                    TipJarState.Pending -> item { TipOutcome(stringResource(Res.string.tip_pending)) }
                    is TipJarState.Offer -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colors.cardBackground)
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                                    .padding(14.dp)
                                    .selectableGroup(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                current.products.forEach { product ->
                                    TipRow(product, product.cups == selectedCups) { selectedCups = product.cups }
                                }
                            }
                        }
                        item {
                            val product = current.products.firstOrNull { it.cups == selectedCups } ?: current.products.first()
                            Button(
                                onClick = {
                                    purchasing = true
                                    scope.launch {
                                        when (store.buy(product)) {
                                            TipResult.SUCCESS -> state = TipJarState.Thanked
                                            TipResult.PENDING -> state = TipJarState.Pending
                                            TipResult.FAILED -> showFailed = true
                                            TipResult.CANCELLED -> {}
                                        }
                                        purchasing = false
                                    }
                                },
                                enabled = !purchasing,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                            ) {
                                Text(
                                    text = stringResource(Res.string.tip_buy, product.price),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipRow(product: TipProduct, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalSereneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // One cup icon per coffee; the label below says how many.
        Row(modifier = Modifier.width(112.dp).clearAndSetSemantics {}) {
            repeat(product.cups) {
                Icon(
                    imageVector = Icons.Default.LocalCafe,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = pluralStringResource(Res.plurals.tip_cups, product.cups, product.cups),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(text = product.price, fontSize = 15.sp, color = colors.onSurfaceVariant)
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null, // the row is announced as a selected radio button
            tint = if (isSelected) colors.primary else Color.Transparent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TipNote(text: String) {
    val colors = LocalSereneColors.current
    Text(
        text = text,
        fontSize = 14.sp,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
    )
}

@Composable
private fun TipOutcome(text: String) {
    val colors = LocalSereneColors.current
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        color = colors.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
    )
}
