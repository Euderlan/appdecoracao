package com.example.decoracao

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.ModelNode

/**
 * Implementação mínima com SceneView 2.x (arsceneview:2.3.1).
 *
 * - O modelo .glb está em: app/src/main/assets/models/decoracao.glb
 * - Para carregar via assets na 2.x, use o caminho relativo à pasta assets:
 *     "models/decoracao.glb"
 *
 * Observação: nesta versão, ModelNode exige o parâmetro `modelInstance` e o carregamento é feito via
 * `arSceneView.modelLoader.createModelInstance(...)` (não existe `loadModelGlbAsync(...)` aqui).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DecoracaoAR"
        private const val MODEL_ASSET_PATH = "models/decoracao.glb"
    }

    private lateinit var arSceneView: ARSceneView
    private var tvHint: MaterialTextView? = null

    private var modelNode: ModelNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHint = findViewById(R.id.tvHint)
        arSceneView = findViewById(R.id.arSceneView)

        tvHint?.text = "Carregando modelo 3D..."
        loadModelFromAssets()
    }

    private fun loadModelFromAssets() {
        Log.d(TAG, "Carregando modelo de assets: $MODEL_ASSET_PATH")

        try {
            // Cria a instância do modelo a partir do arquivo em assets.
            val modelInstance = arSceneView.modelLoader.createModelInstance(
                assetFileLocation = MODEL_ASSET_PATH
            )

            // Cria o nó do modelo (na 2.x, ModelNode exige modelInstance).
            val node = ModelNode(
                modelInstance = modelInstance,
                // Ajuste básico de escala (depois você pode mudar para ficar no tamanho real)
                scaleToUnits = 1.0f
            )

            modelNode = node

            // Adiciona o nó na cena. (Por enquanto, fica fixo na cena;
            // depois podemos fazer hit-test e colocar no plano ao tocar.)
            arSceneView.addChildNode(node)

            tvHint?.text = "Modelo carregado. (Próximo passo: colocar no chão/mesa ao tocar.)"
            Log.d(TAG, "Modelo carregado e adicionado na cena.")

        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao carregar/adicionar o modelo: ${t.message}", t)
            tvHint?.text = "Erro ao carregar modelo. Veja o Logcat."

            Snackbar.make(
                findViewById(android.R.id.content),
                "Erro ao carregar o modelo: ${t.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        // Na sua versão, não existem arSceneView.onResume()/onPause().
        // O destroy() existe e é o recomendado para liberar recursos.
        try {
            arSceneView.destroy()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
