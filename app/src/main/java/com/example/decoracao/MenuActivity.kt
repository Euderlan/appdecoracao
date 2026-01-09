package com.example.decoracao

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}
