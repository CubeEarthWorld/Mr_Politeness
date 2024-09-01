package com.xignal.politeness

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class LLMModel(application: Application) : AndroidViewModel(application) {
    // 禁止文字のセット
    private val forbiddenChars = setOf('@', '#', '$', '%', '^', '&', '*', '{', '}', '/', '>', '<', '|', '\\')
    // 生成設定の定義
    private val generationConfig = generationConfig {
        temperature = 0.3f
        maxOutputTokens = 2048
        responseMimeType = "application/json"
    }
    // 安全設定の定義
    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    fun generatePolite(
        input: String,
        isHighAccuracyMode: Boolean,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 禁止文字をフィルタリング
                val filteredInput = input.filterNot { it in forbiddenChars }
                // プロンプトの作成
                val prompt = buildPrompt(filteredInput)
                // モデル名の選択
                val modelName = if (isHighAccuracyMode) "gemini-1.5-pro" else "gemini-1.5-flash"
                // 生成モデルの作成
                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = BuildConfig.API_KEY,
                    generationConfig = generationConfig,
                    safetySettings = safetySettings
                )
                // コンテンツ生成の実行
                val response = generativeModel.generateContent(prompt)
                // JSONレスポンスの解析
                val jsonResponse = response.text ?: throw Exception("No response generated")
                val jsonObject = JSONObject(jsonResponse)
                val output = jsonObject.getString("output(Corrected text)")
                onResult(output)
            } catch (e: Exception) {
                onResult("Error: ${e.message}")
            }
        }
    }

    private fun buildPrompt(input: String) = """
        Please rewrite the following sentences into polite expressions that can be used appropriately in business situations. Maintain the meaning of the original sentence as much as possible. Determine the language of the input text. Only make corrections. Respond in the same language as the input text. If the sentence is already polite, leave it as is. Provide only one response.

        Use this JSON schema:
        PoliteResponse = {'output(Corrected text)': string}
        Return: PoliteResponse

        Input: $input
    """.trimIndent()
}