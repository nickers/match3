package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.jetbrains.compose.resources.painterResource
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.compose_multiplatform
import kotlin.math.roundToInt

data class PlacedItem(
    val id: Int,
    val position: Offset,
    val label: String
)

@Composable
fun InfinitePlaneApp() {
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }
    var scale by remember { mutableStateOf(1f) }
    var selectedItem by remember { mutableStateOf<PlacedItem?>(null) }
    
    val items = remember {
        listOf(
            PlacedItem(1, Offset(0f, 0f), "Center"),
            PlacedItem(2, Offset(500f, 200f), "East"),
            PlacedItem(3, Offset(-400f, 300f), "South-West"),
            PlacedItem(4, Offset(200f, -400f), "North-East"),
            PlacedItem(5, Offset(-600f, -200f), "North-West"),
            PlacedItem(6, Offset(800f, -100f), "Far East"),
            PlacedItem(7, Offset(-300f, 600f), "Far South-West"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(
                if (selectedItem == null) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            offset = Offset(
                                offset.x + pan.x,
                                offset.y + pan.y
                            )
                            scale = (scale * zoom).coerceIn(0.3f, 3f)
                        }
                    }.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val scrollDelta = event.changes.first().scrollDelta.y
                                    if (scrollDelta != 0f) {
                                        val zoomFactor = if (scrollDelta < 0) 1.1f else 0.9f
                                        val newScale = (scale * zoomFactor).coerceIn(0.3f, 3f)
                                        val mousePos = event.changes.first().position
                                        val worldPosBefore = Offset(
                                            (mousePos.x - offset.x) / scale,
                                            (mousePos.y - offset.y) / scale
                                        )
                                        scale = newScale
                                        val worldPosAfter = Offset(
                                            worldPosBefore.x * scale + offset.x,
                                            worldPosBefore.y * scale + offset.y
                                        )
                                        offset = Offset(
                                            offset.x + (mousePos.x - worldPosAfter.x),
                                            offset.y + (mousePos.y - worldPosAfter.y)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            items.forEach { item ->
                Button(
                    onClick = { selectedItem = item },
                    modifier = Modifier
                        .offset { IntOffset(item.position.x.roundToInt(), item.position.y.roundToInt()) }
                ) {
                    Text(item.label)
                }
            }
        }

        Text(
            text = "Drag to pan | Scroll to zoom | Scale: ${((scale * 10).roundToInt()) / 10.0}x",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )

        selectedItem?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Image(painterResource(Res.drawable.compose_multiplatform), null)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Item ${item.id}: ${Greeting().greet()}")
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { selectedItem = null }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun App() {
    MaterialTheme {
        InfinitePlaneApp()
    }
}
