package com.example.decoracao

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DecoracaoAR"

        // ✅ confira se esse arquivo existe em: app/src/main/assets/models/
        private const val MODEL_ASSET_PATH = "models/pinheiro_de_natal.glb"

        // Ajustes do autoplace
        private const val HITTEST_THROTTLE_MS = 100L
        private const val FALLBACK_AFTER_MS = 2000L
    }

    private lateinit var arSceneView: ARSceneView
    private var tvHint: TextView? = null

    private var modelNode: ModelNode? = null
    private var anchorNode: AnchorNode? = null

    private var placedOnPlane = false

    // controle do autoplace
    private var lastHitTestAt = 0L
    private var startedSearchingAt = 0L

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arSceneView = findViewById(R.id.arSceneView)
        tvHint = findViewById(R.id.tvHint)

        // ✅ Nesta versão do SceneView, o correto é isso:
        arSceneView.lifecycle = lifecycle

        // ✅ Config ARCore
        arSceneView.configureSession { _, config ->
            // Horizontal (chão/mesa). Se quiser parede também, troque para HORIZONTAL_AND_VERTICAL
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

            // ✅ Desliga DEPTH (você estava tendo "Invalid depth" e "No point hit")
            config.depthMode = Config.DepthMode.DISABLED

            // ✅ Para fixar em plano real
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        }

        // Debug de plano: eu deixo ligado e mostro só enquanto não fixou
        arSceneView.planeRenderer.isEnabled = true
        arSceneView.planeRenderer.isVisible = true

        tvHint?.text = "Carregando modelo 3D..."
        loadModel()

        arSceneView.onSessionUpdated = sessionUpdated@{ session, frame ->

            // garante que a view já tem tamanho
            if (arSceneView.width == 0 || arSceneView.height == 0) return@sessionUpdated

            // tracking ainda não estabilizou
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                tvHint?.text = "Movimente o celular para rastrear o ambiente…"
                return@sessionUpdated
            }

            val node = modelNode ?: return@sessionUpdated

            // inicializa relógio de busca
            val now = System.currentTimeMillis()
            if (startedSearchingAt == 0L) startedSearchingAt = now

            // mostra plano só enquanto não fixou em plano
            arSceneView.planeRenderer.isVisible = !placedOnPlane

            // throttle do hit-test (não precisa tentar 60x/seg)
            if (now - lastHitTestAt < HITTEST_THROTTLE_MS) return@sessionUpdated
            lastHitTestAt = now

            val w = arSceneView.width.toFloat()
            val h = arSceneView.height.toFloat()

            // ✅ pontos melhores pra chão/mesa (mais pra baixo)
            val testPoints = listOf(
                w * 0.5f to h * 0.70f,
                w * 0.5f to h * 0.80f,
                w * 0.5f to h * 0.50f
            )

            // 1) tenta achar um PLANO (fixação “de verdade”)
            val planeHit = testPoints.asSequence()
                .flatMap { (x, y) -> frame.hitTest(x, y).asSequence() }
                .firstOrNull { hit ->
                    val t = hit.trackable
                    (t is Plane) &&
                            t.trackingState == TrackingState.TRACKING &&
                            t.isPoseInPolygon(hit.hitPose) &&
                            t.subsumedBy == null
                }

            if (planeHit != null) {
                try {
                    val anchor = session.createAnchor(planeHit.hitPose)
                    placedOnPlane = true

                    attachModelToNewAnchor(
                        AnchorNode(arSceneView.engine, anchor),
                        node
                    )

                    tvHint?.text = "Fixado no plano!"
                    arSceneView.planeRenderer.isVisible = false
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao fixar no plano", e)
                    tvHint?.text = "Erro ao fixar no plano (Logcat)."
                }
                return@sessionUpdated
            }

            // 2) se não achou plano ainda: aguarda um pouco e faz fallback automático
            val elapsed = now - startedSearchingAt

            if (anchorNode == null) {
                if (elapsed < FALLBACK_AFTER_MS) {
                    tvHint?.text = "Procurando superfície… (boa luz e textura ajudam)"
                    return@sessionUpdated
                }

                // fallback: coloca 1m à frente UMA vez (não segue a câmera)
                try {
                    val poseInFront = frame.camera.pose.compose(
                        Pose.makeTranslation(0f, -0.10f, -1.0f)
                    )
                    val anchor = session.createAnchor(poseInFront)

                    attachModelToNewAnchor(
                        AnchorNode(arSceneView.engine, anchor),
                        node
                    )

                    tvHint?.text = "Mostrando modelo… (aguardando plano para fixar)"
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback failed", e)
                    tvHint?.text = "Falha ao mostrar o modelo (Logcat)."
                }
            } else {
                // modelo já está visível via fallback, continua esperando plano aparecer
                tvHint?.text = "Aponte para chão/mesa… aguardando plano para fixar"
            }
        }
    }

    private fun loadModel() {
        try {
            val modelInstance = arSceneView.modelLoader.createModelInstance(
                assetFileLocation = MODEL_ASSET_PATH
            )

            modelNode = ModelNode(
                modelInstance = modelInstance,
                // Se ficar gigante ou minúsculo, ajuste aqui:
                scaleToUnits = 1.0f
            ).apply {
                isEditable = false // remove manipulação manual do node
            }

            Log.d(TAG, "Modelo carregado com sucesso.")
            tvHint?.text = "Aponte para o chão/mesa…"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar modelo", e)
            tvHint?.text = "Erro ao carregar o modelo (veja o Logcat)."
        }
    }

    private fun attachModelToNewAnchor(newAnchorNode: AnchorNode, node: ModelNode) {
        // remove âncora anterior
        anchorNode?.let { old ->
            try { old.anchor.detach() } catch (_: Throwable) {}
            try { arSceneView.removeChildNode(old) } catch (_: Throwable) {}
            try { old.destroy() } catch (_: Throwable) {}
        }

        node.parent = null
        newAnchorNode.addChildNode(node)
        arSceneView.addChildNode(newAnchorNode)

        anchorNode = newAnchorNode
    }

    override fun onDestroy() {
        try {
            try { anchorNode?.anchor?.detach() } catch (_: Throwable) {}
            try { arSceneView.destroy() } catch (_: Throwable) {}
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
