package com.example.decoracao

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

// Activity que funciona como menu de seleção de modelos
// O usuário escolhe qual modelo 3D deseja visualizar em AR
class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Carrega o layout da activity menu (activity_menu.xml)
        setContentView(R.layout.activity_menu)

        // Botão Pinheiro de Natal
        findViewById<MaterialButton>(R.id.btnPinheiro).setOnClickListener {
            startAR("models/pinheiro_de_natal.glb", "Pinheiro de Natal")
        }

        // Botão Árvore Parabólica
        findViewById<MaterialButton>(R.id.btnArvore).setOnClickListener {
            startAR("models/arvore_parabolica.glb", "Árvore Parabólica")
        }

        // Botão Decoração
        findViewById<MaterialButton>(R.id.btnDecoracao).setOnClickListener {
            startAR("models/decoracao.glb", "Decoração")
        }

        // Botão Quadro
        findViewById<MaterialButton>(R.id.btnQuadro).setOnClickListener {
            startAR("models/quadro.glb", "Quadro")
        }
    }

    // Função auxiliar para iniciar a MainActivity
    private fun startAR(modelPath: String, modelName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("MODEL_PATH", modelPath)
            putExtra("MODEL_NAME", modelName)
        }
        startActivity(intent)
    }
}
