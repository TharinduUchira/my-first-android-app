package com.example.myfirstapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: SalaryDbHelper
    private lateinit var recordsContainer: LinearLayout
    private lateinit var netSalaryPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = SalaryDbHelper(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 60)
        }
        scrollView.addView(mainLayout)

        // Title Header
        val header = TextView(this).apply {
            text = "Salary & Payroll Manager"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.START
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(header)

        // Input Fields
        val monthInput = createInputField("Month / Description (e.g. Oct 2026)", InputType.TYPE_CLASS_TEXT)
        val basicSalaryInput = createInputField("Basic Salary", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val allowancesInput = createInputField("Allowances / OT", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val deductionsInput = createInputField("Deductions (EPF / Tax / Advances)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

        mainLayout.addView(monthInput)
        mainLayout.addView(basicSalaryInput)
        mainLayout.addView(allowancesInput)
        mainLayout.addView(deductionsInput)

        // Live Net Salary Summary Display
        netSalaryPreview = TextView(this).apply {
            text = "Net Salary: $0.00"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0284C7"))
            setPadding(20, 30, 20, 30)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E0F2FE"))
        }
        mainLayout.addView(netSalaryPreview)

        // Button Row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 30, 0, 40)
        }

        val saveButton = Button(this).apply {
            text = "Save Record"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                marginEnd = 15
            }
            setOnClickListener {
                val month = monthInput.text.toString().trim()
                val basic = basicSalaryInput.text.toString().toDoubleOrNull() ?: 0.0
                val allowances = allowancesInput.text.toString().toDoubleOrNull() ?: 0.0
                val deductions = deductionsInput.text.toString().toDoubleOrNull() ?: 0.0

                if (month.isEmpty() || basic <= 0.0) {
                    Toast.makeText(this@MainActivity, "Enter month and valid basic salary", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val netSalary = (basic + allowances) - deductions
                dbHelper.insertRecord(month, basic, allowances, deductions, netSalary)

                netSalaryPreview.text = "Net: " + formatCurrency(netSalary)
                Toast.makeText(this@MainActivity, "Record Saved", Toast.LENGTH_SHORT).show()

                // Reset Inputs
                monthInput.text.clear()
                basicSalaryInput.text.clear()
                allowancesInput.text.clear()
                deductionsInput.text.clear()

                loadRecords()
            }
        }

        val clearButton = Button(this).apply {
            text = "Clear All"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
            setOnClickListener {
                dbHelper.deleteAllRecords()
                loadRecords()
                netSalaryPreview.text = "Net Salary: $0.00"
                Toast.makeText(this@MainActivity, "History cleared", Toast.LENGTH_SHORT).show()
            }
        }

        buttonRow.addView(saveButton)
        buttonRow.addView(clearButton)
        mainLayout.addView(buttonRow)

        // History Section Header
        val historyTitle = TextView(this).apply {
            text = "Payroll History"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 10, 0, 20)
        }
        mainLayout.addView(historyTitle)

        recordsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(recordsContainer)

        setContentView(scrollView)
        loadRecords()
    }

    private fun createInputField(hintText: String, inputType: Int): EditText {
        return EditText(this).apply {
            hint = hintText
            this.inputType = inputType
            textSize = 15f
            setPadding(30, 30, 30, 30)
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }
    }

    private fun loadRecords() {
        recordsContainer.removeAllViews()
        val records = dbHelper.getAllRecords()

        if (records.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No saved payroll records yet."
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(10, 10, 10, 10)
            }
            recordsContainer.addView(emptyText)
            return
        }

        for (record in records) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 20
                }
            }

            val title = TextView(this).apply {
                text = record.month
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
            }

            val details = TextView(this).apply {
                text = "Basic: ${formatCurrency(record.basic)} | OT: ${formatCurrency(record.allowances)} | Ded: ${formatCurrency(record.deductions)}"
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 8, 0, 8)
            }

            val net = TextView(this).apply {
                text = "Net: ${formatCurrency(record.net)}"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#16A34A"))
            }

            card.addView(title)
            card.addView(details)
            card.addView(net)
            recordsContainer.addView(card)
        }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount)
    }
}

// Data class
data class SalaryRecord(
    val id: Long,
    val month: String,
    val basic: Double,
    val allowances: Double,
    val deductions: Double,
    val net: Double
)

// Embedded SQLite Database Helper
class SalaryDbHelper(context: Context) : SQLiteOpenHelper(context, "salary.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE salary_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                month TEXT,
                basic REAL,
                allowances REAL,
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

    fun insertRecord(month: String, basic: Double, allowances: Double, deductions: Double, net: Double): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("month", month)
            put("basic", basic)
            put("allowances", allowances)
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
                        month = it.getString(it.getColumnIndexOrThrow("month")),
                        basic = it.getDouble(it.getColumnIndexOrThrow("basic")),
                        allowances = it.getDouble(it.getColumnIndexOrThrow("allowances")),
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
