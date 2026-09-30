package com.example.util

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import java.util.Locale

object ReceiptHelper {

    fun generateReceiptText(
        repair: RepairEntity,
        items: List<RepairItemEntity>,
        payments: List<PaymentEntity>,
        settings: AppSettingsEntity
    ): String {
        val sb = StringBuilder()
        val cur = settings.currency
        sb.appendLine("==================================")
        sb.appendLine("     ${settings.shopName.uppercase(Locale.getDefault())}     ")
        sb.appendLine("==================================")
        sb.appendLine("Phone: ${settings.shopPhone}")
        sb.appendLine("Address: ${settings.shopAddress}")
        sb.appendLine("----------------------------------")
        sb.appendLine("JOB NUMBER: #${repair.jobNumber}")
        sb.appendLine("Date: ${repair.receivedDate}  Time: ${repair.receivedTime}")
        sb.appendLine("Status: ${repair.status}")
        sb.appendLine("----------------------------------")
        sb.appendLine("CUSTOMER DETAILS")
        sb.appendLine("Name:  ${repair.customerName}")
        sb.appendLine("Phone: ${repair.customerPhone}")
        sb.appendLine("----------------------------------")
        sb.appendLine("DEVICE DETAILS")
        sb.appendLine("Device: ${repair.brand} ${repair.model}")
        if (repair.deviceColour.isNotBlank()) sb.appendLine("Colour: ${repair.deviceColour}")
        if (repair.imei.isNotBlank()) sb.appendLine("IMEI:   ${repair.imei}")
        if (repair.accessories.isNotBlank()) sb.appendLine("Accs:   ${repair.accessories}")
        sb.appendLine("Fault:  ${repair.fault}")
        sb.appendLine("----------------------------------")
        sb.appendLine("REPAIR ITEMS & SERVICES")
        if (items.isEmpty()) {
            sb.appendLine("1. Service - $cur ${String.format(Locale.US, "%,.2f", repair.totalPrice)}")
        } else {
            items.forEachIndexed { i, item ->
                sb.appendLine("${i + 1}. ${item.repairType} - $cur ${String.format(Locale.US, "%,.2f", item.price)}")
            }
        }
        sb.appendLine("----------------------------------")
        sb.appendLine("TOTAL REPAIR: $cur ${String.format(Locale.US, "%,.2f", repair.totalPrice)}")
        sb.appendLine("TOTAL PAID:   $cur ${String.format(Locale.US, "%,.2f", repair.amountPaid)}")
        sb.appendLine("BALANCE DUE:  $cur ${String.format(Locale.US, "%,.2f", repair.balance)}")
        sb.appendLine("PAYMENT:      ${repair.paymentStatus}")
        sb.appendLine("----------------------------------")
        if (payments.isNotEmpty()) {
            sb.appendLine("PAYMENT TRANSACTIONS:")
            payments.forEach { p ->
                sb.appendLine(" - #${p.paymentNumber} (${p.date} ${p.time}): $cur ${String.format(Locale.US, "%,.2f", p.amount)}")
            }
            sb.appendLine("----------------------------------")
        }
        sb.appendLine(settings.receiptFooter)
        sb.appendLine("==================================")
        return sb.toString()
    }

    fun generateHtmlReceipt(
        repair: RepairEntity,
        items: List<RepairItemEntity>,
        payments: List<PaymentEntity>,
        settings: AppSettingsEntity
    ): String {
        val cur = settings.currency
        val itemsHtml = StringBuilder()
        if (items.isEmpty()) {
            itemsHtml.append("<tr><td>Service & Repair</td><td style='text-align:right;'>$cur ${String.format(Locale.US, "%,.2f", repair.totalPrice)}</td></tr>")
        } else {
            items.forEach { item ->
                itemsHtml.append("<tr><td>${item.repairType}</td><td style='text-align:right;'>$cur ${String.format(Locale.US, "%,.2f", item.price)}</td></tr>")
            }
        }

        val paymentsHtml = StringBuilder()
        payments.forEach { p ->
            paymentsHtml.append("<tr><td>Pay #${p.paymentNumber} (${p.date})</td><td style='text-align:right;'>$cur ${String.format(Locale.US, "%,.2f", p.amount)}</td></tr>")
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Receipt #${repair.jobNumber}</title>
                <style>
                    body { font-family: 'Courier New', monospace; font-size: 13px; color: #111; margin: 20px; }
                    .header { text-align: center; border-bottom: 2px dashed #333; padding-bottom: 10px; margin-bottom: 12px; }
                    .header h2 { margin: 0; font-size: 20px; }
                    .job-badge { font-size: 22px; font-weight: bold; background: #eee; padding: 4px 12px; display: inline-block; margin: 8px 0; border: 1px solid #333; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
                    th, td { padding: 4px 2px; }
                    .line { border-bottom: 1px dashed #555; margin: 8px 0; }
                    .total-box { font-size: 15px; font-weight: bold; }
                    .footer { text-align: center; font-size: 11px; margin-top: 15px; border-top: 1px solid #ddd; padding-top: 8px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h2>${settings.shopName}</h2>
                    <div>${settings.shopAddress}</div>
                    <div>Phone: ${settings.shopPhone}</div>
                    <div class="job-badge">JOB #${repair.jobNumber}</div>
                    <div>Date: ${repair.receivedDate} ${repair.receivedTime}</div>
                    <div>Status: <b>${repair.status}</b></div>
                </div>

                <div><b>Customer:</b> ${repair.customerName} (${repair.customerPhone})</div>
                <div><b>Device:</b> ${repair.brand} ${repair.model} ${if (repair.deviceColour.isNotBlank()) "(${repair.deviceColour})" else ""}</div>
                ${if (repair.imei.isNotBlank()) "<div><b>IMEI:</b> ${repair.imei}</div>" else ""}
                ${if (repair.accessories.isNotBlank()) "<div><b>Accessories:</b> ${repair.accessories}</div>" else ""}
                <div><b>Fault:</b> ${repair.fault}</div>

                <div class="line"></div>
                <b>REPAIR ITEMS:</b>
                <table>
                    $itemsHtml
                </table>

                <div class="line"></div>
                <table class="total-box">
                    <tr><td>Total Repair:</td><td style="text-align:right;">$cur ${String.format(Locale.US, "%,.2f", repair.totalPrice)}</td></tr>
                    <tr><td>Amount Paid:</td><td style="text-align:right;">$cur ${String.format(Locale.US, "%,.2f", repair.amountPaid)}</td></tr>
                    <tr><td>Balance Due:</td><td style="text-align:right; color:#d32f2f;">$cur ${String.format(Locale.US, "%,.2f", repair.balance)}</td></tr>
                    <tr><td>Payment Status:</td><td style="text-align:right;">${repair.paymentStatus}</td></tr>
                </table>

                ${if (payments.isNotEmpty()) """
                <div class="line"></div>
                <b>Payment History:</b>
                <table>$paymentsHtml</table>
                """.trimIndent() else ""}

                <div class="footer">
                    <p>${settings.receiptFooter}</p>
                    <p>Technician Signature: _____________________</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun printReceipt(
        context: Context,
        repair: RepairEntity,
        items: List<RepairItemEntity>,
        payments: List<PaymentEntity>,
        settings: AppSettingsEntity
    ) {
        val webView = WebView(context)
        val htmlDocument = generateHtmlReceipt(repair, items, payments, settings)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Job_${repair.jobNumber}_Receipt")
                printManager?.print(
                    "Job_${repair.jobNumber}_Receipt",
                    printAdapter,
                    PrintAttributes.Builder().build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)
    }

    fun shareReceipt(
        context: Context,
        repair: RepairEntity,
        items: List<RepairItemEntity>,
        payments: List<PaymentEntity>,
        settings: AppSettingsEntity
    ) {
        val text = generateReceiptText(repair, items, payments, settings)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Receipt #${repair.jobNumber}")
        context.startActivity(shareIntent)
    }
}
