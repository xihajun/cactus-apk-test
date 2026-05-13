package com.example.litertlmchat

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
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null

    private lateinit var statusView: TextView
    private lateinit var chatView: TextView
    private lateinit var inputView: EditText
    private lateinit var sendButton: Button
    private lateinit var scrollView: ScrollView

    private var loaded = false
    private val chatHistory = mutableListOf<Pair<String, String>>() // role -> content

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        // Auto-initialize on launch
        initializeEngine()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 24)
        }

        val title = TextView(this).apply {
            text = "LiteRT-LM Gemma 4 Chat"
            textSize = 22f
            setPadding(0, 0, 0, 4)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Powered by Google AI Edge LiteRT-LM · GPU-accelerated on-device inference"
            textSize = 12f
            setPadding(0, 0, 0, 12)
        }
        root.addView(subtitle)

        statusView = TextView(this).apply {
            text = "Initializing LiteRT-LM engine..."
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        root.addView(statusView)

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
            hint = "Type a message..."
            minLines = 2
            maxLines = 5
        }
        root.addView(inputView)

        sendButton = Button(this).apply {
            text = "Send"
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        root.addView(sendButton)

        setContentView(root)
    }

    private fun initializeEngine() {
        scope.launch {
            try {
                // Model file must be in the app's files dir or external storage.
                // Copy from assets or download from HuggingFace first.
                val modelPath = getModelPath()
                if (modelPath == null) {
                    statusView.text = "No .litertlm model found.\n\n" +
                        "Place your model file in:\n" +
                        "${filesDir.absolutePath}/model.litertlm\n\n" +
                        "Or download from HuggingFace using the download button below."
                    addDownloadButton()
                    return@launch
                }

                statusView.text = "Loading model into memory (~1.5 GB)...\nThis takes 5-20 seconds."

                withContext(Dispatchers.IO) {
                    val config = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        cacheDir = cacheDir.absolutePath
                    )
                    engine = Engine(config).also { it.initialize() }

                    val convConfig = ConversationConfig(
                        systemInstruction = com.google.ai.edge.litertlm.Contents.of(
                            "You are a concise, helpful bilingual assistant. Prefer Chinese unless the user asks otherwise."
                        ),
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
                    )
                    conversation = engine!!.createConversation(convConfig)
                }

                loaded = true
                statusView.text = "Ready. Model loaded with GPU acceleration."
                sendButton.isEnabled = true

            } catch (t: Throwable) {
                loaded = false
                statusView.text = "Failed to load: ${t.message}\n\n" +
                    "Make sure the .litertlm file is at:\n" +
                    "${filesDir.absolutePath}/model.litertlm"
                addDownloadButton()
            }
        }
    }

    private fun getModelPath(): String? {
        // Check app internal storage
        val internal = java.io.File(filesDir, "model.litertlm")
        if (internal.exists() && internal.length() > 1_000_000) return internal.absolutePath

        // Check external files dir
        val external = java.io.File(getExternalFilesDir(null), "model.litertlm")
        if (external.exists() && external.length() > 1_000_000) return external.absolutePath

        return null
    }

    private fun addDownloadButton() {
        // Add a download section at the bottom of the status area
        val root = (statusView.parent as? LinearLayout) ?: return

        val existing = root.findViewWithTag<Button>("download_btn")
        if (existing != null) return

        val dlButton = Button(this).apply {
            tag = "download_btn"
            text = "Download model from HuggingFace"
            setOnClickListener { /* launch download intent or show dialog */ }
        }
        root.addView(dlButton, root.indexOfChild(statusView) + 1)
    }

    private fun sendMessage() {
        val text = inputView.text.toString().trim()
        if (text.isEmpty() || !loaded) return

        hideKeyboard()
        inputView.setText("")
        appendChat("\nYou: $text\n")
        chatHistory.add("user" to text)

        scope.launch {
            try {
                sendButton.isEnabled = false
                statusView.text = "Generating..."

                val assistantPrefix = "Gemma: "
                appendChat(assistantPrefix)
                val currentAnswer = StringBuilder()

                withContext(Dispatchers.IO) {
                    conversation!!.sendMessageAsync(text).collect { token ->
                        currentAnswer.append(token)
                        runOnUiThread {
                            rebuildChatWithStreamingAnswer(currentAnswer.toString())
                        }
                    }
                }

                val finalText = currentAnswer.toString().ifBlank { "[no output]" }
                chatHistory.add("assistant" to finalText)
                rebuildChat()
                statusView.text = "Ready."
            } catch (t: Throwable) {
                appendChat("\n[Error] ${t.message}\n")
                statusView.text = "Generation failed."
            } finally {
                sendButton.isEnabled = true
            }
        }
    }

    private fun appendChat(text: String) {
        chatView.append(text)
        scrollToBottom()
    }

    private fun rebuildChat() {
        val sb = StringBuilder()
        chatHistory.forEach { (role, content) ->
            when (role) {
                "user" -> sb.append("\nYou: $content\n")
                "assistant" -> sb.append("Gemma: $content\n")
            }
        }
        chatView.text = sb.toString()
        scrollToBottom()
    }

    private fun rebuildChatWithStreamingAnswer(partial: String) {
        val sb = StringBuilder()
        chatHistory.forEach { (role, content) ->
            when (role) {
                "user" -> sb.append("\nYou: $content\n")
                "assistant" -> sb.append("Gemma: $content\n")
            }
        }
        sb.append("Gemma: $partial")
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
            conversation?.close()
        } catch (_: Throwable) {}
        try {
            engine?.close()
        } catch (_: Throwable) {}
        scope.cancel()
    }
}
