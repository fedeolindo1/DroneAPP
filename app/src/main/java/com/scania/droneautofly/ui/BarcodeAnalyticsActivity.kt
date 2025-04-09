package com.scania.droneautofly.ui

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scania.droneautofly.R
import com.scania.droneautofly.adapters.BarcodeAdapter
import com.scania.droneautofly.model.BarcodeAnalytics
import com.scania.droneautofly.model.ScannedBarcode
import com.scania.droneautofly.utils.BarcodeDatabase
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class BarcodeAnalyticsActivity : AppCompatActivity() {

    private lateinit var tvTotalScanned: TextView
    private lateinit var tvUniqueCount: TextView
    private lateinit var tvMostCommonFormat: TextView
    private lateinit var tvScanRate: TextView
    private lateinit var pieChartFormats: PieChart
    private lateinit var barChartScanTimes: BarChart
    private lateinit var recyclerViewBarcodes: RecyclerView
    private lateinit var barcodeAdapter: BarcodeAdapter

    private val barcodesList = ArrayList<ScannedBarcode>()
    private val barcodeDatabase = BarcodeDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_analytics)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Análise de Códigos de Barras"

        // Initialize UI components
        initUI()

        // Load data
        loadBarcodeData()

        // Update analytics
        updateAnalytics()

        // Setup charts
        setupPieChart()
        setupBarChart()

        // Setup RecyclerView
        setupRecyclerView()
    }

    private fun initUI() {
        tvTotalScanned = findViewById(R.id.tv_total_scanned)
        tvUniqueCount = findViewById(R.id.tv_unique_count)
        tvMostCommonFormat = findViewById(R.id.tv_most_common_format)
        tvScanRate = findViewById(R.id.tv_scan_rate)
        pieChartFormats = findViewById(R.id.pie_chart_formats)
        barChartScanTimes = findViewById(R.id.bar_chart_scan_times)
        recyclerViewBarcodes = findViewById(R.id.recycler_view_barcodes)
    }

    private fun loadBarcodeData() {
        // In a real implementation, this would load data from the database
        // For now, we'll use the in-memory database
        barcodesList.clear()
        barcodesList.addAll(barcodeDatabase.getAllBarcodes())
    }

    private fun updateAnalytics() {
        // Calculate analytics
        val analytics = calculateBarcodeAnalytics(barcodesList)

        // Update UI
        tvTotalScanned.text = analytics.totalScanned.toString()
        tvUniqueCount.text = analytics.uniqueBarcodes.toString()
        tvMostCommonFormat.text = analytics.mostCommonFormat ?: "N/A"
        tvScanRate.text = String.format("%.2f códigos/min", analytics.scanRate)
    }

    private fun calculateBarcodeAnalytics(barcodes: List<ScannedBarcode>): BarcodeAnalytics {
        if (barcodes.isEmpty()) {
            return BarcodeAnalytics(0, 0, null, 0.0)
        }

        // Count unique barcodes by value
        val uniqueValues = HashSet<String>()
        barcodes.forEach { uniqueValues.add(it.barcodeValue) }

        // Find most common format
        val formatCounts = HashMap<String, Int>()
        barcodes.forEach {
            formatCounts[it.barcodeFormat] = (formatCounts[it.barcodeFormat] ?: 0) + 1
        }
        val mostCommonFormat = formatCounts.maxByOrNull { it.value }?.key

        // Calculate scan rate (barcodes per minute)
        var scanRate = 0.0
        if (barcodes.size > 1) {
            val firstTimestamp = barcodes.minByOrNull { it.timestamp }?.timestamp ?: 0
            val lastTimestamp = barcodes.maxByOrNull { it.timestamp }?.timestamp ?: 0
            val durationMinutes = (lastTimestamp - firstTimestamp) / (1000.0 * 60.0)
            if (durationMinutes > 0) {
                scanRate = barcodes.size / durationMinutes
            }
        }

        return BarcodeAnalytics(
            totalScanned = barcodes.size,
            uniqueBarcodes = uniqueValues.size,
            mostCommonFormat = mostCommonFormat,
            scanRate = scanRate
        )
    }

    private fun setupPieChart() {
        // Count barcode formats
        val formatCounts = HashMap<String, Int>()
        barcodesList.forEach {
            formatCounts[it.barcodeFormat] = (formatCounts[it.barcodeFormat] ?: 0) + 1
        }

        // Create pie chart entries
        val entries = ArrayList<PieEntry>()
        formatCounts.forEach { (format, count) ->
            entries.add(PieEntry(count.toFloat(), format))
        }

        // Create dataset
        val dataSet = PieDataSet(entries, "Formatos de Código de Barras")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.WHITE

        // Create data
        val data = PieData(dataSet)

        // Configure chart
        pieChartFormats.data = data
        pieChartFormats.description.isEnabled = false
        pieChartFormats.centerText = "Formatos"
        pieChartFormats.setCenterTextSize(18f)
        pieChartFormats.setEntryLabelTextSize(12f)
        pieChartFormats.setEntryLabelColor(Color.WHITE)
        pieChartFormats.legend.textSize = 12f
        pieChartFormats.animateY(1000)
        pieChartFormats.invalidate()
    }

    private fun setupBarChart() {
        // Group scans by hour
        val calendar = Calendar.getInstance()
        val hourCounts = HashMap<Int, Int>()
        for (i in 0..23) {
            hourCounts[i] = 0
        }

        barcodesList.forEach {
            calendar.timeInMillis = it.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            hourCounts[hour] = (hourCounts[hour] ?: 0) + 1
        }

        // Create bar entries
        val entries = ArrayList<BarEntry>()
        val hours = ArrayList<String>()
        hourCounts.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
            hours.add("${entry.key}h")
        }

        // Create dataset
        val dataSet = BarDataSet(entries, "Scans por Hora")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 12f

        // Create data
        val data = BarData(dataSet)
        data.barWidth = 0.6f

        // Configure chart
        barChartScanTimes.data = data
        barChartScanTimes.description.isEnabled = false
        barChartScanTimes.setFitBars(true)
        barChartScanTimes.xAxis.valueFormatter = IndexAxisValueFormatter(hours)
        barChartScanTimes.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChartScanTimes.xAxis.granularity = 1f
        barChartScanTimes.xAxis.setDrawGridLines(false)
        barChartScanTimes.axisLeft.setDrawGridLines(false)
        barChartScanTimes.axisRight.isEnabled = false
        barChartScanTimes.legend.textSize = 12f
        barChartScanTimes.animateY(1000)
        barChartScanTimes.invalidate()
    }

    private fun setupRecyclerView() {
        barcodeAdapter = BarcodeAdapter(barcodesList)
        recyclerViewBarcodes.layoutManager = LinearLayoutManager(this)
        recyclerViewBarcodes.adapter = barcodeAdapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}