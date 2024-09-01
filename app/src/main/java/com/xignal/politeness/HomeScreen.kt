package com.xignal.politeness

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
import com.xignal.politeness.ui.theme.Orange
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(llmModel: LLMModel = viewModel()) {
    // 入力テキストの状態を保持する変数
    var inputText by remember { mutableStateOf("") }
    // 出力テキストの状態を保持する変数
    var outputText by remember { mutableStateOf("") }
    // ポップアップ表示の状態を保持する変数
    var showPopup by remember { mutableStateOf(false) }
    // 高精度モードの状態を保持する変数
    var isHighAccuracyMode by remember { mutableStateOf(false) }
    // プログレス表示の状態を保持する変数
    var showProgress by remember { mutableStateOf(false) }
    // クリップボードマネージャーの取得
    val clipboardManager = LocalClipboardManager.current
    // コンテキストの取得
    val context = LocalContext.current
    // コルーチンスコープの取得
    val coroutineScope = rememberCoroutineScope()
    // コピー完了メッセージの取得
    val copied = stringResource(id = R.string.copied_toast)
    // インタースティシャル広告の状態を保持する変数
    var mInterstitialAd: InterstitialAd? by remember { mutableStateOf(null) }
    // 広告リクエストの作成
    val adRequest = AdRequest.Builder().build()

    // 初回表示時にインタースティシャル広告を読み込む
    LaunchedEffect(Unit) {
        loadInterstitialAd(context, adRequest) { interstitialAd ->
            mInterstitialAd = interstitialAd
        }
    }

    Scaffold(
        // トップバーの設定
        topBar = {
            HomeTopBar { showPopup = true }
        },
    ) { innerPadding ->
        // メインコンテンツの表示
        HomeContent(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            outputText = outputText,
            onClearInput = { inputText = "" },
            onPasteInput = { inputText += clipboardManager.getText().toString() },
            onCopyOutput = {
                clipboardManager.setText(AnnotatedString(outputText))
                coroutineScope.launch {
                    android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            isHighAccuracyMode = isHighAccuracyMode,
            onHighAccuracyModeChange = { isHighAccuracyMode = it },
            showProgress = showProgress,
            onExecute = {
                showProgress = true
                if (isHighAccuracyMode && mInterstitialAd != null) {
                    mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        // 広告が閉じられたときの処理
                        override fun onAdDismissedFullScreenContent() {
                            generatePoliteText(llmModel, inputText, isHighAccuracyMode) { result ->
                                outputText = result
                                showProgress = false
                            }
                            loadInterstitialAd(context, adRequest) { interstitialAd ->
                                mInterstitialAd = interstitialAd
                            }
                            mInterstitialAd = null
                        }

                        // 広告の表示に失敗したときの処理
                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            generatePoliteText(llmModel, inputText, isHighAccuracyMode) { result ->
                                outputText = result
                                showProgress = false
                            }
                            loadInterstitialAd(context, adRequest) { interstitialAd ->
                                mInterstitialAd = interstitialAd
                            }
                            mInterstitialAd = null
                        }

                        override fun onAdShowedFullScreenContent() {}
                    }
                    mInterstitialAd?.show(context as Activity)
                } else {
                    generatePoliteText(llmModel, inputText, isHighAccuracyMode) { result ->
                        outputText = result
                        showProgress = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        )
        // ポップアップが表示されている場合の処理
        if (showPopup) {
            PopupDialog(onDismiss = { showPopup = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(onMenuClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 広告バナーの表示
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    adUnitId = "バナー広告のID"
                    setAdSize(AdSize.BANNER)
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
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
fun HomeContent(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    outputText: String,
    onClearInput: () -> Unit,
    onPasteInput: () -> Unit,
    onCopyOutput: () -> Unit,
    isHighAccuracyMode: Boolean,
    onHighAccuracyModeChange: (Boolean) -> Unit,
    showProgress: Boolean,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
        Spacer(modifier = Modifier.height(4.dp))
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
                    .padding(16.dp)
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
        "インタースティシャル広告のID",
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
fun generatePoliteText(
    llmModel: LLMModel,
    inputText: String,
    isHighAccuracyMode: Boolean,
    onResult: (String) -> Unit
) {
    llmModel.generatePolite(inputText, isHighAccuracyMode) { result ->
        onResult(result)
    }
}