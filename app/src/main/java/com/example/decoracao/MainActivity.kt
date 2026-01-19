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

// Activity principal do app de Realidade Aumentada (AR)
// Responsável por:
// 1) Configurar a sessão do ARCore (detecção de planos, luz, etc.)
// 2) Carregar o modelo 3D escolhido no menu
// 3) Tentar fixar automaticamente o modelo em um plano detectado
// 4) Se não achar plano, usar um "fallback" (mostra o modelo 1m à frente)
class MainActivity : AppCompatActivity() {

    companion object {
        // Tag usada para identificar logs no Logcat
        private const val TAG = "DecoracaoAR"

        // Intervalo mínimo entre hit tests (não faz hit test a cada frame)
        // Isso reduz custo de CPU e evita processar demais.
        private const val HITTEST_THROTTLE_MS = 100L

        // Tempo máximo esperando um plano antes de mostrar o modelo via fallback
        private const val FALLBACK_AFTER_MS = 4000L
    }

    // View principal do SceneView/ARSceneView: renderiza câmera + cena AR
    private lateinit var arSceneView: ARSceneView

    // TextViews do layout para orientar o usuário e mostrar nome do modelo
    private var tvHint: TextView? = null
    private var tvModelName: TextView? = null

    // Nó do modelo 3D (o objeto que será renderizado)
    private var modelNode: ModelNode? = null

    // Nó âncora: "prende" o modelo a uma posição fixa no mundo AR
    private var anchorNode: AnchorNode? = null

    // Indica se já foi fixado em algum plano (serve para esconder planos depois)
    private var placedOnPlane = false

    // Controla quando foi feito o último hitTest (para aplicar o throttle)
    private var lastHitTestAt = 0L

    // Marca quando começou a procurar por plano (para saber quando aplicar fallback)
    private var startedSearchingAt = 0L

    // Caminho e nome do modelo (vêm do MenuActivity via Intent)
    private var modelPath = "models/pinheiro_de_natal.glb"
    private var modelName = "Pinheiro de Natal"

    // Suprime warning de concatenação de texto (aqui é ok para dicas simples)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Infla o layout (activity_main.xml)
        setContentView(R.layout.activity_main)

        // Lê os extras enviados pelo MenuActivity.
        // Se não vier nada, usa o pinheiro como padrão.
        modelPath = intent.getStringExtra("MODEL_PATH") ?: "models/pinheiro_de_natal.glb"
        modelName = intent.getStringExtra("MODEL_NAME") ?: "Pinheiro de Natal"

        // Pega referências dos componentes da tela
        arSceneView = findViewById(R.id.arSceneView)
        tvHint = findViewById(R.id.tvHint)
        tvModelName = findViewById(R.id.tvModelName)

        // Mostra o nome do modelo escolhido na tela
        tvModelName?.text = modelName

        // Vincula o lifecycle da Activity ao ARSceneView
        // (permite que SceneView gerencie pausa/retorno corretamente)
        arSceneView.lifecycle = lifecycle

        // Configuração do ARCore (sessão)
        arSceneView.configureSession { _, config ->
            // Detecta planos horizontais (chão/mesa) e verticais (paredes)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

            // Iluminação mais realista usando HDR ambiental
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

            // Desliga depth para evitar possíveis incompatibilidades/erros
            config.depthMode = Config.DepthMode.DISABLED

            // Desliga instant placement (mais preciso, porém precisa de plano real)
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        }

        // Ativa o renderizador de planos (grade/mesh mostrando onde detectou plano)
        arSceneView.planeRenderer.isEnabled = true
        arSceneView.planeRenderer.isVisible = true

        // Mensagem inicial para o usuário
        tvHint?.text = "Carregando modelo 3D..."
        // Carrega o modelo escolhido do assets
        loadModel()

        // Callback chamado a cada atualização da sessão (normalmente ~60fps)
        arSceneView.onSessionUpdated = sessionUpdated@{ session, frame ->

            // Evita rodar lógica se a view ainda não tem tamanho (ex: no início)
            if (arSceneView.width == 0 || arSceneView.height == 0) return@sessionUpdated

            // Se a câmera não está rastreando, pede para o usuário mover o celular
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                tvHint?.text = "Movimente o celular para rastrear o ambiente"
                return@sessionUpdated
            }

            // Se o modelo ainda não carregou, não faz o resto
            val node = modelNode ?: return@sessionUpdated

            // Hora atual para throttle + fallback
            val now = System.currentTimeMillis()

            // Marca início da busca por plano na primeira vez
            if (startedSearchingAt == 0L) startedSearchingAt = now

            // Esconde/mostra grade de planos dependendo se já posicionou o modelo
            arSceneView.planeRenderer.isVisible = !placedOnPlane

            // Throttle do hitTest: só executa a cada HITTEST_THROTTLE_MS
            if (now - lastHitTestAt < HITTEST_THROTTLE_MS) return@sessionUpdated
            lastHitTestAt = now

            // Dimensões da tela em pixels para calcular pontos de teste
            val w = arSceneView.width.toFloat()
            val h = arSceneView.height.toFloat()

            // Pontos onde será feito hitTest:
            // - centro mais abaixo (70%)
            // - centro bem abaixo (80%)
            // - centro mais no meio (50%)
            // Isso aumenta as chances de pegar o chão/mesa sem o usuário tocar.
            val testPoints = listOf(
                w * 0.5f to h * 0.70f,
                w * 0.5f to h * 0.80f,
                w * 0.5f to h * 0.50f
            )

            // Faz hitTest nos pontos e pega o primeiro hit em plano válido
            val planeHit = testPoints.asSequence()
                // Para cada ponto, chama frame.hitTest(x, y) e transforma em sequência
                .flatMap { (x, y) -> frame.hitTest(x, y).asSequence() }
                // Filtra: queremos hit em Plane rastreado e "dentro" do polígono
                .firstOrNull { hit ->
                    val t = hit.trackable
                    (t is Plane) &&
                            t.trackingState == TrackingState.TRACKING &&
                            t.isPoseInPolygon(hit.hitPose) &&
                            // subsumedBy == null evita usar plano que foi "absorvido"
                            t.subsumedBy == null
                }

            // Se achou um plano válido, fixa o modelo nesse plano
            if (planeHit != null) {
                try {
                    // Cria âncora exatamente na pose do impacto
                    val anchor = session.createAnchor(planeHit.hitPose)
                    placedOnPlane = true

                    // Cria um AnchorNode e anexa o modelo dentro dele
                    attachModelToNewAnchor(
                        AnchorNode(arSceneView.engine, anchor),
                        node
                    )

                    // Atualiza UI: já fixou
                    tvHint?.text = "Fixado no plano!"
                    // Esconde a visualização de planos após fixar
                    arSceneView.planeRenderer.isVisible = false
                    // Reseta contador de busca (evita repetir fixação)
                    startedSearchingAt = 0L
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao fixar no plano", e)
                    tvHint?.text = "Erro ao fixar no plano"
                }
                return@sessionUpdated
            }

            // Se não encontrou plano, calcula tempo de espera
            val elapsed = now - startedSearchingAt

            // Só aplica fallback se ainda não existe âncora (modelo ainda não foi colocado)
            if (anchorNode == null) {

                // Enquanto não passou o tempo limite, continua tentando achar plano
                if (elapsed < FALLBACK_AFTER_MS) {
                    tvHint?.text = "Procurando superfície (boa luz ajuda)"
                    return@sessionUpdated
                }

                // Se passou do tempo, faz fallback: coloca 1m à frente da câmera
                try {
                    // Pega pose atual da câmera e compõe uma translação:
                    // x = 0 (centro), y = -0.10 (um pouco abaixo), z = -1.0 (1m à frente)
                    val poseInFront = frame.camera.pose.compose(
                        Pose.makeTranslation(0f, -0.10f, -1.0f)
                    )

                    // Cria âncora nessa pose (mesmo sem plano detectado)
                    val anchor = session.createAnchor(poseInFront)

                    // Anexa o modelo à âncora de fallback
                    attachModelToNewAnchor(
                        AnchorNode(arSceneView.engine, anchor),
                        node
                    )

                    // Mensagem: modelo apareceu, mas ainda pode achar plano depois
                    tvHint?.text = "Modelo exibido (procurando plano)"
                    placedOnPlane = true
                    startedSearchingAt = 0L
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback failed", e)
                    tvHint?.text = "Falha ao mostrar modelo"
                }
            }
        }
    }

    // Carrega o modelo GLB a partir do assets (ex: assets/models/quadro.glb)
    private fun loadModel() {
        try {
            // Cria uma instância do modelo usando o modelLoader do SceneView
            val modelInstance = arSceneView.modelLoader.createModelInstance(
                assetFileLocation = modelPath
            )

            // Cria um ModelNode (objeto renderizável na cena)
            modelNode = ModelNode(
                modelInstance = modelInstance,
                // scaleToUnits = 1.0f significa tentar manter escala "natural"
                scaleToUnits = 1.0f
            ).apply {
                // Não permite edição manual por gizmos/handles
                isEditable = false
            }

            Log.d(TAG, "Modelo carregado: $modelPath")
            // Orienta o usuário a apontar para uma superfície
            tvHint?.text = "Aponte para o chão/mesa"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar modelo", e)
            tvHint?.text = "Erro ao carregar modelo"
        }
    }

    // Troca a âncora atual por uma nova e coloca o modelo dentro dela
    private fun attachModelToNewAnchor(newAnchorNode: AnchorNode, node: ModelNode) {
        // Se já tinha âncora anterior, remove e libera recursos
        anchorNode?.let { old ->
            // Detacha do ARCore (para não ficar rastreando)
            try { old.anchor.detach() } catch (_: Throwable) {}
            // Remove da cena
            try { arSceneView.removeChildNode(old) } catch (_: Throwable) {}
            // Destroi o nó (libera recursos do SceneView)
            try { old.destroy() } catch (_: Throwable) {}
        }

        // Garante que o node não está preso a outro pai
        node.parent = null

        // Adiciona o modelo como filho da nova âncora
        newAnchorNode.addChildNode(node)

        // Adiciona a âncora na cena para renderizar
        arSceneView.addChildNode(newAnchorNode)

        // Salva referência da âncora atual
        anchorNode = newAnchorNode
    }

    // Chamado quando a Activity está sendo destruída (fechando a tela)
    override fun onDestroy() {
        try {
            // Detacha âncora do ARCore para evitar vazamento/uso de sessão
            try { anchorNode?.anchor?.detach() } catch (_: Throwable) {}
            // Destroi o ARSceneView e libera recursos gráficos
            try { arSceneView.destroy() } catch (_: Throwable) {}
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
