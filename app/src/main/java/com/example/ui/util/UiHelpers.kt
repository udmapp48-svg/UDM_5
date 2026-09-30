package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairItemEntity
import com.example.ui.viewmodel.RepairViewModel
import java.net.URLEncoder

@Composable
fun safeGetItemsAndPayments(
    repairId: Long,
    viewModel: RepairViewModel
): Pair<List<RepairItemEntity>, List<PaymentEntity>> {
    val itemsFlow = remember(repairId) { viewModel.getItemsForRepair(repairId) }
    val paymentsFlow = remember(repairId) { viewModel.getPaymentsForRepair(repairId) }

    val items by itemsFlow.collectAsState(initial = emptyList())
    val payments by paymentsFlow.collectAsState(initial = emptyList())

    return items to payments
}

object PhoneActions {
    fun callCustomer(context: Context, rawPhone: String) {
        try {
            val cleaned = rawPhone.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openWhatsApp(context: Context, rawPhone: String, message: String = "") {
        try {
            var cleaned = rawPhone.replace(Regex("[^0-9]"), "")
            // If phone starts with 0 and Sri Lanka / standard formatting (e.g. 0771234567 -> 94771234567)
            if (cleaned.startsWith("0")) {
                cleaned = "94" + cleaned.substring(1)
            }
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleaned&text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
