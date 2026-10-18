package com.example.myfirstapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: FinanceDbHelper
    private lateinit var transactionsContainer: LinearLayout

    // Metric Displays
    private lateinit var bankBalanceText: TextView
    private lateinit var netCashflowText: TextView
    private lateinit var savingsRateText: TextView
    private lateinit var runwayText: TextView
    private lateinit var advisorBanner: LinearLayout
    private lateinit var advisorText: TextView

    // Controls
    private lateinit var currencySpinner: Spinner
    private lateinit var bankBalanceInput: EditText
    private lateinit var entryTitleInput: EditText
    private lateinit var entryAmountInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var categorySpinner: Spinner

    private var activeCurrency = "LKR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = FinanceDbHelper(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 44, 36, 60)
        }
        scrollView.addView(root)

        // Top App Header
        val header = TextView(this).apply {
            text = "Wealth & Expense Intelligence"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subheader = TextView(this).apply {
            text = "Track capital, cashflow, and cut unnecessary burn"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 4, 0, 24)
        }
        root.addView(header)
        root.addView(subheader)

        // 1. Currency & Bank Balance Setup Card
        val configCard = createDarkCard("#161F30")
        val configRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val currLabel = createSmallLabel("CURRENCY")
        currencySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("LKR (Rs.)", "USD ($)"))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    activeCurrency = if (pos == 0) "LKR" else "USD"
                    refreshDashboard()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        bankBalanceInput = createInputField("Current Bank Balance", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val updateBalanceBtn = createButton("Set Capital", "#2563EB", "#FFFFFF").apply {
            setOnClickListener {
                val amt = bankBalanceInput.text.toString().toDoubleOrNull()
                if (amt != null) {
                    dbHelper.setBankBalance(amt)
                    bankBalanceInput.text.clear()
                    refreshDashboard()
                    Toast.makeText(this@MainActivity, "Base capital updated", Toast.LENGTH_SHORT).show()
                }
            }
        }

        configCard.addView(currLabel)
        configCard.addView(currencySpinner)
        configCard.addView(bankBalanceInput)
        configCard.addView(updateBalanceBtn)
        root.addView(configCard)

        // 2. Executive Financial Overview Card
        val overviewCard = createDarkCard("#1E293B")
        val balanceLabel = createSmallLabel("TOTAL LIQUID CAPITAL")
        bankBalanceText = TextView(this).apply {
            text = "Rs. 0.00"
            textSize = 34f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 4, 0, 16)
        }

        // Metrics Grid Row (Cashflow, Savings Rate, Runway)
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, 10, 0, 10)
        }

        val col1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val col2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val col3 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }

        col1.addView(createSmallLabel("NET FLOW"))
        netCashflowText = TextView(this).apply { textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) }
        col1.addView(netCashflowText)

        col2.addView(createSmallLabel("SAVINGS %"))
        savingsRateText = TextView(this).apply { textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) }
        col2.addView(savingsRateText)

        col3.addView(createSmallLabel("RUNWAY"))
        runwayText = TextView(this).apply { textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#F59E0B")) }
        col3.addView(runwayText)

        metricsRow.addView(col1)
        metricsRow.addView(col2)
        metricsRow.addView(col3)

        overviewCard.addView(balanceLabel)
        overviewCard.addView(bankBalanceText)
        overviewCard.addView(metricsRow)
        root.addView(overviewCard)

        // 3. Smart Spending Reduction Advisor Banner
        advisorBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2D1515"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#F43F5E"))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
        }
        val advTitle = TextView(this).apply {
            text = "AI Spending Optimization Audit"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
        }
        advisorText = TextView(this).apply {
            text = "Analyzing spend velocity..."
            textSize = 12f
            setTextColor(Color.parseColor("#FEE2E2"))
            setPadding(0, 4, 0, 0)
        }
        advisorBanner.addView(advTitle)
        advisorBanner.addView(advisorText)
        root.addView(advisorBanner)

        // 4. Transaction Entry Card (Inflow / Outflow)
        val entryCard = createDarkCard("#161F30")
        val entryHeader = TextView(this).apply {
            text = "Post Transaction / Monthly Flow"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 14)
        }
        entryTitleInput = createInputField("Description (e.g. Monthly Salary, Dining Out, Rent)", InputType.TYPE_CLASS_TEXT)
        entryAmountInput = createInputField("Amount", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

        // Dropdown: Type (Income vs Expense)
        typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Expense (Outflow)", "Income (Inflow)"))
        }

        // Dropdown: Category (Essential Need vs Discretionary Want)
        categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Essential (Need / Bills / Food)", "Discretionary (Want / Entertainment / Luxury)", "Salary / Primary Income", "Side Income / Investment"))
        }

        val addEntryBtn = createButton("Record Transaction", "#10B981", "#FFFFFF").apply {
            setOnClickListener { handleAddTransaction() }
        }

        entryCard.addView(entryHeader)
        entryCard.addView(entryTitleInput)
        entryCard.addView(entryAmountInput)
        entryCard.addView(createSmallLabel("TRANSACTION TYPE"))
        entryCard.addView(typeSpinner)
        entryCard.addView(createSmallLabel("BUDGET CATEGORY"))
        entryCard.addView(categorySpinner)
        entryCard.addView(addEntryBtn)
        root.addView(entryCard)

        // 5. Ledger / History Section
        val historyTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 16)
        }
        val histText = TextView(this).apply {
            text = "Recent Transactions"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val clearAllBtn = TextView(this).apply {
            text = "Reset Ledger"
            textSize = 12f
            setTextColor(Color.parseColor("#EF4444"))
            setOnClickListener {
                dbHelper.clearAllTransactions()
                refreshDashboard()
                Toast.makeText(this@MainActivity, "Ledger reset", Toast.LENGTH_SHORT).show()
            }
        }
        historyTitleRow.addView(histText)
        historyTitleRow.addView(clearAllBtn)
        root.addView(historyTitleRow)

        transactionsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(transactionsContainer)

        setContentView(scrollView)
        refreshDashboard()
    }

    private fun handleAddTransaction() {
        val title = entryTitleInput.text.toString().trim()
        val amount = entryAmountInput.text.toString().toDoubleOrNull() ?: 0.0
        val isIncome = typeSpinner.selectedItemPosition == 1
        val categoryStr = categorySpinner.selectedItem.toString()

        if (title.isEmpty() || amount <= 0.0) {
            Toast.makeText(this, "Enter valid description and positive amount", Toast.LENGTH_SHORT).show()
            return
        }

        val categoryType = if (isIncome) {
            "INCOME"
        } else if (categorySpinner.selectedItemPosition == 0) {
            "NEED"
        } else {
            "WANT"
        }

        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())
        dbHelper.insertTransaction(title, amount, if (isIncome) 1 else 0, categoryType, dateStr)

        entryTitleInput.text.clear()
        entryAmountInput.text.clear()
        refreshDashboard()
        Toast.makeText(this, "Transaction logged", Toast.LENGTH_SHORT).show()
    }

    private fun refreshDashboard() {
        val startingBalance = dbHelper.getBankBalance()
        val transactions = dbHelper.getAllTransactions()

        var totalIncome = 0.0
        var totalExpenses = 0.0
        var needsTotal = 0.0
        var wantsTotal = 0.0

        for (t in transactions) {
            if (t.isIncome) {
                totalIncome += t.amount
            } else {
                totalExpenses += t.amount
                if (t.category == "NEED") needsTotal += t.amount else wantsTotal += t.amount
            }
        }

        val currentLiquidBalance = startingBalance + totalIncome - totalExpenses
        val netCashflow = totalIncome - totalExpenses

        // 1. Update Core Displays
        bankBalanceText.text = formatCurrency(currentLiquidBalance, activeCurrency)
        
        netCashflowText.text = (if (netCashflow >= 0) "+ " else "- ") + formatCurrency(Math.abs(netCashflow), activeCurrency)
        netCashflowText.setTextColor(if (netCashflow >= 0) Color.parseColor("#10B981") else Color.parseColor("#F43F5E"))

        val savingsRate = if (totalIncome > 0) ((totalIncome - totalExpenses) / totalIncome) * 100 else 0.0
        savingsRateText.text = String.format(Locale.US, "%.1f%%", Math.max(0.0, savingsRate))

        val monthlyBurn = if (totalExpenses > 0) totalExpenses else 1.0
        val runwayMonths = if (currentLiquidBalance > 0) currentLiquidBalance / monthlyBurn else 0.0
        runwayText.text = if (runwayMonths > 0) String.format(Locale.US, "%.1f Mo", runwayMonths) else "0 Mo"

        // 2. Spending Reduction & Optimization Logic
        renderSmartAdvice(totalIncome, totalExpenses, needsTotal, wantsTotal)

        // 3. Render Transaction List
        renderTransactionList(transactions)
    }

    private fun renderSmartAdvice(income: Double, expenses: Double, needs: Double, wants: Double) {
        val wantsPercentage = if (income > 0) (wants / income) * 100 else 0.0
        val needsPercentage = if (income > 0) (needs / income) * 100 else 0.0

        if (income == 0.0 && expenses > 0.0) {
            advisorBanner.visibility = View.VISIBLE
            advisorText.text = "No recorded inflow. All outflows are draining liquid reserves."
            return
        }

        if (wantsPercentage > 30.0) {
            advisorBanner.visibility = View.VISIBLE
            val excessiveAmount = wants - (income * 0.30)
            advisorText.text = "⚠️ High Discretionary Burn: You are spending ${String.format(Locale.US, "%.1f", wantsPercentage)}% on non-essentials (Target: < 30%). Trimming ${formatCurrency(excessiveAmount, activeCurrency)} will restore your recommended 20% savings buffer."
        } else if (expenses > income) {
            advisorBanner.visibility = View.VISIBLE
            advisorText.text = "⚠️ Deficit alert: Outflow exceeds inflow by ${formatCurrency(expenses - income, activeCurrency)}. Reduce non-essential expenses immediately."
        } else {
            advisorBanner.visibility = View.VISIBLE
            advisorBanner.background = GradientDrawable().apply {
                setColor(Color.parseColor("#13281E"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#10B981"))
            }
            advisorText.setTextColor(Color.parseColor("#A7F3D0"))
            advisorText.text = "✓ Optimal Budget: Needs (${String.format(Locale.US, "%.0f", needsPercentage)}%) and Wants (${String.format(Locale.US, "%.0f", wantsPercentage)}%) are within healthy 50/30 parameters."
        }
    }

    private fun renderTransactionList(transactions: List<TransactionRecord>) {
        transactionsContainer.removeAllViews()

        if (transactions.isEmpty()) {
            val emptyNotice = TextView(this).apply {
                text = "No transactions logged. Add income or expenses above."
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(8, 12, 8, 12)
            }
            transactionsContainer.addView(emptyNotice)
            return
        }

        for (t in transactions) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 24, 20)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161F30"))
                    cornerRadius = 14f
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            }

            val leftCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val title = TextView(this).apply {
                text = t.title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val badgeText = if (t.isIncome) "INFLOW" else if (t.category == "NEED") "ESSENTIAL" else "DISCRETIONARY (WANT)"
            val meta = TextView(this).apply {
                text = "${t.date} • $badgeText"
                textSize = 11f
                setTextColor(if (t.category == "WANT") Color.parseColor("#F59E0B") else Color.parseColor("#64748B"))
                setPadding(0, 4, 0, 0)
            }
            leftCol.addView(title)
            leftCol.addView(meta)

            val amountView = TextView(this).apply {
                text = (if (t.isIncome) "+ " else "- ") + formatCurrency(t.amount, activeCurrency)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (t.isIncome) Color.parseColor("#10B981") else Color.parseColor("#F43F5E"))
                gravity = Gravity.CENTER_VERTICAL
            }

            item.addView(leftCol)
            item.addView(amountView)
            transactionsContainer.addView(item)
        }
    }

    private fun createDarkCard(bgColorHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColorHex))
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 }
        }
    }

    private fun createSmallLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 4)
        }
    }

    private fun createInputField(hint: String, inputType: Int): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 12f
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
        }
    }

    private fun createButton(label: String, bgHex: String, textHex: String): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 14f
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6; bottomMargin = 6 }
        }
    }

    private fun formatCurrency(amt: Double, currency: String): String {
        return if (currency == "LKR") {
            "Rs. " + String.format(Locale.US, "%,.2f", amt)
        } else {
            NumberFormat.getCurrencyInstance(Locale.US).format(amt)
        }
    }
}

// Data Models
data class TransactionRecord(
    val id: Long,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: String,
    val date: String
)

// Embedded SQLite Engine
class FinanceDbHelper(context: Context) : SQLiteOpenHelper(context, "finance_intel.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profile (
                id INTEGER PRIMARY KEY,
                bank_balance REAL
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO profile (id, bank_balance) VALUES (1, 0.0)")

        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                amount REAL,
                is_income INTEGER,
                category TEXT,
                date TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS profile")
        db.execSQL("DROP TABLE IF EXISTS transactions")
        onCreate(db)
    }

    fun getBankBalance(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT bank_balance FROM profile WHERE id = 1", null)
        var balance = 0.0
        cursor.use {
            if (it.moveToFirst()) balance = it.getDouble(0)
        }
        return balance
    }

    fun setBankBalance(amount: Double) {
        val db = writableDatabase
        val values = ContentValues().apply { put("bank_balance", amount) }
        db.update("profile", values, "id = 1", null)
    }

    fun insertTransaction(title: String, amount: Double, isIncome: Int, category: String, date: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("amount", amount)
            put("is_income", isIncome)
            put("category", category)
            put("date", date)
        }
        return db.insert("transactions", null, values)
    }

    fun getAllTransactions(): List<TransactionRecord> {
        val list = mutableListOf<TransactionRecord>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM transactions ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    TransactionRecord(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        isIncome = it.getInt(it.getColumnIndexOrThrow("is_income")) == 1,
                        category = it.getString(it.getColumnIndexOrThrow("category")),
                        date = it.getString(it.getColumnIndexOrThrow("date"))
                    )
                )
            }
        }
        return list
    }

    fun clearAllTransactions() {
        val db = writableDatabase
        db.delete("transactions", null, null)
    }
}
