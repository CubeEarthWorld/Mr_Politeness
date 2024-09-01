import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.xignal.politeness.LLMModel
import com.xignal.politeness.R
import com.xignal.politeness.ui.theme.Orange
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(llmModel: LLMModel = viewModel()) {
    val homeScreenState = remember { HomeScreenState() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val copied = stringResource(id = R.string.copied_toast)//コピー時に表示するテキスト

    // 初回表示時にインタースティシャル広告を読み込む
    LaunchedEffect(Unit) {
        loadInterstitialAd(context, AdRequest.Builder().build()) { interstitialAd ->
            homeScreenState.mInterstitialAd = interstitialAd
        }
    }

    Scaffold(
        topBar = { HomeTopBar(onMenuClick = { homeScreenState.showPopup = true }) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .imePadding(), // キーボード表示時にパディングを追加
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextFields(
                    inputText = homeScreenState.inputText,
                    outputText = homeScreenState.outputText,
                    onInputTextChange = { homeScreenState.inputText = it },
                    onClearInput = { homeScreenState.inputText = "" },
                    onPasteInput = { homeScreenState.inputText += clipboardManager.getText().toString() },
                    onCopyOutput = {
                        clipboardManager.setText(AnnotatedString(homeScreenState.outputText))
                        coroutineScope.launch {
                            android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                ExecutionControls(
                    isHighAccuracyMode = homeScreenState.isHighAccuracyMode,
                    showProgress = homeScreenState.showProgress,
                    onHighAccuracyModeChange = { homeScreenState.isHighAccuracyMode = it },
                    onExecute = {
                        homeScreenState.showProgress = true
                        executePoliteTextGeneration(homeScreenState, context, llmModel)
                    }
                )
                // 広告バナーの表示
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            adUnitId = ""
                            setAdSize(AdSize.BANNER)
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
            if (homeScreenState.showPopup) {
                PopupDialog(onDismiss = { homeScreenState.showPopup = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(onMenuClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // トップバーの設定
        CenterAlignedTopAppBar(
            title = { Text(stringResource(id = R.string.app_name)) },
            actions = {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_menu),
                        contentDescription = "Menu"
                    )
                }
            }
        )
    }
}

@Composable
fun TextFields(
    inputText: String,
    outputText: String,
    onInputTextChange: (String) -> Unit,
    onClearInput: () -> Unit,
    onPasteInput: () -> Unit,
    onCopyOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 入力テキストフィールド
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChange,
            label = {
                Text(
                    if (inputText.isNotEmpty()) stringResource(
                        id = R.string.input_label,
                        inputText.length
                    ) else stringResource(id = R.string.input_label_no_input)
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        // 入力フィールドのクリアとペーストボタン
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClearInput, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_clear),
                    contentDescription = stringResource(id = R.string.clear_description),
                    tint = Color.Gray
                )
            }
            IconButton(onClick = onPasteInput, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_paste),
                    contentDescription = stringResource(id = R.string.paste_description),
                    tint = Color.Gray
                )
            }
        }
        // 出力テキストフィールド
        OutlinedTextField(
            value = outputText,
            onValueChange = {},
            label = {
                Text(
                    if (outputText.isNotEmpty()) stringResource(
                        id = R.string.output_label,
                        outputText.length
                    ) else stringResource(id = R.string.output_label_no_input)
                )
            },
            readOnly = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        // 出力フィールドのコピーとミスについての注意書き
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(id = R.string.can_mistakes),
                color = Color.Gray,
                modifier = Modifier.width(300.dp),
                fontSize = 12.sp
            )
            IconButton(onClick = onCopyOutput, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_copy),
                    contentDescription = stringResource(id = R.string.copy_description),
                    tint = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ExecutionControls(
    isHighAccuracyMode: Boolean,
    showProgress: Boolean,
    onHighAccuracyModeChange: (Boolean) -> Unit,
    onExecute: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 高精度モードのチェックボックス
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isHighAccuracyMode,
                onCheckedChange = onHighAccuracyModeChange
            )
            Text(stringResource(id = R.string.high_accuracy_mode))
        }
        // 実行ボタン
        Button(
            onClick = onExecute,
            colors = ButtonDefaults.buttonColors(containerColor = Orange),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text(stringResource(id = R.string.execute_button))
            }
        }
    }
}

@Composable
fun PopupDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.onPrimary
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.popup_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.popup_text),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                // 閉じるボタン
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(id = R.string.close_button))
                }
            }
        }
    }
}

// インタースティシャル広告を読み込む関数
fun loadInterstitialAd(context: Context, adRequest: AdRequest, onAdLoaded: (InterstitialAd?) -> Unit) {
    InterstitialAd.load(
        context,
        "",
        adRequest,
        object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                onAdLoaded(interstitialAd)
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                onAdLoaded(null)
            }
        }
    )
}

// 入力テキストを丁寧な表現に変換する関数
fun executePoliteTextGeneration(
    state: HomeScreenState,
    context: Context,
    llmModel: LLMModel
) {
    if (state.isHighAccuracyMode && state.mInterstitialAd != null) {
        state.mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            // 広告が閉じられたときの処理
            override fun onAdDismissedFullScreenContent() {
                generatePoliteText(llmModel, state)
                loadInterstitialAd(context, AdRequest.Builder().build()) { interstitialAd ->
                    state.mInterstitialAd = interstitialAd
                }
            }

            // 広告の表示に失敗したときの処理
            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                generatePoliteText(llmModel, state)
                loadInterstitialAd(context, AdRequest.Builder().build()) { interstitialAd ->
                    state.mInterstitialAd = interstitialAd
                }
            }

            override fun onAdShowedFullScreenContent() {}
        }
        state.mInterstitialAd?.show(context as Activity)
    } else {
        generatePoliteText(llmModel, state)
    }
}

fun generatePoliteText(llmModel: LLMModel, state: HomeScreenState) {
    llmModel.generatePolite(state.inputText, state.isHighAccuracyMode) { result ->
        state.outputText = result
        state.showProgress = false
    }
}

class HomeScreenState {
    var inputText by mutableStateOf("")
    var outputText by mutableStateOf("")
    var showPopup by mutableStateOf(false)
    var isHighAccuracyMode by mutableStateOf(false)
    var showProgress by mutableStateOf(false)
    var mInterstitialAd: InterstitialAd? by mutableStateOf(null)
}