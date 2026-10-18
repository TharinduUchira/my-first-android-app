package com.example.myfirstapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: SalaryDbHelper
    private lateinit var recordsContainer: LinearLayout
    private lateinit var netPayText: TextView
    private lateinit var grossPayText: TextView
    private lateinit var epfDeductionText: TextView
    private lateinit var employerCostText: TextView

    // Form controls
    private lateinit var currencySpinner: Spinner
    private lateinit var periodInput: EditText
    private lateinit var basicSalaryInput: EditText
    private lateinit var allowanceInput: EditText
    private lateinit var otHoursInput: EditText
    private lateinit var otRateInput: EditText
    private lateinit var otherDeductionsInput: EditText
    private lateinit var epfCheckbox: CheckBox

    private var selectedCurrency: String = "LKR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = SalaryDbHelper(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 48)
        }
        scrollView.addView(root)

        // Title Header
        val header = TextView(this).apply {
            text = "Payroll & Salary Manager"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        }
        val subHeader = TextView(this).apply {
            text = "Track earnings, OT, and statutory EPF/ETF"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, 32)
        }
        root.addView(header)
        root.addView(subHeader)

        // Currency Selector Card
        val currencyCard = createCardLayout()
        val currencyLabel = TextView(this).apply {
            text = "Select Currency"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 0, 0, 12)
        }
        currencySpinner = Spinner(this).apply {
            val currencies = arrayOf("LKR (Rs.)", "USD ($)")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currencies)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedCurrency = if (position == 0) "LKR" else "USD"
                    updateCalculations()
                    loadRecords()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        currencyCard.addView(currencyLabel)
        currencyCard.addView(currencySpinner)
        root.addView(currencyCard)

        // Earnings Section Card
        val earningsCard = createCardLayout()
        val earningsTitle = createSectionTitle("1. Earnings & Inclusions")
        periodInput = createInputField("Pay Period / Month (e.g. Oct 2026)", InputType.TYPE_CLASS_TEXT)
        basicSalaryInput = createInputField("Basic Salary", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        allowanceInput = createInputField("Fixed Allowances / Budgetary Relief", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        
        val otRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        otHoursInput = createInputField("OT Hours", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 12 }
        }
        otRateInput = createInputField("OT Rate / Hr", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        otRow.addView(otHoursInput)
        otRow.addView(otRateInput)

        earningsCard.addView(earningsTitle)
        earningsCard.addView(periodInput)
        earningsCard.addView(basicSalaryInput)
        earningsCard.addView(allowanceInput)
        earningsCard.addView(otRow)
        root.addView(earningsCard)

        // Deductions & Statutory Card
        val deductionsCard = createCardLayout()
        val deductionsTitle = createSectionTitle("2. Deductions & Statutory Contributions")
        
        epfCheckbox = CheckBox(this).apply {
            text = "Deduct EPF (8% Employee Contribution)"
            isChecked = true
            setTextColor(Color.parseColor("#1E293B"))
            setOnCheckedChangeListener { _, _ -> updateCalculations() }
        }
        otherDeductionsInput = createInputField("Other Deductions (Salary Advance / Loan / Tax)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

        deductionsCard.addView(deductionsTitle)
        deductionsCard.addView(epfCheckbox)
        deductionsCard.addView(otherDeductionsInput)
        root.addView(deductionsCard)

        // Live Summary Card
        val summaryCard = createCardLayout().apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 24f
            }
        }
        val summaryTitle = TextView(this).apply {
            text = "Estimated Net Take-Home"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        netPayText = TextView(this).apply {
            text = "Rs. 0.00"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 4, 0, 16)
        }
        grossPayText = createSummaryLine("Gross Earnings: Rs. 0.00")
        epfDeductionText = createSummaryLine("Employee EPF (8%): Rs. 0.00")
        employerCostText = createSummaryLine("Employer Liability (EPF 12% + ETF 3%): Rs. 0.00")

        summaryCard.addView(summaryTitle)
        summaryCard.addView(netPayText)
        summaryCard.addView(grossPayText)
        summaryCard.addView(epfDeductionText)
        summaryCard.addView(employerCostText)
        root.addView(summaryCard)

        // Action Buttons
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 24, 0, 36)
        }
        val saveBtn = Button(this).apply {
            text = "Save Record"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = 18f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply { marginEnd = 16 }
            setOnClickListener { handleSaveRecord() }
        }
        val clearBtn = Button(this).apply {
            text = "Clear All"
            setTextColor(Color.parseColor("#EF4444"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEE2E2"))
                cornerRadius = 18f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
            setOnClickListener {
                dbHelper.deleteAllRecords()
                loadRecords()
                Toast.makeText(this@MainActivity, "All payroll records deleted", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(saveBtn)
        buttonRow.addView(clearBtn)
        root.addView(buttonRow)

        // History Section Header
        val historyHeader = TextView(this).apply {
            text = "Payroll History"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 18)
        }
        root.addView(historyHeader)

        recordsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(recordsContainer)

        // Real-time calculation triggers
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateCalculations() }
            override fun afterTextChanged(s: Editable?) {}
        }
        basicSalaryInput.addTextChangedListener(watcher)
        allowanceInput.addTextChangedListener(watcher)
        otHoursInput.addTextChangedListener(watcher)
        otRateInput.addTextChangedListener(watcher)
        otherDeductionsInput.addTextChangedListener(watcher)

        setContentView(scrollView)
        loadRecords()
    }

    private fun updateCalculations() {
        val basic = basicSalaryInput.text.toString().toDoubleOrNull() ?: 0.0
        val allowance = allowanceInput.text.toString().toDoubleOrNull() ?: 0.0
        val otHours = otHoursInput.text.toString().toDoubleOrNull() ?: 0.0
        val otRate = otRateInput.text.toString().toDoubleOrNull() ?: 0.0
        val otherDeductions = otherDeductionsInput.text.toString().toDoubleOrNull() ?: 0.0

        val otTotal = otHours * otRate
        val grossEarnings = basic + allowance + otTotal

        // EPF base typically includes Basic + Fixed Allowance
        val epfBase = basic + allowance
        val epfEmployee = if (epfCheckbox.isChecked) epfBase * 0.08 else 0.0
        val epfEmployer = if (epfCheckbox.isChecked) epfBase * 0.12 else 0.0
        val etfEmployer = if (epfCheckbox.isChecked) epfBase * 0.03 else 0.0

        val totalDeductions = epfEmployee + otherDeductions
        val netSalary = grossEarnings - totalDeductions

        netPayText.text = formatValue(netSalary, selectedCurrency)
        grossPayText.text = "Gross Earnings: ${formatValue(grossEarnings, selectedCurrency)}"
        epfDeductionText.text = "Employee EPF (8%): ${formatValue(epfEmployee, selectedCurrency)}"
        employerCostText.text = "Employer Liability (EPF 12% + ETF 3%): ${formatValue(epfEmployer + etfEmployer, selectedCurrency)}"
    }

    private fun handleSaveRecord() {
        val period = periodInput.text.toString().trim()
        val basic = basicSalaryInput.text.toString().toDoubleOrNull() ?: 0.0
        val allowance = allowanceInput.text.toString().toDoubleOrNull() ?: 0.0
        val otHours = otHoursInput.text.toString().toDoubleOrNull() ?: 0.0
        val otRate = otRateInput.text.toString().toDoubleOrNull() ?: 0.0
        val otherDeductions = otherDeductionsInput.text.toString().toDoubleOrNull() ?: 0.0

        if (period.isEmpty() || basic <= 0.0) {
            Toast.makeText(this, "Please enter pay period and valid basic salary", Toast.LENGTH_SHORT).show()
            return
        }

        val otTotal = otHours * otRate
        val gross = basic + allowance + otTotal
        val epfEmployee = if (epfCheckbox.isChecked) (basic + allowance) * 0.08 else 0.0
        val net = gross - (epfEmployee + otherDeductions)

        dbHelper.insertRecord(
            period = period,
            currency = selectedCurrency,
            basic = basic,
            allowance = allowance,
            otTotal = otTotal,
            deductions = epfEmployee + otherDeductions,
            net = net
        )

        Toast.makeText(this, "Record saved for $period", Toast.LENGTH_SHORT).show()
        periodInput.text.clear()
        basicSalaryInput.text.clear()
        allowanceInput.text.clear()
        otHoursInput.text.clear()
        otRateInput.text.clear()
        otherDeductionsInput.text.clear()

        updateCalculations()
        loadRecords()
    }

    private fun loadRecords() {
        recordsContainer.removeAllViews()
        val records = dbHelper.getAllRecords()

        if (records.isEmpty()) {
            val emptyNotice = TextView(this).apply {
                text = "No saved payroll records yet."
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(8, 8, 8, 8)
            }
            recordsContainer.addView(emptyNotice)
            return
        }

        for (record in records) {
            val itemCard = createCardLayout().apply {
                val rowHeader = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                val title = TextView(this@MainActivity).apply {
                    text = record.period
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val badge = TextView(this@MainActivity).apply {
                    text = record.currency
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#2563EB"))
                    setBackgroundColor(Color.parseColor("#DBEAFE"))
                    setPadding(16, 6, 16, 6)
                }
                rowHeader.addView(title)
                rowHeader.addView(badge)

                val breakdown = TextView(this@MainActivity).apply {
                    text = "Basic: ${formatValue(record.basic, record.currency)} | Allow: ${formatValue(record.allowance, record.currency)} | OT: ${formatValue(record.otTotal, record.currency)}"
                    textSize = 13f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, 8, 0, 4)
                }

                val deductionLine = TextView(this@MainActivity).apply {
                    text = "Deductions (EPF + Other): ${formatValue(record.deductions, record.currency)}"
                    textSize = 13f
                    setTextColor(Color.parseColor("#EF4444"))
                    setPadding(0, 0, 0, 8)
                }

                val netLine = TextView(this@MainActivity).apply {
                    text = "Net Pay: ${formatValue(record.net, record.currency)}"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#059669"))
                }

                addView(rowHeader)
                addView(breakdown)
                addView(deductionLine)
                addView(netLine)
            }
            recordsContainer.addView(itemCard)
        }
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 0, 0, 16)
        }
    }

    private fun createInputField(hint: String, inputType: Int): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = 14f
            }
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
    }

    private fun createSummaryLine(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, 6)
        }
    }

    private fun formatValue(amount: Double, currency: String): String {
        return if (currency == "LKR") {
            "Rs. " + String.format(Locale.US, "%,.2f", amount)
        } else {
            NumberFormat.getCurrencyInstance(Locale.US).format(amount)
        }
    }
}

data class SalaryRecord(
    val id: Long,
    val period: String,
    val currency: String,
    val basic: Double,
    val allowance: Double,
    val otTotal: Double,
    val deductions: Double,
    val net: Double
)

class SalaryDbHelper(context: Context) : SQLiteOpenHelper(context, "salary_manager.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE salary_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                period TEXT,
                currency TEXT,
                basic REAL,
                allowance REAL,
                otTotal REAL,
                deductions REAL,
                net REAL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS salary_records")
        onCreate(db)
    }

    fun insertRecord(
        period: String,
        currency: String,
        basic: Double,
        allowance: Double,
        otTotal: Double,
        deductions: Double,
        net: Double
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("period", period)
            put("currency", currency)
            put("basic", basic)
            put("allowance", allowance)
            put("otTotal", otTotal)
            put("deductions", deductions)
            put("net", net)
        }
        return db.insert("salary_records", null, values)
    }

    fun getAllRecords(): List<SalaryRecord> {
        val list = mutableListOf<SalaryRecord>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM salary_records ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SalaryRecord(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        period = it.getString(it.getColumnIndexOrThrow("period")),
                        currency = it.getString(it.getColumnIndexOrThrow("currency")),
                        basic = it.getDouble(it.getColumnIndexOrThrow("basic")),
                        allowance = it.getDouble(it.getColumnIndexOrThrow("allowance")),
                        otTotal = it.getDouble(it.getColumnIndexOrThrow("otTotal")),
                        deductions = it.getDouble(it.getColumnIndexOrThrow("deductions")),
                        net = it.getDouble(it.getColumnIndexOrThrow("net"))
                    )
                )
            }
        }
        return list
    }

    fun deleteAllRecords() {
        val db = writableDatabase
        db.delete("salary_records", null, null)
    }
}
