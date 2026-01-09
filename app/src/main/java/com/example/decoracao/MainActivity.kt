package com.example.decoracao

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DecoracaoAR"
        private const val MODEL_ASSET_PATH = "models/decoracao.glb"
        private const val DRAG_THROTTLE_MS = 50L
    }

    private lateinit var arSceneView: ARSceneView
    private var tvHint: MaterialTextView? = null

    private var modelNode: ModelNode? = null
    private var anchorNode: AnchorNode? = null

    private var lastFrame: Frame? = null
    private var lastSession: Session? = null

    private var isDragging = false
    private var lastDragUpdateAt = 0L

    // garante que aparece ao menos uma vez
    private var didInitialPlacement = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arSceneView = findViewById(R.id.arSceneView)
        tvHint = findViewById(R.id.tvHint)

        tvHint?.text = "Carregando modelo 3D..."
        loadModel()

        arSceneView.onSessionUpdated = { session, frame ->
            lastSession = session
            lastFrame = frame

            val tracking = frame.camera.trackingState
            if (!isDragging) {
                tvHint?.text = if (tracking == TrackingState.TRACKING) {
                    "Toque e arraste no chão/mesa para posicionar."
                } else {
                    "Movimente o celular para rastrear o ambiente…"
                }
            }

            // ✅ Fallback: assim que o tracking ficar OK, coloca o modelo 1m à frente (para ele SEMPRE aparecer)
            if (!didInitialPlacement && tracking == TrackingState.TRACKING && modelNode != null) {
                try {
                    val poseInFront = frame.camera.pose.compose(Pose.makeTranslation(0f, 0f, -1.0f))
                    val anchor = session.createAnchor(poseInFront)
                    attachModelToNewAnchor(anchor, modelNode!!)
                    didInitialPlacement = true
                    tvHint?.text = "Modelo aparecendo. Arraste no chão/mesa para reposicionar."
                } catch (e: Exception) {
                    Log.e(TAG, "Falha no posicionamento inicial", e)
                }
            }
        }

        // Arrastar para reposicionar
        arSceneView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    lastDragUpdateAt = 0L
                    updatePlacementFromTouch(event, force = true)
                }
                MotionEvent.ACTION_MOVE -> {
                    updatePlacementFromTouch(event, force = false)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    tvHint?.text = "Posicionado! (Arraste de novo para reposicionar.)"
                }
            }
            true
        }
    }

    private fun loadModel() {
        try {
            val modelInstance = arSceneView.modelLoader.createModelInstance(
                assetFileLocation = MODEL_ASSET_PATH
            )

            modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 1.0f
            )

            tvHint?.text = "Modelo carregado. Aponte para o chão/mesa…"
            Log.d(TAG, "Modelo carregado com sucesso.")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar modelo", e)
            tvHint?.text = "Erro ao carregar o modelo (veja o Logcat)."
        }
    }

    private fun updatePlacementFromTouch(event: MotionEvent, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDragUpdateAt < DRAG_THROTTLE_MS) return
        lastDragUpdateAt = now

        val frame = lastFrame ?: return
        val session = lastSession ?: return
        val node = modelNode ?: return

        if (frame.camera.trackingState != TrackingState.TRACKING) return

        // Hit-test no ponto do toque (precisa tocar em plano detectado)
        val hit = frame.hitTest(event).firstOrNull { hitResult ->
            val trackable = hitResult.trackable
            trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)
        } ?: run {
            // Se ainda não detectou plano, mantém o modelo onde está (fallback)
            tvHint?.text = "Procure uma superfície (chão/mesa) com textura e boa luz."
            return
        }

        try {
            val anchor = session.createAnchor(hit.hitPose)
            attachModelToNewAnchor(anchor, node)
            didInitialPlacement = true // já está posicionado de forma “boa”
            tvHint?.text = "Arraste para ajustar. Solte para fixar."
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reposicionar com anchor", e)
        }
    }

    private fun attachModelToNewAnchor(anchor: Anchor, node: ModelNode) {
        // remove âncora anterior
        anchorNode?.let { old ->
            try { old.anchor?.detach() } catch (_: Throwable) {}
            try { arSceneView.removeChildNode(old) } catch (_: Throwable) {}
            try { old.destroy() } catch (_: Throwable) {}
        }
        anchorNode = null

        val newAnchorNode = AnchorNode(
            engine = arSceneView.engine,
            anchor = anchor
        )

        node.parent = null
        newAnchorNode.addChildNode(node)
        arSceneView.addChildNode(newAnchorNode)

        anchorNode = newAnchorNode
    }

    override fun onDestroy() {
        try {
            anchorNode?.anchor?.detach()
            arSceneView.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
