package com.ppp65isking.box.data.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * The downloaded on-device model for LiteRT-LM.
 *
 * Why Qwen 1.5B and not the 0.5B the research named: the
 * litert-community Qwen 2.5 0.5B repo has NO .litertlm bundle — only
 * MediaPipe .task/.tflite files, which LiteRT-LM cannot load. The
 * smallest Qwen .litertlm bundle Google publishes is the 1.5B int8
 * one (q8, 4096-token context, not gated, Apache-2.0), ~1.6 GB. The
 * UI says ~1.6 GB honestly; the 0.5B figure in the research was the
 * .task file, not a .litertlm bundle.
 *
 * The download is verified by exact size AND the file's SHA-256
 * (the Hugging Face LFS oid for this file). If Hugging Face ever
 * re-uploads the file, the SHA changes and the download fails with a
 * plain message instead of loading a file it can't trust.
 */
class LiteRtModelManager(
    private val context: Context,
    private val okHttp: OkHttpClient,
) {
    private val _state = MutableStateFlow<ModelState>(
        if (isDownloaded()) ModelState.Ready else ModelState.NotDownloaded,
    )
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun modelFile(): File = File(context.filesDir, "ai/qwen-1.5b.litertlm")

    fun isDownloaded(): Boolean {
        val file = modelFile()
        return file.exists() && file.length() == MODEL_SIZE_BYTES
    }

    fun delete() {
        modelFile().delete()
        _state.value = ModelState.NotDownloaded
    }

    /** Wi-Fi-only download with progress, size + SHA-256 verification.
     *  Safe to call from a UI coroutine. */
    suspend fun download() {
        if (_state.value is ModelState.Downloading) return
        if (!isWifi()) {
            _state.value = ModelState.Failed(
                "Connect to Wi-Fi to download the model (~1.6 GB).",
            )
            return
        }
        val freeBytes = StatFs(context.filesDir.path).availableBytes
        if (freeBytes < MODEL_SIZE_BYTES + 200_000_000L) {
            _state.value = ModelState.Failed(
                "Not enough free storage — the model needs ~1.6 GB.",
            )
            return
        }
        _state.value = ModelState.Downloading(0, MODEL_SIZE_BYTES)
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(MODEL_URL).build()
                okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download failed (${response.code}).")
                    }
                    val body = response.body
                        ?: throw IOException("Download failed (empty reply).")
                    val dir = modelFile().parentFile ?: context.filesDir
                    dir.mkdirs()
                    val tmp = File(dir, "qwen-1.5b.litertlm.tmp")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var read: Int
                            var total = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                total += read
                                _state.value =
                                    ModelState.Downloading(total, MODEL_SIZE_BYTES)
                            }
                        }
                    }
                    if (tmp.length() != MODEL_SIZE_BYTES) {
                        tmp.delete()
                        throw IOException("Download didn't finish — try again.")
                    }
                    if (sha256(tmp) != MODEL_SHA256) {
                        tmp.delete()
                        throw IOException(
                            "Downloaded file didn't match — try again.",
                        )
                    }
                    tmp.renameTo(modelFile())
                }
            }
            _state.value = ModelState.Ready
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: "Download failed.")
        }
    }

    private fun isWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        const val MODEL_SIZE_BYTES = 1_597_922_856L
        const val MODEL_SHA256 =
            "c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e"
        const val MODEL_LABEL = "Qwen 2.5 1.5B (on-device model, ~1.6 GB)"
    }
}

/**
 * LiteRT-LM engine — the downloaded Qwen model, running in Box's own
 * process. Unlike Nano it CAN run in the background, but loading a
 * ~1.6 GB model costs time and ~2 GB of RAM, so Phase 1 only runs it
 * on tap, in the foreground, like Nano. The engine is initialized
 * once and kept for the life of the app.
 */
class LiteRtEngine(
    private val context: Context,
    private val modelManager: LiteRtModelManager,
) : AiEngine {
    override val leg = AiLeg.LITERT
    override val label = "Qwen on-device"

    private var engine: Engine? = null

    /** The native libs only ship for arm64-v8a and x86_64. */
    private fun supportedAbi(): Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    private fun enoughMemory(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
            as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.availMem >= 1_000_000_000L
    }

    override suspend fun available(): Boolean =
        supportedAbi() && modelManager.isDownloaded() && enoughMemory()

    private suspend fun getEngine(): Engine? = withContext(Dispatchers.Default) {
        engine?.let { return@withContext it }
        try {
            val config = EngineConfig(
                modelPath = modelManager.modelFile().absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path,
            )
            val created = Engine(config)
            created.initialize()
            engine = created
            created
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun run(task: AiTask): String? {
        val prompt = task.toPrompt() ?: return null
        if (!available()) return null
        val eng = getEngine() ?: return null
        return try {
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    "You are a helpful assistant. Answer briefly and plainly.",
                ),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.3),
            )
            val text = withContext(Dispatchers.Default) {
                eng.createConversation(config).use { conversation ->
                    conversation.sendMessageAsync(prompt)
                        .map { it.toString() }
                        .toList()
                        .joinToString("")
                }
            }
            text.trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
