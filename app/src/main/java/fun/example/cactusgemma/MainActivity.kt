package fun.example.cactusgemma

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.cactus.CactusCompletionParams
import com.cactus.CactusContextInitializer
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var lm: CactusLM

    private lateinit var statusView: TextView
    private lateinit var chatView: TextView
    private lateinit var inputView: EditText
    private lateinit var loadButton: Button
    private lateinit var sendButton: Button
    private lateinit var scrollView: ScrollView

    private var loaded = false
    private val history = mutableListOf<ChatMessage>()

    // Cactus SDK uses this canonical model name and downloads the Cactus-Compute pre-converted weights.
    private val modelName = "google/gemma-4-E2B-it"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CactusContextInitializer.initialize(this)
        lm = CactusLM()
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 24)
        }

        val title = TextView(this).apply {
            text = "Cactus Gemma 4 Chat"
            textSize = 22f
            setPadding(0, 0, 0, 12)
        }
        root.addView(title)

        statusView = TextView(this).apply {
            text = "未加载模型。点击下面按钮后会从 Hugging Face/Cactus-Compute 下载 Gemma 4 E2B INT4 权重。第一次下载约数 GB，请用 Wi-Fi。"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        root.addView(statusView)

        loadButton = Button(this).apply {
            text = "下载并加载 Gemma 4 E2B"
            setOnClickListener { loadModel() }
        }
        root.addView(loadButton)

        scrollView = ScrollView(this)
        chatView = TextView(this).apply {
            text = ""
            textSize = 16f
            setPadding(0, 16, 0, 16)
        }
        scrollView.addView(chatView)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        inputView = EditText(this).apply {
            hint = "输入消息，例如：用中文介绍一下你自己"
            minLines = 2
            maxLines = 5
        }
        root.addView(inputView)

        sendButton = Button(this).apply {
            text = "发送"
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        root.addView(sendButton)

        setContentView(root)
    }

    private fun setBusy(busy: Boolean) {
        loadButton.isEnabled = !busy
        sendButton.isEnabled = loaded && !busy
        inputView.isEnabled = !busy
    }

    private fun loadModel() {
        scope.launch {
            try {
                setBusy(true)
                statusView.text = "正在下载 $modelName。第一次可能很久，后续会直接使用本地缓存。"

                withContext(Dispatchers.IO) {
                    lm.downloadModel(modelName)
                }

                statusView.text = "下载完成，正在初始化本地推理引擎..."

                withContext(Dispatchers.IO) {
                    lm.initializeModel(
                        CactusInitParams(
                            model = modelName,
                            contextSize = 1024
                        )
                    )
                }

                loaded = true
                statusView.text = "已加载 $modelName。现在可以离线聊天。"
                sendButton.isEnabled = true
            } catch (t: Throwable) {
                loaded = false
                statusView.text = "加载失败：${t.message ?: t.javaClass.simpleName}\n建议先确认网络、手机剩余空间和 RAM；也可以把 modelName 换成 google/gemma-3-270m-it 做小模型测试。"
            } finally {
                setBusy(false)
            }
        }
    }

    private fun sendMessage() {
        val text = inputView.text.toString().trim()
        if (text.isEmpty()) return

        hideKeyboard()
        inputView.setText("")
        appendChat("\n你：$text\n")
        history.add(ChatMessage(content = text, role = "user"))

        scope.launch {
            try {
                setBusy(true)
                statusView.text = "Gemma 正在本地生成..."

                val assistantPrefix = "Gemma："
                appendChat(assistantPrefix)
                val currentAnswer = StringBuilder()

                val messages = listOf(
                    ChatMessage(
                        content = "You are a concise, helpful bilingual assistant. Prefer Chinese unless the user asks otherwise.",
                        role = "system"
                    )
                ) + history

                val result = withContext(Dispatchers.IO) {
                    lm.generateCompletion(
                        messages = messages,
                        params = CactusCompletionParams(
                            maxTokens = 256,
                            temperature = 0.7
                        ),
                        onToken = { token, _ ->
                            currentAnswer.append(token)
                            runOnUiThread {
                                rebuildChatWithStreamingAnswer(currentAnswer.toString())
                            }
                        }
                    )
                }

                val finalText = result?.response?.ifBlank { currentAnswer.toString() }
                    ?: currentAnswer.toString().ifBlank { "[无输出]" }

                history.add(ChatMessage(content = finalText, role = "assistant"))
                rebuildChat()
                statusView.text = "完成。"
            } catch (t: Throwable) {
                appendChat("\n[错误] ${t.message ?: t.javaClass.simpleName}\n")
                statusView.text = "生成失败。"
            } finally {
                setBusy(false)
            }
        }
    }

    private fun appendChat(text: String) {
        chatView.append(text)
        scrollToBottom()
    }

    private fun rebuildChat() {
        val sb = StringBuilder()
        history.forEach { msg ->
            when (msg.role) {
                "user" -> sb.append("\n你：").append(msg.content).append("\n")
                "assistant" -> sb.append("Gemma：").append(msg.content).append("\n")
            }
        }
        chatView.text = sb.toString()
        scrollToBottom()
    }

    private fun rebuildChatWithStreamingAnswer(partial: String) {
        val sb = StringBuilder()
        history.forEach { msg ->
            when (msg.role) {
                "user" -> sb.append("\n你：").append(msg.content).append("\n")
                "assistant" -> sb.append("Gemma：").append(msg.content).append("\n")
            }
        }
        sb.append("Gemma：").append(partial)
        chatView.text = sb.toString()
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputView.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::lm.isInitialized) lm.unload()
        } catch (_: Throwable) {
        }
        scope.cancel()
    }
}
