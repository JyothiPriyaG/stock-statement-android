package com.stockstatement.inventory

import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.stockstatement.inventory.data.InventoryDatabase
import com.stockstatement.inventory.model.DailyStock
import com.stockstatement.inventory.report.StatementExporter
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var database: InventoryDatabase
    private var reportWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = InventoryDatabase(this)
        setContent {
            InventoryApp(
                database = database,
                onPrint = ::printStatement
            )
        }
    }

    private fun printStatement(date: LocalDate, stocks: List<DailyStock>) {
        reportWebView?.destroy()
        val webView = WebView(this)
        reportWebView = webView
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(
                    "Stock statement $date",
                    view.createPrintDocumentAdapter("Stock statement $date"),
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL(
            null,
            StatementExporter.html(date, stocks),
            "text/html",
            "UTF-8",
            null
        )
    }

    override fun onDestroy() {
        reportWebView?.destroy()
        reportWebView = null
        database.close()
        super.onDestroy()
    }
}
