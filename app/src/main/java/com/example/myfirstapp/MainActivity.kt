package com.example.myfirstapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "Hello from Core i3 + Cloud Pipeline!"
            textSize = 22f
            setPadding(60, 200, 60, 0)
        }
        setContentView(textView)
    }
}
