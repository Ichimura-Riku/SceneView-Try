package com.senmonb.sceneview_tutorial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.senmonb.sceneview_tutorial.ui.theme.SceneView_tutorialTheme
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView

private const val kModelFile = "models/square.glb"
private const val kMaxModelInstances = 10

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SceneView_tutorialTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(),) {
                        ARScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun ARScreen(){

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine = engine)
    var planeRenderer by remember { mutableStateOf(true) }
    val view = rememberView(engine = engine)
    val cameraNode = rememberARCameraNode(engine = engine)
    val childNodes = rememberNodes()
    var frame by remember { mutableStateOf<Frame?>(null) }
    val materialLoader = rememberMaterialLoader(engine = engine)
    val modelInstances = remember { mutableListOf<ModelInstance>() }
    val onSessionUpdated: (session: Session, frame: Frame) -> Unit = { session, updatedFrame ->
        frame = updatedFrame
        if (childNodes.isEmpty()) {
            updatedFrame.getUpdatedPlanes()
                .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                ?.let { it.createAnchorOrNull(it.centerPose) }?.let { anchor ->
                    childNodes += createAnchorNode(
                        engine = engine,
                        modelLoader = modelLoader,
                        materialLoader = materialLoader,
                        modelInstances = modelInstances,
                        anchor = anchor
                    )
                }
        }
    }

    val onGestureListener = rememberOnGestureListener(
        onSingleTapConfirmed = { motionEvent, node ->
            if (node == null) {
                val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                hitResults?.firstOrNull {
                    it.isValid(
                        depthPoint = false,
                        point = false
                    )
                }?.createAnchorOrNull()
                    ?.let { anchor ->
                        planeRenderer = false
                        childNodes += createAnchorNode(
                            engine = engine,
                            modelLoader = modelLoader,
                            materialLoader = materialLoader,
                            modelInstances = modelInstances,
                            anchor = anchor
                        )
                    }
            }
        })


    // option
    /**
     *  深度取得モード、即時配置モード、光照推定モードなどを設定
     * */
    val sessionConfiguration: (session: Session, Config) -> Unit = { session, config ->
        config.depthMode =
            when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                true -> Config.DepthMode.AUTOMATIC
                else -> Config.DepthMode.DISABLED
            }
        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
        config.lightEstimationMode =
            Config.LightEstimationMode.ENVIRONMENTAL_HDR
    }

    ARScene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        sessionConfiguration = sessionConfiguration,
        planeRenderer = planeRenderer,
        view = view,
        cameraNode = cameraNode,
        childNodes = childNodes,
        onSessionUpdated = onSessionUpdated,
        onGestureListener = onGestureListener,

//        < option >
//        sessionFeatures = ,
//        sessionCameraConfig = ,
//        cameraStream = ,
//        isOpaque = ,
//        renderer = ,
//        scene = ,
//        environment = ,
//        mainLightNode = ,
//        collisionSystem = ,
//        viewNodeWindowManager = ,
//        onSessionCreated = ,
//        onSessionResumed = ,
//        onSessionPaused = ,
//        onSessionFailed = ,
//        onTrackingFailureChanged ,
//        onTouchEvent = ,
//        activity = ,
//        lifecycle = ,
//        onViewUpdated = ,
//        onViewCreated = ,


    )
}
fun createAnchorNode(
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    modelInstances: MutableList<ModelInstance>,
    anchor: Anchor
): AnchorNode {
    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
    val modelNode = ModelNode(
        modelInstance = modelInstances.apply {
            if (isEmpty()) {
                this += modelLoader.createInstancedModel(kModelFile, kMaxModelInstances)
            }
        }.removeLast(),
        // Scale to fit in a 0.5 meters cube
        scaleToUnits = 0.5f
    ).apply {
        // Model Node needs to be editable for independent rotation from the anchor rotation
        isEditable = true
    }
    val boundingBoxNode = CubeNode(
        engine,
        size = modelNode.extents,
        center = modelNode.center,
        materialInstance = materialLoader.createColorInstance(Color.White.copy(alpha = 0.5f))
    ).apply {
        isVisible = false
    }
    modelNode.addChildNode(boundingBoxNode)
    anchorNode.addChildNode(modelNode)

    listOf(modelNode, anchorNode).forEach {
        it.onEditingChanged = { editingTransforms ->
            boundingBoxNode.isVisible = editingTransforms.isNotEmpty()
        }
    }
    return anchorNode
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SceneView_tutorialTheme {
        Greeting("Android")
    }
}