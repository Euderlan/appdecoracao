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

        // Configura o listener do botão Pinheiro de Natal
        findViewById<MaterialButton>(R.id.btnPinheiro).setOnClickListener {
            // Cria um intent para iniciar a MainActivity
            val intent = Intent(this, MainActivity::class.java)
            // Passa o caminho do modelo GLB como extra
            intent.putExtra("MODEL_PATH", "models/pinheiro_de_natal.glb")
            // Passa o nome do modelo para exibição na tela
            intent.putExtra("MODEL_NAME", "Pinheiro de Natal")
            // Inicia a MainActivity com o modelo selecionado
            startActivity(intent)
        }

        // Configura o listener do botão Árvore Parabólica
        findViewById<MaterialButton>(R.id.btnArvore).setOnClickListener {
            // Cria um intent para iniciar a MainActivity
            val intent = Intent(this, MainActivity::class.java)
            // Passa o caminho do modelo GLB como extra
            intent.putExtra("MODEL_PATH", "models/arvore_parabolica.glb")
            // Passa o nome do modelo para exibição na tela
            intent.putExtra("MODEL_NAME", "Árvore Parabólica")
            // Inicia a MainActivity com o modelo selecionado
            startActivity(intent)
        }

        // Configura o listener do botão Decoração
        findViewById<MaterialButton>(R.id.btnDecoracao).setOnClickListener {
            // Cria um intent para iniciar a MainActivity
            val intent = Intent(this, MainActivity::class.java)
            // Passa o caminho do modelo GLB como extra
            intent.putExtra("MODEL_PATH", "models/decoracao.glb")
            // Passa o nome do modelo para exibição na tela
            intent.putExtra("MODEL_NAME", "Decoração")
            // Inicia a MainActivity com o modelo selecionado
            startActivity(intent)
        }
    }
}