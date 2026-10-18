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

    // Metric Views
    private lateinit var bankBalanceText: TextView
    private lateinit var dailySafeSpendText: TextView
    private lateinit var runwayText: TextView
    private lateinit var daysRemainingText: TextView
    private lateinit var advisorBanner: LinearLayout
    private lateinit var advisorTitle: TextView
    private lateinit var advisorText: TextView

    // Containers
    private lateinit var billsContainer: LinearLayout
    private lateinit var ledgerContainer: LinearLayout

    // Form inputs
    private lateinit var currencySpinner: Spinner
    private lateinit var bankInput: EditText
    private lateinit var paydayAnchorInput: EditText
    private lateinit var transTitleInput: EditText
    private lateinit var transAmountInput: EditText
    private lateinit var transTypeSpinner: Spinner
    private lateinit var transCategorySpinner: Spinner

    private var activeCurrency = "LKR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = FinanceDbHelper(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#090D16"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 44, 32, 60)
        }
        scrollView.addView(root)

        // 1. App Header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        val headerTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val header = TextView(this).apply {
            text = "Capital & Cashflow"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subheader = TextView(this).apply {
            text = "Payday-Anchored Financial Dashboard"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        }
        headerTitleCol.addView(header)
        headerTitleCol.addView(subheader)

        currencySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("LKR (Rs.)", "USD ($)"))
            setSelection(if (dbHelper.getProfile().currency == "USD") 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    activeCurrency = if (pos == 0) "LKR" else "USD"
                    dbHelper.updateCurrency(activeCurrency)
                    refreshAll()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
        headerRow.addView(headerTitleCol)
        headerRow.addView(currencySpinner)
        root.addView(headerRow)

        // 2. The Command Center: Liquid Capital & Safe-to-Spend
        val heroCard = createCard("#111827", "#1E293B")

        val capLabel = createLabel("TOTAL LIQUID CAPITAL (BANK & CASH)")
        bankBalanceText = TextView(this).apply {
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 16)
        }

        val heroDivider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply { bottomMargin = 16 }
        }

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        val safeSpendCol = createMetricCol("DAILY SAFE-SPEND", "#10B981")
        dailySafeSpendText = safeSpendCol.second
        val daysCol = createMetricCol("DAYS TO PAYDAY", "#38BDF8")
        daysRemainingText = daysCol.second
        val runwayCol = createMetricCol("EMERGENCY RUNWAY", "#F59E0B")
        runwayText = runwayCol.second

        metricsRow.addView(safeSpendCol.first)
        metricsRow.addView(daysCol.first)
        metricsRow.addView(runwayCol.first)

        heroCard.addView(capLabel)
        heroCard.addView(bankBalanceText)
        heroCard.addView(heroDivider)
        heroCard.addView(metricsRow)
        root.addView(heroCard)

        // 3. Intelligent Waste & Leak Advisor Banner
        advisorBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
        }
        advisorTitle = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }
        advisorText = TextView(this).apply {
            textSize = 12f
            setPadding(0, 4, 0, 0)
        }
        advisorBanner.addView(advisorTitle)
        advisorBanner.addView(advisorText)
        root.addView(advisorBanner)

        // 4. Fixed Commitments & Recurring Bills Checklist
        val billsHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        val billsTitle = TextView(this).apply {
            text = "Committed Recurring Bills"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val resetBillsBtn = TextView(this).apply {
            text = "Reset Cycle Bills"
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
            setOnClickListener {
                dbHelper.resetMonthlyBills()
                refreshAll()
                Toast.makeText(this@MainActivity, "Cycle bills refreshed", Toast.LENGTH_SHORT).show()
            }
        }
        billsHeaderRow.addView(billsTitle)
        billsHeaderRow.addView(resetBillsBtn)
        root.addView(billsHeaderRow)

        billsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
        }
        root.addView(billsContainer)

        // 5. Quick Transaction Logger (Income vs Needs vs Wants)
        val actionCard = createCard("#111827", "#1E293B")
        val actionTitle = TextView(this).apply {
            text = "Log Transaction / Salary Inflow"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }

        transTitleInput = createInput("Description (e.g. Salary, Supermarket, Dining, Fuel)", InputType.TYPE_CLASS_TEXT)
        transAmountInput = createInput("Amount", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 12)
        }
        transTypeSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 }
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Expense (Outflow)", "Income (Inflow)"))
        }
        transCategorySpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Essential (Need: Food/Bills)", "Discretionary (Want: Dining/Leisure)", "Salary Inflow", "Side Hustle / Other"))
        }
        spinnerRow.addView(transTypeSpinner)
        spinnerRow.addView(transCategorySpinner)

        val postBtn = createButton("Record to Ledger", "#2563EB", "#FFFFFF").apply {
            setOnClickListener { handleAddTransaction() }
        }

        actionCard.addView(actionTitle)
        actionCard.addView(transTitleInput)
        actionCard.addView(transAmountInput)
        actionCard.addView(spinnerRow)
        actionCard.addView(postBtn)
        root.addView(actionCard)

        // 6. Anchor Configuration & Base Capital Settings Card
        val settingsCard = createCard("#0E1626", "#172554")
        val settingsTitle = TextView(this).apply {
            text = "Account Baseline Settings"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#93C5FD"))
            setPadding(0, 0, 0, 12)
        }
        bankInput = createInput("Adjust Base Liquid Capital", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        paydayAnchorInput = createInput("Payday Anchor Day of Month (e.g. 25)", InputType.TYPE_CLASS_NUMBER)

        val saveSettingsBtn = createButton("Save Baseline Settings", "#1D4ED8", "#FFFFFF").apply {
            setOnClickListener {
                val newCap = bankInput.text.toString().toDoubleOrNull()
                val newDay = paydayAnchorInput.text.toString().toIntOrNull()
                if (newCap != null || newDay != null) {
                    val current = dbHelper.getProfile()
                    dbHelper.updateProfile(
                        newCap ?: current.liquidBalance,
                        newDay ?: current.paydayAnchor,
                        activeCurrency
                    )
                    bankInput.text.clear()
                    paydayAnchorInput.text.clear()
                    refreshAll()
                    Toast.makeText(this@MainActivity, "Base settings updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
        settingsCard.addView(settingsTitle)
        settingsCard.addView(bankInput)
        settingsCard.addView(paydayAnchorInput)
        settingsCard.addView(saveSettingsBtn)
        root.addView(settingsCard)

        // 7. Recent Ledger Section
        val ledgerTitle = TextView(this).apply {
            text = "Recent Transactions"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        }
        root.addView(ledgerTitle)

        ledgerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(ledgerContainer)

        setContentView(scrollView)
        refreshAll()
    }

    private fun handleAddTransaction() {
        val title = transTitleInput.text.toString().trim()
        val amount = transAmountInput.text.toString().toDoubleOrNull() ?: 0.0
        val isIncome = transTypeSpinner.selectedItemPosition == 1
        val catPos = transCategorySpinner.selectedItemPosition

        if (title.isEmpty() || amount <= 0.0) {
            Toast.makeText(this, "Enter a valid description and positive amount", Toast.LENGTH_SHORT).show()
            return
        }

        val category = when {
            isIncome -> "INCOME"
            catPos == 0 -> "NEED"
            else -> "WANT"
        }

        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())
        dbHelper.insertTransaction(title, amount, if (isIncome) 1 else 0, category, dateStr)

        // Update liquid capital
        val profile = dbHelper.getProfile()
        val updatedCapital = if (isIncome) profile.liquidBalance + amount else profile.liquidBalance - amount
        dbHelper.updateProfile(updatedCapital, profile.paydayAnchor, profile.currency)

        transTitleInput.text.clear()
        transAmountInput.text.clear()
        refreshAll()
        Toast.makeText(this, "Logged successfully", Toast.LENGTH_SHORT).show()
    }

    private fun refreshAll() {
        val profile = dbHelper.getProfile()
        val transactions = dbHelper.getAllTransactions()
        val bills = dbHelper.getAllBills()

        // 1. Payday Anchor & Days Remaining
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val anchor = profile.paydayAnchor

        val daysRemaining = if (currentDay <= anchor) {
            anchor - currentDay
        } else {
            (maxDaysInMonth - currentDay) + anchor
        }.coerceAtLeast(1)

        daysRemainingText.text = "$daysRemaining Days"

        // 2. Liquid Capital & Reserved Obligations
        val unspentCapital = profile.liquidBalance
        bankBalanceText.text = formatCurrency(unspentCapital, activeCurrency)

        val pendingBillsSum = bills.filter { !it.isPaid }.sumOf { it.estimatedAmount }

        // Free spending pool = Capital minus pending fixed commitments
        val freeSpendPool = (unspentCapital - pendingBillsSum).coerceAtLeast(0.0)
        val dailySafe = freeSpendPool / daysRemaining
        dailySafeSpendText.text = formatCurrency(dailySafe, activeCurrency)

        // 3. Runway Calculation
        val totalBurn = bills.sumOf { it.estimatedAmount } + transactions.filter { !it.isIncome }.sumOf { it.amount }
        val avgBurn = if (totalBurn > 0.0) totalBurn else 1.0
        val runwayMonths = if (unspentCapital > 0.0) unspentCapital / avgBurn else 0.0
        runwayText.text = String.format(Locale.US, "%.1f Mo", runwayMonths)

        // 4. Waste-Cutting Advisor Audit
        val totalIncome = transactions.filter { it.isIncome }.sumOf { it.amount }
        val wantsTotal = transactions.filter { it.category == "WANT" }.sumOf { it.amount }
        val wantsPercentage = if (totalIncome > 0.0) (wantsTotal / totalIncome) * 100 else 0.0

        if (wantsPercentage > 30.0) {
            advisorBanner.background = GradientDrawable().apply {
                setColor(Color.parseColor("#261113"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#E11D48"))
            }
            advisorTitle.setTextColor(Color.parseColor("#FDA4AF"))
            advisorTitle.text = "⚠️ HIGH DISCRETIONARY BURN DETECTED"
            advisorText.setTextColor(Color.parseColor("#FFE4E6"))
            val excess = wantsTotal - (totalIncome * 0.30)
            advisorText.text = "Non-essential 'Wants' account for ${String.format(Locale.US, "%.1f", wantsPercentage)}% of income (Target: < 30%). Cutting ${formatCurrency(excess, activeCurrency)} will restore your healthy savings buffer."
        } else {
            advisorBanner.background = GradientDrawable().apply {
                setColor(Color.parseColor("#0C2018"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#059669"))
            }
            advisorTitle.setTextColor(Color.parseColor("#6EE7B7"))
            advisorTitle.text = "✓ LEAN BUDGET: BALANCED DISCRETIONARY SPENDING"
            advisorText.setTextColor(Color.parseColor("#D1FAE5"))
            advisorText.text = "Your non-essential spending is under control (${String.format(Locale.US, "%.1f", wantsPercentage)}% of total inflow). Remaining capital is preserved for savings."
        }

        // 5. Render Recurring Bills
        renderBills(bills)

        // 6. Render Ledger
        renderLedger(transactions)
    }

    private fun renderBills(bills: List<BillRecord>) {
        billsContainer.removeAllViews()
        for (bill in bills) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 18, 24, 18)
                background = GradientDrawable().apply {
                    setColor(if (bill.isPaid) Color.parseColor("#0F172A") else Color.parseColor("#1E293B"))
                    cornerRadius = 14f
                    if (!bill.isPaid) setStroke(1, Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val title = TextView(this).apply {
                text = bill.title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (bill.isPaid) Color.parseColor("#64748B") else Color.WHITE)
            }
            val status = TextView(this).apply {
                text = if (bill.isPaid) "Paid • Deducted from capital" else "Reserved • Due day ${bill.dueDay}"
                textSize = 11f
                setTextColor(if (bill.isPaid) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            }
            textCol.addView(title)
            textCol.addView(status)

            val amtText = TextView(this).apply {
                text = formatCurrency(bill.estimatedAmount, activeCurrency)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (bill.isPaid) Color.parseColor("#64748B") else Color.WHITE)
                setPadding(0, 0, 16, 0)
                gravity = Gravity.CENTER_VERTICAL
            }

            val actionBtn = Button(this).apply {
                text = if (bill.isPaid) "Done" else "Pay"
                textSize = 12f
                isEnabled = !bill.isPaid
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (bill.isPaid) Color.parseColor("#1E293B") else Color.parseColor("#2563EB"))
                    cornerRadius = 10f
                }
                setOnClickListener {
                    dbHelper.markBillPaid(bill.id)
                    // Deduct from capital
                    val p = dbHelper.getProfile()
                    dbHelper.updateProfile(p.liquidBalance - bill.estimatedAmount, p.paydayAnchor, p.currency)
                    refreshAll()
                }
            }

            card.addView(textCol)
            card.addView(amtText)
            card.addView(actionBtn)
            billsContainer.addView(card)
        }
    }

    private fun renderLedger(transactions: List<TransactionRecord>) {
        ledgerContainer.removeAllViews()
        if (transactions.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No recorded transactions."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(8, 8, 8, 8)
            }
            ledgerContainer.addView(empty)
            return
        }

        for (t in transactions.take(15)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 16, 20, 16)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#111827"))
                    cornerRadius = 12f
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
            }

            val detailsCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val title = TextView(this).apply {
                text = t.title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val badge = TextView(this).apply {
                text = "${t.date} • ${if (t.isIncome) "INFLOW" else t.category}"
                textSize = 11f
                setTextColor(when (t.category) {
                    "WANT" -> Color.parseColor("#F59E0B")
                    "NEED" -> Color.parseColor("#38BDF8")
                    else -> Color.parseColor("#10B981")
                })
            }
            detailsCol.addView(title)
            detailsCol.addView(badge)

            val amount = TextView(this).apply {
                text = (if (t.isIncome) "+ " else "- ") + formatCurrency(t.amount, activeCurrency)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (t.isIncome) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(detailsCol)
            row.addView(amount)
            ledgerContainer.addView(row)
        }
    }

    // UI Builders
    private fun createCard(bgHex: String, borderHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 26, 28, 26)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 18f
                setStroke(1, Color.parseColor(borderHex))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 }
        }
    }

    private fun createMetricCol(label: String, valColorHex: String): Pair<LinearLayout, TextView> {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val lbl = createLabel(label)
        val value = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(valColorHex))
            setPadding(0, 2, 0, 0)
        }
        col.addView(lbl)
        col.addView(value)
        return Pair(col, value)
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            letterSpacing = 0.05f
        }
    }

    private fun createInput(hint: String, inputType: Int): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = 12f
            }
            setPadding(22, 18, 22, 18)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
        }
    }

    private fun createButton(label: String, bgHex: String, textHex: String): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor(textHex))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 }
        }
    }

    private fun formatCurrency(amount: Double, currency: String): String {
        return if (currency == "LKR") {
            "Rs. " + String.format(Locale.US, "%,.2f", amount)
        } else {
            NumberFormat.getCurrencyInstance(Locale.US).format(amount)
        }
    }
}

// Data Entities
data class UserProfile(val liquidBalance: Double, val paydayAnchor: Int, val currency: String)
data class BillRecord(val id: Long, val title: String, val estimatedAmount: Double, val dueDay: Int, val isPaid: Boolean)
data class TransactionRecord(val id: Long, val title: String, val amount: Double, val isIncome: Boolean, val category: String, val date: String)

// SQLite Engine
class FinanceDbHelper(context: Context) : SQLiteOpenHelper(context, "finance_v3.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profile (id INTEGER PRIMARY KEY, liquid_balance REAL, payday_anchor INTEGER, currency TEXT)")
        db.execSQL("INSERT INTO profile VALUES (1, 150000.0, 25, 'LKR')")

        db.execSQL("CREATE TABLE recurring_bills (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, estimated_amount REAL, due_day INTEGER, is_paid INTEGER)")
        db.execSQL("INSERT INTO recurring_bills (title, estimated_amount, due_day, is_paid) VALUES ('Residential Rent', 45000.0, 5, 0)")
        db.execSQL("INSERT INTO recurring_bills (title, estimated_amount, due_day, is_paid) VALUES ('Electricity (CEB)', 7500.0, 12, 0)")
        db.execSQL("INSERT INTO recurring_bills (title, estimated_amount, due_day, is_paid) VALUES ('Dialog Broadband & Mobile', 4200.0, 18, 0)")

        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, amount REAL, is_income INTEGER, category TEXT, date TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS profile")
        db.execSQL("DROP TABLE IF EXISTS recurring_bills")
        db.execSQL("DROP TABLE IF EXISTS transactions")
        onCreate(db)
    }

    fun getProfile(): UserProfile {
        val db = readableDatabase
        val c = db.rawQuery("SELECT liquid_balance, payday_anchor, currency FROM profile WHERE id = 1", null)
        var p = UserProfile(0.0, 25, "LKR")
        c.use {
            if (it.moveToFirst()) {
                p = UserProfile(it.getDouble(0), it.getInt(1), it.getString(2) ?: "LKR")
            }
        }
        return p
    }

    fun updateProfile(balance: Double, anchor: Int, currency: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("liquid_balance", balance)
            put("payday_anchor", anchor)
            put("currency", currency)
        }
        db.update("profile", cv, "id = 1", null)
    }

    fun updateCurrency(currency: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("currency", currency) }
        db.update("profile", cv, "id = 1", null)
    }

    fun getAllBills(): List<BillRecord> {
        val list = mutableListOf<BillRecord>()
        val c = readableDatabase.rawQuery("SELECT * FROM recurring_bills", null)
        c.use {
            while (it.moveToNext()) {
                list.add(BillRecord(
                    it.getLong(it.getColumnIndexOrThrow("id")),
                    it.getString(it.getColumnIndexOrThrow("title")),
                    it.getDouble(it.getColumnIndexOrThrow("estimated_amount")),
                    it.getInt(it.getColumnIndexOrThrow("due_day")),
                    it.getInt(it.getColumnIndexOrThrow("is_paid")) == 1
                ))
            }
        }
        return list
    }

    fun markBillPaid(id: Long) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("is_paid", 1) }
        db.update("recurring_bills", cv, "id = ?", arrayOf(id.toString()))
    }

    fun resetMonthlyBills() {
        val db = writableDatabase
        val cv = ContentValues().apply { put("is_paid", 0) }
        db.update("recurring_bills", cv, null, null)
    }

    fun insertTransaction(title: String, amount: Double, isIncome: Int, category: String, date: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", title)
            put("amount", amount)
            put("is_income", isIncome)
            put("category", category)
            put("date", date)
        }
        db.insert("transactions", null, cv)
    }

    fun getAllTransactions(): List<TransactionRecord> {
        val list = mutableListOf<TransactionRecord>()
        val c = readableDatabase.rawQuery("SELECT * FROM transactions ORDER BY id DESC", null)
        c.use {
            while (it.moveToNext()) {
                list.add(TransactionRecord(
                    it.getLong(it.getColumnIndexOrThrow("id")),
                    it.getString(it.getColumnIndexOrThrow("title")),
                    it.getDouble(it.getColumnIndexOrThrow("amount")),
                    it.getInt(it.getColumnIndexOrThrow("is_income")) == 1,
                    it.getString(it.getColumnIndexOrThrow("category")),
                    it.getString(it.getColumnIndexOrThrow("date"))
                ))
            }
        }
        return list
    }
}
