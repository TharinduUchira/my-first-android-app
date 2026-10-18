package com.example.myfirstapp

import android.app.AlertDialog
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ============================================================================
// 1. DOMAIN LAYER: STRICT MONEY ARITHMETIC & VALUE OBJECTS
// ============================================================================

data class Money(
    val amountMinor: Long,
    val currency: String
) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return Money(amountMinor + other.amountMinor, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return Money(amountMinor - other.amountMinor, currency)
    }

    fun isPositive(): Boolean = amountMinor > 0L

    fun format(): String {
        val majorUnits = BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY)
        return if (currency == "LKR") {
            "Rs. " + NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(majorUnits)
        } else {
            NumberFormat.getCurrencyInstance(Locale.US).apply {
                this.currency = java.util.Currency.getInstance("USD")
            }.format(majorUnits)
        }
    }

    fun toDecimalString(): String {
        return BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY).toPlainString()
    }

    companion object {
        fun fromMajorString(input: String, currency: String): Money {
            val clean = input.trim().replace(",", "")
            if (clean.isEmpty()) return Money(0L, currency)
            val parsed = BigDecimal(clean)
            val minor = parsed.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toLong()
            return Money(minor, currency)
        }
    }
}

data class Account(
    val id: Long,
    val name: String,
    val type: String,
    val currency: String,
    val openingBalanceMinor: Long,
    val currentBalance: Money
)

data class TransactionItem(
    val id: Long,
    val type: String,
    val amount: Money,
    val accountId: Long,
    val accountName: String,
    val destinationAccountName: String?,
    val category: String,
    val payeeOrNote: String,
    val dateString: String
)

data class RecurringBill(
    val id: Long,
    val title: String,
    val amount: Money,
    val dueDay: Int,
    val isPaid: Boolean
)

// ============================================================================
// 2. DATA LAYER: MULTI-ACCOUNT SQLITE REPOSITORY (FULL CRUD)
// ============================================================================

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "money_master_v2.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // Accounts table
        db.execSQL(
            """
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                currency TEXT NOT NULL,
                opening_balance_minor INTEGER NOT NULL,
                is_archived INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        // Seed default accounts
        db.execSQL("INSERT INTO accounts (name, type, currency, opening_balance_minor) VALUES ('Primary Bank Account', 'BANK', 'LKR', 12500000)")
        db.execSQL("INSERT INTO accounts (name, type, currency, opening_balance_minor) VALUES ('Cash Wallet', 'CASH', 'LKR', 1500000)")
        db.execSQL("INSERT INTO accounts (name, type, currency, opening_balance_minor) VALUES ('Emergency Vault', 'SAVINGS', 'LKR', 25000000)")

        // Profile configuration
        db.execSQL(
            """
            CREATE TABLE user_profile (
                id INTEGER PRIMARY KEY,
                payday_anchor_day INTEGER NOT NULL,
                base_currency TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO user_profile (id, payday_anchor_day, base_currency) VALUES (1, 25, 'LKR')")

        // Recurring bills table
        db.execSQL(
            """
            CREATE TABLE recurring_bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL,
                due_day INTEGER NOT NULL,
                is_paid INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO recurring_bills (title, amount_minor, currency, due_day, is_paid) VALUES ('Apartment Rent', 4500000, 'LKR', 5, 0)")
        db.execSQL("INSERT INTO recurring_bills (title, amount_minor, currency, due_day, is_paid) VALUES ('Electricity (CEB)', 850000, 'LKR', 12, 0)")
        db.execSQL("INSERT INTO recurring_bills (title, amount_minor, currency, due_day, is_paid) VALUES ('Dialog Broadband', 420000, 'LKR', 18, 0)")

        // Transactions ledger
        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL,
                account_id INTEGER NOT NULL,
                destination_account_id INTEGER,
                category TEXT NOT NULL,
                note TEXT NOT NULL,
                date_str TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS transactions")
        db.execSQL("DROP TABLE IF EXISTS recurring_bills")
        db.execSQL("DROP TABLE IF EXISTS user_profile")
        db.execSQL("DROP TABLE IF EXISTS accounts")
        onCreate(db)
    }

    // --- Profile Settings ---
    fun getPaydayAnchor(): Int {
        val cursor = readableDatabase.rawQuery("SELECT payday_anchor_day FROM user_profile WHERE id = 1", null)
        var anchor = 25
        cursor.use { if (it.moveToFirst()) anchor = it.getInt(0) }
        return anchor
    }

    fun updatePaydayAnchor(newDay: Int) {
        val cv = ContentValues().apply { put("payday_anchor_day", newDay) }
        writableDatabase.update("user_profile", cv, "id = 1", null)
    }

    fun getBaseCurrency(): String {
        val cursor = readableDatabase.rawQuery("SELECT base_currency FROM user_profile WHERE id = 1", null)
        var curr = "LKR"
        cursor.use { if (it.moveToFirst()) curr = it.getString(0) ?: "LKR" }
        return curr
    }

    fun updateBaseCurrency(newCurr: String) {
        val cv = ContentValues().apply { put("base_currency", newCurr) }
        writableDatabase.update("user_profile", cv, "id = 1", null)
    }

    // --- Accounts CRUD ---
    fun getAccounts(): List<Account> {
        val list = mutableListOf<Account>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, name, type, currency, opening_balance_minor FROM accounts WHERE is_archived = 0", null)
        cursor.use {
            while (it.moveToNext()) {
                val accId = it.getLong(0)
                val name = it.getString(1)
                val type = it.getString(2)
                val currency = it.getString(3)
                val opening = it.getLong(4)

                val inCursor = db.rawQuery(
                    "SELECT SUM(amount_minor) FROM transactions WHERE (account_id = ? AND type = 'INCOME') OR (destination_account_id = ? AND type = 'TRANSFER')",
                    arrayOf(accId.toString(), accId.toString())
                )
                var credits = 0L
                inCursor.use { c -> if (c.moveToFirst()) credits = c.getLong(0) }

                val outCursor = db.rawQuery(
                    "SELECT SUM(amount_minor) FROM transactions WHERE account_id = ? AND (type = 'EXPENSE' OR type = 'TRANSFER')",
                    arrayOf(accId.toString())
                )
                var debits = 0L
                outCursor.use { c -> if (c.moveToFirst()) debits = c.getLong(0) }

                val currentBalance = Money(opening + credits - debits, currency)
                list.add(Account(accId, name, type, currency, opening, currentBalance))
            }
        }
        return list
    }

    fun insertAccount(name: String, type: String, currency: String, openingBalanceMinor: Long): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("type", type)
            put("currency", currency)
            put("opening_balance_minor", openingBalanceMinor)
        }
        return writableDatabase.insert("accounts", null, cv)
    }

    fun updateAccount(id: Long, name: String, type: String, openingBalanceMinor: Long) {
        val cv = ContentValues().apply {
            put("name", name)
            put("type", type)
            put("opening_balance_minor", openingBalanceMinor)
        }
        writableDatabase.update("accounts", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteAccount(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("transactions", "account_id = ? OR destination_account_id = ?", arrayOf(id.toString(), id.toString()))
            db.delete("accounts", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- Recurring Bills CRUD ---
    fun getRecurringBills(): List<RecurringBill> {
        val list = mutableListOf<RecurringBill>()
        val cursor = readableDatabase.rawQuery("SELECT id, title, amount_minor, currency, due_day, is_paid FROM recurring_bills ORDER BY due_day ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    RecurringBill(
                        it.getLong(0),
                        it.getString(1),
                        Money(it.getLong(2), it.getString(3)),
                        it.getInt(4),
                        it.getInt(5) == 1
                    )
                )
            }
        }
        return list
    }

    fun insertBill(title: String, amountMinor: Long, currency: String, dueDay: Int): Long {
        val cv = ContentValues().apply {
            put("title", title)
            put("amount_minor", amountMinor)
            put("currency", currency)
            put("due_day", dueDay)
            put("is_paid", 0)
        }
        return writableDatabase.insert("recurring_bills", null, cv)
    }

    fun updateBill(id: Long, title: String, amountMinor: Long, dueDay: Int) {
        val cv = ContentValues().apply {
            put("title", title)
            put("amount_minor", amountMinor)
            put("due_day", dueDay)
        }
        writableDatabase.update("recurring_bills", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteBill(id: Long) {
        writableDatabase.delete("recurring_bills", "id = ?", arrayOf(id.toString()))
    }

    fun markBillPaid(billId: Long, accountId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val c = db.rawQuery("SELECT title, amount_minor, currency FROM recurring_bills WHERE id = ?", arrayOf(billId.toString()))
            if (c.moveToFirst()) {
                val title = c.getString(0)
                val amountMinor = c.getLong(1)
                val currency = c.getString(2)

                val cv = ContentValues().apply { put("is_paid", 1) }
                db.update("recurring_bills", cv, "id = ?", arrayOf(billId.toString()))

                val transCv = ContentValues().apply {
                    put("type", "EXPENSE")
                    put("amount_minor", amountMinor)
                    put("currency", currency)
                    put("account_id", accountId)
                    put("category", "NEED")
                    put("note", "Bill: $title")
                    put("date_str", SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date()))
                    put("created_at", System.currentTimeMillis())
                }
                db.insert("transactions", null, transCv)
            }
            c.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun resetMonthlyBills() {
        val cv = ContentValues().apply { put("is_paid", 0) }
        writableDatabase.update("recurring_bills", cv, null, null)
    }

    // --- Transactions CRUD ---
    fun addTransaction(type: String, amount: Money, accountId: Long, destAccountId: Long?, category: String, note: String) {
        val cv = ContentValues().apply {
            put("type", type)
            put("amount_minor", amount.amountMinor)
            put("currency", amount.currency)
            put("account_id", accountId)
            if (destAccountId != null) put("destination_account_id", destAccountId)
            put("category", category)
            put("note", note)
            put("date_str", SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date()))
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("transactions", null, cv)
    }

    fun updateTransaction(id: Long, amountMinor: Long, category: String, note: String) {
        val cv = ContentValues().apply {
            put("amount_minor", amountMinor)
            put("category", category)
            put("note", note)
        }
        writableDatabase.update("transactions", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteTransaction(id: Long) {
        writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
    }

    fun getRecentTransactions(limit: Int = 30): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val sql = """
            SELECT t.id, t.type, t.amount_minor, t.currency, t.account_id, a.name, da.name, t.category, t.note, t.date_str
            FROM transactions t
            JOIN accounts a ON t.account_id = a.id
            LEFT JOIN accounts da ON t.destination_account_id = da.id
            ORDER BY t.id DESC
            LIMIT ?
        """.trimIndent()
        val c = readableDatabase.rawQuery(sql, arrayOf(limit.toString()))
        c.use {
            while (it.moveToNext()) {
                list.add(
                    TransactionItem(
                        id = it.getLong(0),
                        type = it.getString(1),
                        amount = Money(it.getLong(2), it.getString(3)),
                        accountId = it.getLong(4),
                        accountName = it.getString(5),
                        destinationAccountName = it.getString(6),
                        category = it.getString(7),
                        payeeOrNote = it.getString(8),
                        dateString = it.getString(9)
                    )
                )
            }
        }
        return list
    }

    fun getCashflowMetrics(baseCurrency: String): Triple<Money, Money, Money> {
        var totalIncome = 0L
        var totalExpenses = 0L
        var wantsExpenses = 0L

        val c = readableDatabase.rawQuery("SELECT type, amount_minor, category, currency FROM transactions", null)
        c.use {
            while (it.moveToNext()) {
                val type = it.getString(0)
                val amt = it.getLong(1)
                val cat = it.getString(2)
                val curr = it.getString(3)

                if (curr == baseCurrency) {
                    if (type == "INCOME") totalIncome += amt
                    else if (type == "EXPENSE") {
                        totalExpenses += amt
                        if (cat == "WANT") wantsExpenses += amt
                    }
                }
            }
        }
        return Triple(Money(totalIncome, baseCurrency), Money(totalExpenses, baseCurrency), Money(wantsExpenses, baseCurrency))
    }
}

// ============================================================================
// 3. PRESENTATION LAYER: FULLY INTERACTIVE FINTECH DASHBOARD
// ============================================================================

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: AppDatabaseHelper

    private lateinit var totalCapitalText: TextView
    private lateinit var dailySafeSpendText: TextView
    private lateinit var daysRemainingText: TextView
    private lateinit var runwayText: TextView
    private lateinit var advisorBanner: LinearLayout
    private lateinit var advisorTitle: TextView
    private lateinit var advisorText: TextView

    private lateinit var accountsContainer: LinearLayout
    private lateinit var billsContainer: LinearLayout
    private lateinit var ledgerContainer: LinearLayout
    private lateinit var currencySpinner: Spinner

    private var currentCurrency = "LKR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = AppDatabaseHelper(this)
        currentCurrency = dbHelper.getBaseCurrency()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#070B14"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 44, 32, 60)
        }
        scrollView.addView(mainLayout)

        // Top Navigation & Currency Switcher
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val appTitle = TextView(this).apply {
            text = "Capital & Vaults"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val appSubtitle = TextView(this).apply {
            text = "Tap any card or item to edit"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        }
        titleBox.addView(appTitle)
        titleBox.addView(appSubtitle)

        currencySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("LKR (Rs.)", "USD ($)"))
            setSelection(if (currentCurrency == "USD") 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    val selected = if (pos == 0) "LKR" else "USD"
                    if (selected != currentCurrency) {
                        currentCurrency = selected
                        dbHelper.updateBaseCurrency(currentCurrency)
                        refreshDashboard()
                    }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
        headerBar.addView(titleBox)
        headerBar.addView(currencySpinner)
        mainLayout.addView(headerBar)

        // 1. Executive Snapshot Card (Tap "Days to Payday" to edit anchor)
        val snapCard = createStyledCard("#0F172A", "#1E293B")
        val capLabel = createLabel("TOTAL LIQUID CAPITAL (ACROSS ALL VAULTS)")
        totalCapitalText = TextView(this).apply {
            textSize = 34f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 16)
        }

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply { bottomMargin = 16 }
        }

        val metricsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        val safeSpendCol = createMetricItem("SAFE DAILY SPEND", "#10B981")
        dailySafeSpendText = safeSpendCol.second

        val daysCol = createMetricItem("DAYS TO PAYDAY (EDIT)", "#38BDF8")
        daysRemainingText = daysCol.second
        daysCol.first.setOnClickListener { showEditPaydayAnchorDialog() }

        val runwayCol = createMetricItem("EMERGENCY RUNWAY", "#F59E0B")
        runwayText = runwayCol.second

        metricsGrid.addView(safeSpendCol.first)
        metricsGrid.addView(daysCol.first)
        metricsGrid.addView(runwayCol.first)

        snapCard.addView(capLabel)
        snapCard.addView(totalCapitalText)
        snapCard.addView(divider)
        snapCard.addView(metricsGrid)
        mainLayout.addView(snapCard)

        // 2. Spending Leak Advisor Banner
        advisorBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
        }
        advisorTitle = TextView(this).apply { textSize = 12f; setTypeface(null, Typeface.BOLD) }
        advisorText = TextView(this).apply { textSize = 12f; setPadding(0, 4, 0, 0) }
        advisorBanner.addView(advisorTitle)
        advisorBanner.addView(advisorText)
        mainLayout.addView(advisorBanner)

        // 3. Vaults / Multi-Account Strip (Tap card to edit, "+ Add" to create)
        val accHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        val accTitle = TextView(this).apply {
            text = "Active Accounts & Vaults"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val addAccBtn = TextView(this).apply {
            text = "+ Add Vault"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setOnClickListener { showAddAccountDialog() }
        }
        accHeaderRow.addView(accTitle)
        accHeaderRow.addView(addAccBtn)
        mainLayout.addView(accHeaderRow)

        accountsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
        }
        mainLayout.addView(accountsContainer)

        // 4. Quick Action Buttons
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 28 }
        }
        val expenseBtn = createActionButton("− Expense", "#EF4444") { showTransactionDialog("EXPENSE") }
        val incomeBtn = createActionButton("+ Inflow", "#10B981") { showTransactionDialog("INCOME") }
        val transferBtn = createActionButton("⇄ Transfer", "#3B82F6") { showTransferDialog() }

        actionRow.addView(expenseBtn)
        actionRow.addView(incomeBtn)
        actionRow.addView(transferBtn)
        mainLayout.addView(actionRow)

        // 5. Recurring Bills Strip (Tap bill to edit, "+ Add Bill" to create)
        val billTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        val billTitle = TextView(this).apply {
            text = "Monthly Committed Bills"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val addBillBtn = TextView(this).apply {
            text = "+ Add Bill"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 20, 0)
            setOnClickListener { showAddBillDialog() }
        }
        val resetBills = TextView(this).apply {
            text = "Reset Cycle"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setOnClickListener {
                dbHelper.resetMonthlyBills()
                refreshDashboard()
                Toast.makeText(this@MainActivity, "Cycle bills reset", Toast.LENGTH_SHORT).show()
            }
        }
        billTitleRow.addView(billTitle)
        billTitleRow.addView(addBillBtn)
        billTitleRow.addView(resetBills)
        mainLayout.addView(billTitleRow)

        billsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
        }
        mainLayout.addView(billsContainer)

        // 6. Recent Ledger (Tap any transaction row to edit or delete)
        val ledgerTitle = TextView(this).apply {
            text = "Recent Transactions (Tap to Edit)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 12)
        }
        mainLayout.addView(ledgerTitle)

        ledgerContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainLayout.addView(ledgerContainer)

        setContentView(scrollView)
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val accounts = dbHelper.getAccounts()
        val bills = dbHelper.getRecurringBills()
        val anchor = dbHelper.getPaydayAnchor()
        val baseCurrency = dbHelper.getBaseCurrency()

        // 1. Total Liquid Capital
        val liquidSumMinor = accounts.filter { it.currency == baseCurrency }.sumOf { it.currentBalance.amountMinor }
        val totalCapital = Money(liquidSumMinor, baseCurrency)
        totalCapitalText.text = totalCapital.format()

        // 2. Payday Anchor & Days Remaining
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = if (currentDay <= anchor) {
            anchor - currentDay
        } else {
            (maxDaysInMonth - currentDay) + anchor
        }.coerceAtLeast(1)
        daysRemainingText.text = "$daysRemaining Days (Payday $anchor)"

        // 3. Quarantined Bills & Safe Spend
        val pendingBillsSumMinor = bills.filter { !it.isPaid && it.amount.currency == baseCurrency }.sumOf { it.amount.amountMinor }
        val freeToSpendMinor = (totalCapital.amountMinor - pendingBillsSumMinor).coerceAtLeast(0L)
        val dailySafeMinor = freeToSpendMinor / daysRemaining
        dailySafeSpendText.text = Money(dailySafeMinor, baseCurrency).format()

        // 4. Runway
        val totalMonthlyBurnMinor = bills.filter { it.amount.currency == baseCurrency }.sumOf { it.amount.amountMinor }
        val burnRate = if (totalMonthlyBurnMinor > 0L) totalMonthlyBurnMinor.toDouble() else 1.0
        val runwayMonths = if (totalCapital.amountMinor > 0L) totalCapital.amountMinor.toDouble() / burnRate else 0.0
        runwayText.text = String.format(Locale.US, "%.1f Mo", runwayMonths)

        // 5. 50/30/20 Advisor
        val (totalIncome, _, wantsExpenses) = dbHelper.getCashflowMetrics(baseCurrency)
        if (totalIncome.amountMinor > 0L) {
            val wantsPercent = (wantsExpenses.amountMinor.toDouble() / totalIncome.amountMinor.toDouble()) * 100.0
            if (wantsPercent > 30.0) {
                advisorBanner.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#230F13"))
                    cornerRadius = 14f
                    setStroke(2, Color.parseColor("#E11D48"))
                }
                advisorTitle.setTextColor(Color.parseColor("#FDA4AF"))
                advisorTitle.text = "⚠️ HIGH DISCRETIONARY BURN AUDIT"
                advisorText.setTextColor(Color.parseColor("#FFE4E6"))
                val excessMinor = (wantsExpenses.amountMinor - (totalIncome.amountMinor * 0.30)).toLong()
                advisorText.text = "Non-essential 'Wants' account for ${String.format(Locale.US, "%.1f", wantsPercent)}% of inflow (Target: <30%). Cutting ${Money(excessMinor, baseCurrency).format()} will restore your savings rate."
            } else {
                advisorBanner.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0C2018"))
                    cornerRadius = 14f
                    setStroke(2, Color.parseColor("#10B981"))
                }
                advisorTitle.setTextColor(Color.parseColor("#6EE7B7"))
                advisorTitle.text = "✓ LEAN BUDGET: OPTIMAL SPENDING VELOCITY"
                advisorText.setTextColor(Color.parseColor("#D1FAE5"))
                advisorText.text = "Your discretionary spending is disciplined (${String.format(Locale.US, "%.1f", wantsPercent)}% of income). Reserves remain protected."
            }
        } else {
            advisorBanner.background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 14f
            }
            advisorTitle.setTextColor(Color.parseColor("#94A3B8"))
            advisorTitle.text = "• CASHFLOW AWAITING PAYDAY INFLOW"
            advisorText.setTextColor(Color.parseColor("#CBD5E1"))
            advisorText.text = "Log monthly salary or income using '+ Inflow' to activate predictive burn tracking."
        }

        renderAccounts(accounts)
        renderBills(bills)
        renderLedger(dbHelper.getRecentTransactions())
    }

    private fun renderAccounts(accounts: List<Account>) {
        accountsContainer.removeAllViews()
        for (acc in accounts) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 18, 24, 18)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0F172A"))
                    cornerRadius = 14f
                    setStroke(1, Color.parseColor("#1E293B"))
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
                setOnClickListener { showEditAccountDialog(acc) }
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val name = TextView(this).apply {
                text = acc.name
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val typeTag = TextView(this).apply {
                text = "${acc.type} • ${acc.currency} • Tap to edit"
                textSize = 11f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 2, 0, 0)
            }
            col.addView(name)
            col.addView(typeTag)

            val bal = TextView(this).apply {
                text = acc.currentBalance.format()
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#38BDF8"))
                gravity = Gravity.CENTER_VERTICAL
            }

            card.addView(col)
            card.addView(bal)
            accountsContainer.addView(card)
        }
    }

    private fun renderBills(bills: List<RecurringBill>) {
        billsContainer.removeAllViews()
        for (bill in bills) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 16, 24, 16)
                background = GradientDrawable().apply {
                    setColor(if (bill.isPaid) Color.parseColor("#0A0F1D") else Color.parseColor("#111827"))
                    cornerRadius = 14f
                    if (!bill.isPaid) setStroke(1, Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
                setOnClickListener { showEditBillDialog(bill) }
            }

            val col = LinearLayout(this).apply {
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
                text = if (bill.isPaid) "Paid • Tap to edit" else "Due Day ${bill.dueDay} • Tap to edit"
                textSize = 11f
                setTextColor(if (bill.isPaid) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            }
            col.addView(title)
            col.addView(status)

            val amt = TextView(this).apply {
                text = bill.amount.format()
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (bill.isPaid) Color.parseColor("#64748B") else Color.WHITE)
                setPadding(0, 0, 16, 0)
                gravity = Gravity.CENTER_VERTICAL
            }

            val payBtn = Button(this).apply {
                text = if (bill.isPaid) "Done" else "Pay"
                textSize = 11f
                isEnabled = !bill.isPaid
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (bill.isPaid) Color.parseColor("#1E293B") else Color.parseColor("#2563EB"))
                    cornerRadius = 10f
                }
                setOnClickListener {
                    val accounts = dbHelper.getAccounts()
                    val primary = accounts.firstOrNull()
                    if (primary != null) {
                        dbHelper.markBillPaid(bill.id, primary.id)
                        refreshDashboard()
                    }
                }
            }

            card.addView(col)
            card.addView(amt)
            card.addView(payBtn)
            billsContainer.addView(card)
        }
    }

    private fun renderLedger(items: List<TransactionItem>) {
        ledgerContainer.removeAllViews()
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No recorded transactions in ledger."
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(8, 8, 8, 8)
            }
            ledgerContainer.addView(empty)
            return
        }

        for (item in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 14, 20, 14)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0F172A"))
                    cornerRadius = 12f
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
                setOnClickListener { showEditTransactionDialog(item) }
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val title = TextView(this).apply {
                text = item.payeeOrNote
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val sub = TextView(this).apply {
                val flowContext = if (item.type == "TRANSFER") "${item.accountName} ➔ ${item.destinationAccountName}" else "${item.accountName} • ${item.category}"
                text = "${item.dateString} • $flowContext"
                textSize = 11f
                setTextColor(when (item.type) {
                    "TRANSFER" -> Color.parseColor("#38BDF8")
                    "INCOME" -> Color.parseColor("#10B981")
                    else -> if (item.category == "WANT") Color.parseColor("#F59E0B") else Color.parseColor("#64748B")
                })
            }
            col.addView(title)
            col.addView(sub)

            val amt = TextView(this).apply {
                text = when (item.type) {
                    "INCOME" -> "+ " + item.amount.format()
                    "EXPENSE" -> "− " + item.amount.format()
                    else -> "⇄ " + item.amount.format()
                }
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(when (item.type) {
                    "INCOME" -> Color.parseColor("#10B981")
                    "EXPENSE" -> Color.parseColor("#EF4444")
                    else -> Color.parseColor("#38BDF8")
                })
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(col)
            row.addView(amt)
            ledgerContainer.addView(row)
        }
    }

    // ========================================================================
    // EDIT & CREATE DIALOGS (ACCOUNTS, BILLS, TRANSACTIONS, PAYDAY)
    // ========================================================================

    private fun showAddAccountDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add New Account / Vault")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val nameInput = EditText(this).apply { hint = "Account Name (e.g. Commercial Bank)" }
        val balanceInput = EditText(this).apply {
            hint = "Opening Balance"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("BANK", "CASH", "SAVINGS"))
        }

        layout.addView(nameInput)
        layout.addView(balanceInput)
        layout.addView(TextView(this).apply { text = "Account Type"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(typeSpinner)

        builder.setView(layout)
        builder.setPositiveButton("Create") { _, _ ->
            val name = nameInput.text.toString().trim()
            val balStr = balanceInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val money = Money.fromMajorString(balStr, currentCurrency)
                val type = typeSpinner.selectedItem.toString()
                dbHelper.insertAccount(name, type, currentCurrency, money.amountMinor)
                refreshDashboard()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showEditAccountDialog(account: Account) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Account: ${account.name}")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val nameInput = EditText(this).apply {
            setText(account.name)
            hint = "Account Name"
        }
        val balanceInput = EditText(this).apply {
            val initialMoney = Money(account.openingBalanceMinor, account.currency)
            setText(initialMoney.toDecimalString())
            hint = "Opening Balance"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val typeSpinner = Spinner(this).apply {
            val types = arrayOf("BANK", "CASH", "SAVINGS")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, types)
            setSelection(types.indexOf(account.type).coerceAtLeast(0))
        }

        layout.addView(nameInput)
        layout.addView(TextView(this).apply { text = "Base Opening Balance"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(balanceInput)
        layout.addView(TextView(this).apply { text = "Account Type"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(typeSpinner)

        builder.setView(layout)
        builder.setPositiveButton("Save") { _, _ ->
            val name = nameInput.text.toString().trim()
            val balStr = balanceInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val money = Money.fromMajorString(balStr, account.currency)
                val type = typeSpinner.selectedItem.toString()
                dbHelper.updateAccount(account.id, name, type, money.amountMinor)
                refreshDashboard()
            }
        }
        builder.setNeutralButton("Delete") { _, _ ->
            dbHelper.deleteAccount(account.id)
            refreshDashboard()
            Toast.makeText(this, "Account and associated logs deleted", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showAddBillDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Committed Recurring Bill")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val titleInput = EditText(this).apply { hint = "Bill Title (e.g. Gym, Water)" }
        val amountInput = EditText(this).apply {
            hint = "Estimated Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val dueDayInput = EditText(this).apply {
            hint = "Due Day of Month (1-31)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(titleInput)
        layout.addView(amountInput)
        layout.addView(dueDayInput)

        builder.setView(layout)
        builder.setPositiveButton("Add Bill") { _, _ ->
            val title = titleInput.text.toString().trim()
            val amtStr = amountInput.text.toString().trim()
            val day = dueDayInput.text.toString().toIntOrNull() ?: 1
            if (title.isNotEmpty() && amtStr.isNotEmpty()) {
                val money = Money.fromMajorString(amtStr, currentCurrency)
                dbHelper.insertBill(title, money.amountMinor, currentCurrency, day)
                refreshDashboard()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showEditBillDialog(bill: RecurringBill) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Recurring Bill")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val titleInput = EditText(this).apply { setText(bill.title); hint = "Title" }
        val amountInput = EditText(this).apply {
            setText(bill.amount.toDecimalString())
            hint = "Estimated Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val dueDayInput = EditText(this).apply {
            setText(bill.dueDay.toString())
            hint = "Due Day (1-31)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(titleInput)
        layout.addView(amountInput)
        layout.addView(dueDayInput)

        builder.setView(layout)
        builder.setPositiveButton("Save") { _, _ ->
            val title = titleInput.text.toString().trim()
            val amtStr = amountInput.text.toString().trim()
            val day = dueDayInput.text.toString().toIntOrNull() ?: bill.dueDay
            if (title.isNotEmpty() && amtStr.isNotEmpty()) {
                val money = Money.fromMajorString(amtStr, bill.amount.currency)
                dbHelper.updateBill(bill.id, title, money.amountMinor, day)
                refreshDashboard()
            }
        }
        builder.setNeutralButton("Delete") { _, _ ->
            dbHelper.deleteBill(bill.id)
            refreshDashboard()
            Toast.makeText(this, "Bill deleted", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showEditTransactionDialog(item: TransactionItem) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Ledger Entry")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val noteInput = EditText(this).apply {
            setText(item.payeeOrNote)
            hint = "Description / Note"
        }
        val amountInput = EditText(this).apply {
            setText(item.amount.toDecimalString())
            hint = "Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val categorySpinner = Spinner(this).apply {
            val categories = arrayOf("NEED", "WANT", "SALARY", "TRANSFER")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            setSelection(categories.indexOf(item.category).coerceAtLeast(0))
        }

        layout.addView(noteInput)
        layout.addView(amountInput)
        layout.addView(TextView(this).apply { text = "Category"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(categorySpinner)

        builder.setView(layout)
        builder.setPositiveButton("Save Changes") { _, _ ->
            val note = noteInput.text.toString().trim()
            val amtStr = amountInput.text.toString().trim()
            if (note.isNotEmpty() && amtStr.isNotEmpty()) {
                val money = Money.fromMajorString(amtStr, item.amount.currency)
                val cat = categorySpinner.selectedItem.toString()
                dbHelper.updateTransaction(item.id, money.amountMinor, cat, note)
                refreshDashboard()
                Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNeutralButton("Delete") { _, _ ->
            dbHelper.deleteTransaction(item.id)
            refreshDashboard()
            Toast.makeText(this, "Transaction deleted & balances recalculated", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showEditPaydayAnchorDialog() {
        val currentAnchor = dbHelper.getPaydayAnchor()
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set Payday Anchor Day")

        val input = EditText(this).apply {
            setText(currentAnchor.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Day of Month (1 - 31)"
            setPadding(40, 30, 40, 30)
        }
        builder.setView(input)
        builder.setPositiveButton("Update") { _, _ ->
            val day = input.text.toString().toIntOrNull()
            if (day != null && day in 1..31) {
                dbHelper.updatePaydayAnchor(day)
                refreshDashboard()
                Toast.makeText(this, "Payday anchor set to day $day", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showTransactionDialog(type: String) {
        val accounts = dbHelper.getAccounts()
        if (accounts.isEmpty()) {
            Toast.makeText(this, "Please create an account first", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (type == "INCOME") "Record Inflow / Salary" else "Record Expense")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val noteInput = EditText(this).apply { hint = "Description (e.g. Salary, Dining, Fuel)" }
        val amountInput = EditText(this).apply {
            hint = "Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val accountSpinner = Spinner(this).apply {
            val names = accounts.map { "${it.name} (${it.currentBalance.format()})" }
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
        }

        val categorySpinner = Spinner(this).apply {
            val cats = if (type == "INCOME") arrayOf("SALARY", "SIDE_INCOME") else arrayOf("NEED", "WANT")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, cats)
        }

        layout.addView(noteInput)
        layout.addView(amountInput)
        layout.addView(TextView(this).apply { text = "Select Account"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(accountSpinner)
        layout.addView(TextView(this).apply { text = "Select Category"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(categorySpinner)

        builder.setView(layout)
        builder.setPositiveButton("Save") { _, _ ->
            val note = noteInput.text.toString().trim()
            val amtString = amountInput.text.toString().trim()
            val selectedAccount = accounts[accountSpinner.selectedItemPosition]
            if (note.isNotEmpty() && amtString.isNotEmpty()) {
                val money = Money.fromMajorString(amtString, selectedAccount.currency)
                if (money.isPositive()) {
                    val category = categorySpinner.selectedItem.toString()
                    dbHelper.addTransaction(type, money, selectedAccount.id, null, category, note)
                    refreshDashboard()
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showTransferDialog() {
        val accounts = dbHelper.getAccounts()
        if (accounts.size < 2) {
            Toast.makeText(this, "Need at least 2 accounts to transfer", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Transfer Between Vaults")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val amountInput = EditText(this).apply {
            hint = "Transfer Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val names = accounts.map { "${it.name} (${it.currentBalance.format()})" }
        val sourceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
        }
        val destSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(1)
        }

        layout.addView(amountInput)
        layout.addView(TextView(this).apply { text = "From Source Vault"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(sourceSpinner)
        layout.addView(TextView(this).apply { text = "To Destination Vault"; textSize = 11f; setPadding(0, 10, 0, 2) })
        layout.addView(destSpinner)

        builder.setView(layout)
        builder.setPositiveButton("Transfer") { _, _ ->
            val srcPos = sourceSpinner.selectedItemPosition
            val dstPos = destSpinner.selectedItemPosition
            if (srcPos == dstPos) {
                Toast.makeText(this, "Source and destination cannot be identical", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val srcAcc = accounts[srcPos]
            val dstAcc = accounts[dstPos]
            val amtString = amountInput.text.toString().trim()
            if (amtString.isNotEmpty()) {
                val transferMoney = Money.fromMajorString(amtString, srcAcc.currency)
                if (transferMoney.isPositive()) {
                    dbHelper.addTransaction("TRANSFER", transferMoney, srcAcc.id, dstAcc.id, "TRANSFER", "Vault Transfer")
                    refreshDashboard()
                    Toast.makeText(this, "Transferred cleanly", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // UI Factories
    private fun createStyledCard(bgHex: String, borderHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 18f
                setStroke(1, Color.parseColor(borderHex))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 20 }
        }
    }

    private fun createMetricItem(label: String, valColorHex: String): Pair<LinearLayout, TextView> {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val lbl = createLabel(label)
        val value = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(valColorHex))
            setPadding(0, 4, 0, 0)
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

    private fun createActionButton(label: String, colorHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 }
            setOnClickListener { onClick() }
        }
    }
}
