package com.example.decoracao

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode

// Activity principal que gerencia a experiência de Realidade Aumentada
// Detecta planos, carrega modelos 3D e permite rotacionar com gestos
class MainActivity : AppCompatActivity() {

    // Constantes estáticas da classe
    companion object {
        // Tag para logs do Android Logcat
        private const val TAG = "DecoracaoAR"

        // Tempo mínimo entre tentativas de hit test em milissegundos
        // Evita fazer teste de colisão a cada frame (60fps seria processamento excessivo)
        private const val HITTEST_THROTTLE_MS = 100L

        // Tempo máximo de espera por um plano antes de usar fallback automático
        private const val FALLBACK_AFTER_MS = 4000L
    }

    // Componente principal que renderiza a cena de Realidade Aumentada
    private lateinit var arSceneView: ARSceneView

    // TextViews para exibir mensagens e informações ao usuário
    private var tvHint: TextView? = null
    private var tvModelName: TextView? = null

    // Node que representa o modelo 3D carregado
    private var modelNode: ModelNode? = null

    // Nó âncora que prende o modelo a uma posição fixa no espaço 3D
    private var anchorNode: AnchorNode? = null

    // Flag que indica se o modelo foi fixado com sucesso em um plano
    private var placedOnPlane = false

    // Armazena o tempo do último hit test realizado
    private var lastHitTestAt = 0L

    // Marca o momento em que começou a procurar por um plano
    private var startedSearchingAt = 0L

    // Caminho do arquivo do modelo GLB e seu nome (recebidos via Intent)
    private var modelPath = "models/pinheiro_de_natal.glb"
    private var modelName = "Pinheiro de Natal"

    // Armazena a rotação atual do modelo em torno do eixo Y (em radianos)
    private var currentRotationY = 0f

    // Detector de gestos que captura movimentos de toque na tela
    private lateinit var gestureDetector: GestureDetector

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Recupera o modelo e seu nome passados via Intent da MenuActivity
        modelPath = intent.getStringExtra("MODEL_PATH") ?: "models/pinheiro_de_natal.glb"
        modelName = intent.getStringExtra("MODEL_NAME") ?: "Pinheiro de Natal"

        // Inicializa as referências aos componentes da interface
        arSceneView = findViewById(R.id.arSceneView)
        tvHint = findViewById(R.id.tvHint)
        tvModelName = findViewById(R.id.tvModelName)

        // Exibe o nome do modelo selecionado na tela
        tvModelName?.text = modelName

        // Vincula o lifecycle da ARSceneView ao lifecycle da Activity
        // Essencial para sincronizar a sessão AR com o ciclo de vida da Activity
        arSceneView.lifecycle = lifecycle

        // Configura o detector de gestos para capturar deslizamento horizontal
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            // Método chamado quando o usuário desliza o dedo na tela
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // Só permite rotação se o modelo foi fixado e está carregado
                if (placedOnPlane && modelNode != null) {
                    // Incrementa a rotação Y baseado na distância horizontal do gesto
                    // O fator 0.01f controla a sensibilidade (quanto maior, mais sensível)
                    currentRotationY += distanceX * 0.01f
                    // Aplica a rotação ao modelo
                    modelNode?.rotation?.y = currentRotationY
                }
                return true
            }
        })

        // Configura a sessão do ARCore com parâmetros necessários
        arSceneView.configureSession { _, config ->
            // Define o modo de busca de planos
            // HORIZONTAL_AND_VERTICAL detecta chão, mesa E paredes
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

            // Estimativa de iluminação em alta definição
            // Renderiza o modelo com sombras e iluminação mais realista
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

            // Desabilita o modo de profundidade para evitar erros
            config.depthMode = Config.DepthMode.DISABLED

            // Desabilita posicionamento instantâneo para maior precisão na colocação
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        }

        // Ativa o renderizador de planos
        // Mostra planos detectados como grades translúcidas na tela
        arSceneView.planeRenderer.isEnabled = true
        arSceneView.planeRenderer.isVisible = true

        tvHint?.text = "Carregando modelo 3D..."

        // Carrega o modelo 3D do arquivo GLB
        loadModel()

        // Define o callback executado a cada atualização da sessão AR
        // Executa aproximadamente 60 vezes por segundo (60fps)
        arSceneView.onSessionUpdated = sessionUpdated@{ session, frame ->

            // Verifica se a view tem dimensões válidas
            if (arSceneView.width == 0 || arSceneView.height == 0) return@sessionUpdated

            // Verifica se a câmera está rastreando o ambiente corretamente
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                tvHint?.text = "Movimente o celular para rastrear o ambiente"
                return@sessionUpdated
            }

            // Sai se o modelo ainda não foi carregado
            val node = modelNode ?: return@sessionUpdated

            // Obtém o tempo atual em milissegundos
            val now = System.currentTimeMillis()

            // Inicializa o relógio de busca na primeira execução
            if (startedSearchingAt == 0L) startedSearchingAt = now

            // Oculta os planos depois que o modelo foi fixado
            arSceneView.planeRenderer.isVisible = !placedOnPlane

            // Controla a frequência de hit tests para economizar processamento
            // Não testa a cada frame, apenas a cada 100ms
            if (now - lastHitTestAt < HITTEST_THROTTLE_MS) return@sessionUpdated
            lastHitTestAt = now

            // Obtém as dimensões da tela em pixels
            val w = arSceneView.width.toFloat()
            val h = arSceneView.height.toFloat()

            // Define os pontos na tela onde será testada colisão com planos
            // Testa em 3 pontos: centro inferior, bem inferior e centro médio
            // Estes pontos cobrem bem a área onde um plano provavelmente será
            val testPoints = listOf(
                w * 0.5f to h * 0.70f,
                w * 0.5f to h * 0.80f,
                w * 0.5f to h * 0.50f
            )

            // Tenta encontrar uma colisão com um plano rastreado pelo ARCore
            val planeHit = testPoints.asSequence()
                // Para cada ponto de teste, realiza um hit test (teste de colisão)
                .flatMap { (x, y) -> frame.hitTest(x, y).asSequence() }
                // Filtra para encontrar apenas planos válidos
                .firstOrNull { hit ->
                    val t = hit.trackable
                    // Verifica múltiplas condições:
                    // - É um plano (não outro tipo de objeto)
                    // - Está sendo rastreado ativamente
                    // - O ponto de impacto está dentro do polígono do plano
                    // - Não foi absorvido por outro plano
                    (t is Plane) &&
                            t.trackingState == TrackingState.TRACKING &&
                            t.isPoseInPolygon(hit.hitPose) &&
                            t.subsumedBy == null
                }

            // Se encontrou um plano válido, fixa o modelo nele
            if (planeHit != null) {
                try {
                    // Cria uma âncora (ponto de referência fixo) no ponto de colisão
                    val anchor = session.createAnchor(planeHit.hitPose)
                    placedOnPlane = true

                    // Vincula o modelo à nova âncora criada
                    attachModelToNewAnchor(
                        AnchorNode(arSceneView.engine, anchor),
                        node
                    )

                    tvHint?.text = "Fixado no plano! Deslize para rotacionar"
                    // Esconde os planos após fixar o modelo
                    arSceneView.planeRenderer.isVisible = false

                    // Para de buscar planos após fixar com sucesso
                    // Reseta o relógio para evitar detecções duplicadas
                    startedSearchingAt = 0L

                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao fixar no plano", e)
                    tvHint?.text = "Erro ao fixar no plano"
                }
                return@sessionUpdated
            }

            // Se não encontrou plano, aguarda um tempo antes de usar fallback
            val elapsed = now - startedSearchingAt

            // Se a âncora ainda não foi criada (modelo não foi fixado)
            if (anchorNode == null) {
                // Continua procurando por um plano durante FALLBACK_AFTER_MS (4 segundos)
                if (elapsed < FALLBACK_AFTER_MS) {
                    tvHint?.text = "Procurando superfície (boa luz ajuda)"
                    return@sessionUpdated
                }

                // Se passou do tempo limite, usa fallback automático
                try {
                    // Cria uma pose (posição e orientação) a 1 metro à frente da câmera
                    // Valores: X=0 (centro), Y=-0.10 (um pouco abaixo), Z=-1.0 (1m frente)
                    val poseInFront = frame.camera.pose.compose(
                        Pose.makeTranslation(0f, -0.10f, -1.0f)
                    )
                    // Cria uma âncora nessa posição de fallback
                    val anchor = session.createAnchor(poseInFront)

                    // Vincula o modelo à âncora de fallback
                    attachModelToNewAnchor(
                        AnchorNode(arSceneView.engine, anchor),
                        node
                    )

                    tvHint?.text = "Modelo exibido (procurando plano)"
                    placedOnPlane = true

                    // Para a busca após usar fallback
                    startedSearchingAt = 0L

                } catch (e: Exception) {
                    Log.e(TAG, "Fallback failed", e)
                    tvHint?.text = "Falha ao mostrar modelo"
                }
            }
        }
    }

    // Função responsável por carregar o modelo 3D do arquivo GLB
    private fun loadModel() {
        try {
            // Carrega a instância do modelo usando o caminho especificado
            val modelInstance = arSceneView.modelLoader.createModelInstance(
                assetFileLocation = modelPath
            )

            // Cria o node do modelo com escala 1:1 (tamanho original)
            modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 1.0f
            ).apply {
                // Desabilita edição manual do node (remove gizmo de transformação)
                isEditable = false
            }

            Log.d(TAG, "Modelo carregado: $modelPath")
            tvHint?.text = "Aponte para o chão/mesa"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar modelo", e)
            tvHint?.text = "Erro ao carregar modelo"
        }
    }

    // Função que vincula o modelo a uma nova âncora
    // Remove a âncora anterior e cria uma nova com o modelo
    private fun attachModelToNewAnchor(newAnchorNode: AnchorNode, node: ModelNode) {
        // Remove a âncora anterior se existir
        anchorNode?.let { old ->
            // Desanexa a âncora do rastreamento
            try { old.anchor.detach() } catch (_: Throwable) {}
            // Remove o node da cena
            try { arSceneView.removeChildNode(old) } catch (_: Throwable) {}
            // Destroi o node e libera recursos
            try { old.destroy() } catch (_: Throwable) {}
        }

        // Remove o node do modelo de seu pai anterior
        node.parent = null

        // Reseta a rotação do modelo para zero
        currentRotationY = 0f
        node.rotation?.y = 0f

        // Adiciona o modelo como filho da nova âncora
        newAnchorNode.addChildNode(node)

        // Adiciona a âncora à cena AR para que seja renderizada
        arSceneView.addChildNode(newAnchorNode)

        // Atualiza a referência da âncora atual
        anchorNode = newAnchorNode
    }

    // Captura eventos de toque da tela
    // Passa os eventos para o gesture detector para detectar gestos
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            // Envia o evento de toque para o detector de gestos
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    // Limpa recursos quando a Activity é destruída
    // Chamado quando o usuário fecha o app ou a Activity é finalizada
    override fun onDestroy() {
        try {
            // Remove a âncora do rastreamento do ARCore
            try { anchorNode?.anchor?.detach() } catch (_: Throwable) {}
            // Destroi a view AR e libera seus recursos
            try { arSceneView.destroy() } catch (_: Throwable) {}
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}