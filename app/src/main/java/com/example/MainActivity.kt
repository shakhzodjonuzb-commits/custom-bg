package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.AppUiState
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BgRemoverViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BgRemoverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Enable modern edge-to-edge full screen
        enableEdgeToEdge()

        // 2. Configure immersive fullscreen mode (hide system bars, allow swiping to reveal)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F19)
                ) {
                    AppMainLayout(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppMainLayout(viewModel: BgRemoverViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Pickers setup
    val originalImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processOriginalImage(context, uri)
        }
    }

    val backgroundImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            when (val state = uiState) {
                is AppUiState.PreviewForeground -> {
                    viewModel.selectBackground(context, uri, state.originalUri, state.transparentBitmap)
                }
                is AppUiState.Composited -> {
                    viewModel.selectBackground(context, uri, state.originalUri, state.transparentBitmap)
                }
                else -> {}
            }
        }
    }

    // Smooth page content rendering using transitions
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn(animationSpec = tween(450)) togetherWith fadeOut(animationSpec = tween(350))
            },
            label = "screen_navigation"
        ) { state ->
            when (state) {
                is AppUiState.SelectingOriginal -> {
                    MainScreen(
                        onSelectClick = { originalImagePicker.launch("image/*") }
                    )
                }

                is AppUiState.Processing -> {
                    ProcessingScreen(message = state.message)
                }

                is AppUiState.PreviewForeground -> {
                    PreviewForegroundScreen(
                        state = state,
                        onReselect = { originalImagePicker.launch("image/*") },
                        onCancel = { viewModel.resetToStart() },
                        onSelectBackground = { backgroundImagePicker.launch("image/*") }
                    )
                }

                is AppUiState.Composited -> {
                    CompositedScreen(
                        state = state,
                        onReselectBackground = { backgroundImagePicker.launch("image/*") },
                        onSave = { viewModel.saveCompositedImage(context, state) }
                    )
                }

                is AppUiState.Saving -> {
                    ProcessingScreen(message = "Rasm saqlanmoqda...")
                }

                is AppUiState.Success -> {
                    SuccessScreen(
                        uri = state.savedUri,
                        message = state.message,
                        onReset = { viewModel.resetToStart() }
                    )
                }

                is AppUiState.Error -> {
                    ErrorScreen(
                        message = state.errorMessage,
                        onRetry = state.onRetry,
                        onCancel = state.onCancel
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. MAIN SCREEN
// ---------------------------------------------------------------------------
@Composable
fun MainScreen(onSelectClick: () -> Unit) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Enforce full bleed loop back.mp4 video with particle gradients fallback
        VideoBackgroundLoop(context = context)

        // Top brand header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BG REMOVER",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .background(Color(0xFF6366F1), RoundedCornerShape(2.dp))
            )
        }

        // Subtitle card center
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .blur(0.dp) // decorative
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Professional Fundan Tozalovchi",
                    fontSize = 18.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Har qanday rasmingiz fonini bir tegishda o'chirib, uni orqa manzaralar bilan bemalol moslashtiring.",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom styled Select.png call-to-action button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PressEffectButton(
                onClick = onSelectClick,
                modifier = Modifier.wrapContentSize()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.select),
                    contentDescription = "Select original photo button",
                    modifier = Modifier.size(200.dp, 56.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Saqlash formati: PNG • Pictures/Custom BG/",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. PROCESSING / LOADING SCREEN
// ---------------------------------------------------------------------------
@Composable
fun ProcessingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // Aesthetic moving stars backdrop
        AnimatedGradientFallback()

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant glowing circular container card
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF6366F1),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Iltimos kuting",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. RESULT PREVIEW SCREEN (TRANSPARENT PNG DISPLAY)
// ---------------------------------------------------------------------------
@Composable
fun PreviewForegroundScreen(
    state: AppUiState.PreviewForeground,
    onReselect: () -> Unit,
    onCancel: () -> Unit,
    onSelectBackground: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // TOP NAVIGATION (Fullscreen UI)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action: cancel.png inside glassy round boundary (btn-press style)
            PressEffectButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.cancel),
                    contentDescription = "Cancel and close",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Center: PROCESSING state badge and shadow line
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "PROCESSING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .background(Color(0xFF6366F1), RoundedCornerShape(2.dp))
                )
            }

            // Right Action: reselect.png inside glassy pill ring
            PressEffectButton(
                onClick = onReselect,
                modifier = Modifier
                    .wrapContentSize()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.reselect),
                    contentDescription = "Reselect photo",
                    modifier = Modifier.size(130.dp, 44.dp)
                )
            }
        }

        // MAIN PREVIEW CANVAS (4/5 Aspect ratio with checkerboard and badge)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 110.dp, bottom = 180.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                    .background(Color(0xFF0F0F0F))
            ) {
                // Checkerboard background for transparency
                CheckerboardBackground(modifier = Modifier.fillMaxSize())

                // Render transparent PNG output
                Image(
                    bitmap = state.transparentBitmap.asImageBitmap(),
                    contentDescription = "Background removed PNG preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentScale = ContentScale.Fit
                )

                // Overlaid PNG Ready Badge (bottom left)
                // px-4 py-2 bg-black/60 backdrop-blur-xl border border-white/10 rounded-full flex items-center gap-2
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_loop")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "green_pulse"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF22C55E).copy(alpha = pulseAlpha), CircleShape)
                    )

                    Text(
                        text = "PNG READY",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // BOTTOM ACTION (select_bg.png)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PressEffectButton(onClick = onSelectBackground) {
                Image(
                    painter = painterResource(id = R.drawable.select_bg),
                    contentDescription = "Pick custom background scenery",
                    modifier = Modifier.size(200.dp, 56.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. COMPOSITED ARTWORK SCREEN
// ---------------------------------------------------------------------------
@Composable
fun CompositedScreen(
    state: AppUiState.Composited,
    onReselectBackground: () -> Unit,
    onSave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // TOP NAVIGATION (Match Preview Screen persistent layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action Spacer to balance (We keep the clean center and right)
            Box(modifier = Modifier.size(48.dp))

            // Center: COMPOSITING state badge and shadow line
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "COMPOSITING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .background(Color(0xFF6366F1), RoundedCornerShape(2.dp))
                )
            }

            // Right Action Spacer
            Box(modifier = Modifier.size(48.dp))
        }

        // MAIN ARTWORK PREVIEW CANVAS (4/5 Aspect ratio with glassmorphic borders)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 110.dp, bottom = 180.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                    .background(Color(0xFF111111))
            ) {
                Image(
                    bitmap = state.compositedBitmap.asImageBitmap(),
                    contentDescription = "Composited artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Overlaid COMPOSITED ready badge (bottom left)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_loop_composited")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "green_pulse_composited"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981).copy(alpha = pulseAlpha), CircleShape)
                    )

                    Text(
                        text = "READY",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // BOTTOM BAR CONTROLS (reselect_bg.png AND save.png side by side)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // reselect_bg.png
            PressEffectButton(
                onClick = onReselectBackground,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.reselect_bg),
                    contentDescription = "Change background image",
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }

            // save.png
            PressEffectButton(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.save),
                    contentDescription = "Save composite to pictures gallery",
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. SUCCESS SCREEN
// ---------------------------------------------------------------------------
@Composable
fun SuccessScreen(
    uri: Uri?,
    message: String,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        AnimatedGradientFallback()

        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111).copy(alpha = 0.9f)),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color(0xFF10B981), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Muvaffaqiyatli saqlandi!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "$message\n\nRasm galereyangizdagi\nPictures/Custom BG/\npapkasiga joylandi.",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Yangi rasm tayyorlash", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. ERROR SCREEN
// ---------------------------------------------------------------------------
@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        AnimatedGradientFallback()

        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111).copy(alpha = 0.9f)),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error notification icon",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Xatolik Yuz Berdi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = Color(0xFFFDA4AF),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Text("Yopish", fontSize = 14.sp)
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Qayta urinish", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 7. COMPOSABLE HELPERS (BUTTONS, CHECKERBOARDS, VIDEO)
// ---------------------------------------------------------------------------
@Composable
fun PressEffectButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_effect_scaling"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default overlay ripple, using scale
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val squareSize = 10.dp.toPx()
        val numCols = (size.width / squareSize).toInt() + 1
        val numRows = (size.height / squareSize).toInt() + 1
        for (i in 0 until numCols) {
            for (j in 0 until numRows) {
                val color = if ((i + j) % 2 == 0) Color(0xFF222222) else Color(0xFF111111)
                drawRect(
                    color = color,
                    topLeft = Offset(i * squareSize, j * squareSize),
                    size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
                )
            }
        }
    }
}

@Composable
fun VideoBackgroundLoop(context: Context, modifier: Modifier = Modifier) {
    var hasPlaybackFailed by remember { mutableStateOf(false) }

    if (!hasPlaybackFailed) {
        AndroidView(
            factory = { ctx ->
                try {
                    VideoView(ctx).apply {
                        val path = "android.resource://${ctx.packageName}/${R.raw.back}"
                        setVideoPath(path)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f) // Mute audio completely
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            hasPlaybackFailed = true
                            true // Error handled
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                } catch (e: Exception) {
                    hasPlaybackFailed = true
                    View(ctx) // Return transparent empty view
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }

    if (hasPlaybackFailed) {
        AnimatedGradientFallback(modifier)
    }
}

@Composable
fun AnimatedGradientFallback(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient_fallback_loop")

    val xOffset1 by infiniteTransition.animateFloat(
        initialValue = -250f,
        targetValue = 1350f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radial_fallback_x1"
    )
    val yOffset1 by infiniteTransition.animateFloat(
        initialValue = -250f,
        targetValue = 2050f,
        animationSpec = infiniteRepeatable(
            animation = tween(19000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radial_fallback_y1"
    )

    val xOffset2 by infiniteTransition.animateFloat(
        initialValue = 1350f,
        targetValue = -250f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radial_fallback_x2"
    )
    val yOffset2 by infiniteTransition.animateFloat(
        initialValue = 250f,
        targetValue = 1850f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radial_fallback_y2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF040711)) // Immersive deep blue canvas back
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Soft Radial spot lights
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2563EB).copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(xOffset1, yOffset1),
                    radius = 900f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(xOffset2, yOffset2),
                    radius = 1100f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFDB2777).copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = 1300f
                )
            )
        }
    }
}
