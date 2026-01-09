package com.example.decoracao

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DecoracaoAR"
        private const val MODEL_ASSET_PATH = "models/decoracao.glb"
        private const val DRAG_THROTTLE_MS = 50L

        // Preview 1m à frente: reancora no máximo a cada X ms (para sempre aparecer)
        private const val PREVIEW_REANCHOR_MS = 200L

        // Rotação do node (yaw) no máximo a cada X ms
        private const val NODE_ROTATION_UPDATE_MS = 50L
    }

    private lateinit var arSceneView: ARSceneView
    private var tvHint: MaterialTextView? = null

    private var modelNode: ModelNode? = null
    private var anchorNode: AnchorNode? = null

    private var lastFrame: Frame? = null
    private var lastSession: Session? = null

    private var isDragging = false
    private var lastDragUpdateAt = 0L

    private var didInitialPlacement = false
    private var isAnchoredOnSurface = false

    private var lastPreviewReanchorAt = 0L
    private var lastNodeRotationAt = 0L

    // ✅ Depois de fixar: gira o modelo (no node) para "acompanhar" o celular (sem reancorar -> sem tremor)
    private val rotateNodeWithPhoneWhenAnchored = true

    @SuppressLint("SetTextI18n")
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
                tvHint?.text = when {
                    tracking != TrackingState.TRACKING ->
                        "Movimente o celular para rastrear o ambiente…"
                    isAnchoredOnSurface ->
                        "Fixado! (Gire o celular para rotacionar o modelo.)"
                    else ->
                        "Aponte para a superfície e toque para fixar."
                }
            }

            // ✅ PREVIEW: enquanto não fixou, mantém modelo 1m à frente para sempre aparecer
            if (tracking == TrackingState.TRACKING && modelNode != null && !isAnchoredOnSurface) {
                val now = System.currentTimeMillis()
                if (!didInitialPlacement || now - lastPreviewReanchorAt >= PREVIEW_REANCHOR_MS) {
                    try {
                        val poseInFront = frame.camera.pose.compose(
                            Pose.makeTranslation(0f, -0.15f, -1.0f)
                        )
                        val anchor = session.createAnchor(poseInFront)
                        attachModelToNewAnchor(anchor, modelNode!!)
                        didInitialPlacement = true
                        lastPreviewReanchorAt = now
                    } catch (e: Exception) {
                        Log.e(TAG, "Falha no preview (reanchor)", e)
                    }
                }
            }

            // ✅ ROTACIONAR NO NODE (sem reancorar) -> não treme
            if (
                rotateNodeWithPhoneWhenAnchored &&
                tracking == TrackingState.TRACKING &&
                isAnchoredOnSurface &&
                modelNode != null &&
                anchorNode != null
            ) {
                val now = System.currentTimeMillis()
                if (now - lastNodeRotationAt >= NODE_ROTATION_UPDATE_MS) {
                    try {
                        val camPose = frame.camera.pose
                        val objPose = anchorNode!!.anchor.pose

                        // vetor do objeto -> câmera no plano XZ
                        val dx = camPose.tx() - objPose.tx()
                        val dz = camPose.tz() - objPose.tz()

                        // yaw para o modelo "apontar" para a câmera
                        val yaw = atan2(dx, dz).toFloat()

                        modelNode!!.rotation = Rotation(0f, yaw, 0f)

                        lastNodeRotationAt = now
                    } catch (e: Exception) {
                        Log.e(TAG, "Falha ao rotacionar node", e)
                    }
                }
            }
        }

        setupTouch()
    }

    @SuppressLint("SetTextI18n")
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        arSceneView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    lastDragUpdateAt = 0L
                    updatePlacementFromTouch(event, force = true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    updatePlacementFromTouch(event, force = false)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePlacementFromTouch(event: MotionEvent, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDragUpdateAt < DRAG_THROTTLE_MS) return
        lastDragUpdateAt = now

        val frame = lastFrame ?: return
        val session = lastSession ?: return
        val node = modelNode ?: return

        if (frame.camera.trackingState != TrackingState.TRACKING) return

        // ✅ Agora aceita Plane OU Point (feature point)
        val hit = frame.hitTest(event).firstOrNull { hitResult ->
            when (val trackable = hitResult.trackable) {
                is Plane -> trackable.isPoseInPolygon(hitResult.hitPose)
                is Point -> true
                else -> false
            }
        } ?: run {
            tvHint?.text = "Procure uma superfície (chão/mesa) com textura e boa luz."
            return
        }

        try {
            // ✅ Se for Plane: ancora no centro do plano
            // ✅ Se for Point: ancora no hitPose
            val anchor: Anchor = when (val trackable = hit.trackable) {
                is Plane -> session.createAnchor(trackable.centerPose)
                else -> session.createAnchor(hit.hitPose)
            }

            attachModelToNewAnchor(anchor, node)

            isAnchoredOnSurface = true
            didInitialPlacement = true
            tvHint?.text = "Fixado! (Plane ou ponto detectado.)"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fixar", e)
        }
    }

    private fun attachModelToNewAnchor(anchor: Anchor, node: ModelNode) {
        // remove âncora anterior
        anchorNode?.let { old ->
            try { old.anchor.detach() } catch (_: Throwable) {}
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
            try { anchorNode?.anchor?.detach() } catch (_: Throwable) {}
            arSceneView.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
