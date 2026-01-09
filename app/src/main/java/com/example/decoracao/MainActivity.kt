package com.example.decoracao

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.ModelNode

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DecoracaoAR"
        private const val MODEL_ASSET_PATH = "models/decoracao.glb"
    }

    private lateinit var arSceneView: ARSceneView
    private var tvHint: MaterialTextView? = null

    // Guardamos a instância do modelo para reutilizar quando criar o Anchor
    private var modelInstance: Any? = null // (tipo real é ModelInstance; deixo Any para evitar import chato)
    private var placedNode: ModelNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHint = findViewById(R.id.tvHint)
        arSceneView = findViewById(R.id.arSceneView)

        tvHint?.text = "Carregando modelo 3D..."
        loadModelFromAssets()

        // Toque para colocar no plano (chão/mesa)
        arSceneView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                tryPlaceOnPlane(event)
            }
            true
        }

        // Atualiza dica conforme tracking
        arSceneView.onSessionUpdated = { session, frame ->
            val tracking = frame.camera.trackingState
            if (placedNode == null) {
                tvHint?.text = when (tracking) {
                    TrackingState.TRACKING -> "Toque no chão/mesa para posicionar o objeto."
                    TrackingState.PAUSED -> "Movimente o celular para o ARCore rastrear o ambiente…"
                    TrackingState.STOPPED -> "Tracking parado."
                }
            }
        }
    }

    private fun loadModelFromAssets() {
        Log.d(TAG, "Carregando modelo de assets: $MODEL_ASSET_PATH")

        try {
            // cria a instância (uma vez)
            val instance = arSceneView.modelLoader.createModelInstance(
                assetFileLocation = MODEL_ASSET_PATH
            )
            modelInstance = instance
            tvHint?.text = "Modelo carregado. Aponte para o chão/mesa e toque para colocar."

        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao carregar modelo: ${t.message}", t)
            tvHint?.text = "Erro ao carregar modelo. Veja o Logcat."
            Snackbar.make(
                findViewById(android.R.id.content),
                "Erro ao carregar o modelo: ${t.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun tryPlaceOnPlane(event: MotionEvent) {
        if (placedNode != null) {
            // Se quiser permitir reposicionar, descomente:
            // placedNode?.destroy()
            // placedNode = null
            Snackbar.make(findViewById(android.R.id.content), "Objeto já posicionado.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val instance = modelInstance
        if (instance == null) {
            Snackbar.make(findViewById(android.R.id.content), "Modelo ainda não carregou.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val frame = arSceneView.currentFrame ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            Snackbar.make(findViewById(android.R.id.content), "Aguarde o tracking estabilizar…", Snackbar.LENGTH_SHORT).show()
            return
        }

        // Hit-test no ponto tocado
        val hits = frame.hitTest(event)
        val hit = hits.firstOrNull { hitResult ->
            val trackable = hitResult.trackable
            trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)
        }

        if (hit == null) {
            Snackbar.make(findViewById(android.R.id.content), "Toque em uma superfície detectada (chão/mesa).", Snackbar.LENGTH_SHORT).show()
            return
        }

        val anchor = hit.createAnchor()

        // ✅ Agora sim: prende em Anchor (fica parado)
        val node = ModelNode(
            modelInstance = instance as io.github.sceneview.model.ModelInstance,
            scaleToUnits = 1.0f
        ).apply {
            this.anchor = anchor
            // opcional: levantar um pouquinho se estiver "afundando" no plano
            // position = position.copy(y = position.y + 0.01f)
        }

        arSceneView.addChildNode(node)
        placedNode = node
        tvHint?.text = "Objeto fixado. ✅"
        Log.d(TAG, "Objeto ancorado e fixado no plano.")
    }

    override fun onDestroy() {
        try {
            arSceneView.destroy()
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
