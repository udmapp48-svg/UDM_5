package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.theme.PaymentUnpaid
import com.example.ui.theme.StatusCancelled
import com.example.ui.theme.StatusChecking
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusReady
import com.example.ui.theme.StatusReceived
import com.example.ui.theme.StatusRepairing
import java.util.Locale

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status.uppercase(Locale.getDefault())) {
        "RECEIVED" -> StatusReceived.copy(alpha = 0.15f) to StatusReceived
        "CHECKING" -> StatusChecking.copy(alpha = 0.15f) to StatusChecking
        "REPAIRING" -> StatusRepairing.copy(alpha = 0.15f) to StatusRepairing
        "READY" -> StatusReady.copy(alpha = 0.15f) to StatusReady
        "DELIVERED" -> StatusDelivered.copy(alpha = 0.15f) to StatusDelivered
        "CANCELLED" -> StatusCancelled.copy(alpha = 0.15f) to StatusCancelled
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = status.uppercase(Locale.getDefault()),
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun PaymentBadge(paymentStatus: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (paymentStatus.uppercase(Locale.getDefault())) {
        "PAID" -> PaymentPaid.copy(alpha = 0.15f) to PaymentPaid
        "PARTIALLY PAID" -> PaymentPartial.copy(alpha = 0.15f) to PaymentPartial
        else -> PaymentUnpaid.copy(alpha = 0.15f) to PaymentUnpaid
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = paymentStatus.uppercase(Locale.getDefault()),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String = "Search...",
    modifier: Modifier = Modifier,
    testTag: String = "search_input"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholderText, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = CyanAccent)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

/**
 * Smart Autocomplete with live filtering and "Add New [Type]: [value]" action.
 * Displays suggestion chips and popup directly attached to the field above the keyboard.
 */
@Composable
fun SmartAutocompleteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    onAddNew: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String = "smart_autocomplete"
) {
    var expanded by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) }
    }

    val exactMatch = remember(value, suggestions) {
        suggestions.any { it.equals(value.trim(), ignoreCase = true) }
    }

    val localFocusManager = LocalFocusManager.current

    val effectiveKeyboardActions = remember(keyboardActions, expanded, filteredSuggestions, exactMatch) {
        KeyboardActions(
            onNext = {
                if (expanded && filteredSuggestions.isNotEmpty() && !exactMatch) {
                    val top = filteredSuggestions.firstOrNull()
                    if (top != null) {
                        onSuggestionSelected(top)
                    }
                }
                expanded = false
                try {
                    keyboardActions.onNext?.invoke(this) ?: run {
                        if (!localFocusManager.moveFocus(FocusDirection.Down)) {
                            localFocusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                } catch (_: Exception) {}
            },
            onDone = {
                if (expanded && filteredSuggestions.isNotEmpty() && !exactMatch) {
                    val top = filteredSuggestions.firstOrNull()
                    if (top != null) {
                        onSuggestionSelected(top)
                    }
                }
                expanded = false
                try {
                    keyboardActions.onDone?.invoke(this) ?: localFocusManager.clearFocus()
                } catch (_: Exception) {}
            }
        )
    }

    Column(modifier = modifier) {
        val baseModifier = if (focusRequester != null) {
            Modifier.focusRequester(focusRequester)
        } else {
            Modifier
        }

        val textFieldModifier = baseModifier
            .fillMaxWidth()
            .testTag(testTag)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    expanded = true
                }
            }

        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = effectiveKeyboardActions,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Show suggestions")
                }
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = textFieldModifier
        )

        // HORIZONTAL SUGGESTION CHIPS (Fast 1-tap selection, scrollable horizontally)
        if (expanded && (filteredSuggestions.isNotEmpty() || (value.isNotBlank() && !exactMatch))) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (value.isNotBlank() && !exactMatch) {
                    item {
                        Surface(
                            color = CyanAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent),
                            modifier = Modifier
                                .clickable {
                                    onAddNew(value.trim())
                                    onSuggestionSelected(value.trim())
                                    expanded = false
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add \"${value.trim()}\"",
                                    color = CyanAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                items(filteredSuggestions) { suggestion ->
                    Surface(
                        color = CyanAccent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable {
                                onSuggestionSelected(suggestion)
                                expanded = false
                            }
                    ) {
                        Text(
                            text = suggestion,
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessoriesSelector(
    selectedAccessories: Set<String>,
    onToggleAccessory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        "SIM Tray",
        "Back Cover",
        "Memory Card",
        "Charger",
        "Device Only",
        "SIM Card",
        "Battery"
    )

    Column(modifier = modifier) {
        Text(
            text = "Accessories Received",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { item ->
                val isSelected = selectedAccessories.contains(item)
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleAccessory(item) },
                    label = { Text(item, fontSize = 12.sp) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                        selectedLabelColor = CyanAccent,
                        selectedLeadingIconColor = CyanAccent
                    )
                )
            }
        }
    }
}
