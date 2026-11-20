package com.nielit.tomatoai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tvResult: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnGallery: Button
    private lateinit var btnLanguage: Button
    private lateinit var btnConfidenceDetails: TextView
    private lateinit var tvConfidenceValue: TextView
    private lateinit var llThinkingState: LinearLayout

    private lateinit var tflite: Interpreter
    private lateinit var labels: List<String>
    private lateinit var execuTorchRuntime: ExecuTorchRuntime

    // Camera launcher for capturing the image
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            runInference(bitmap)
        } else {
            Toast.makeText(this, getString(R.string.no_image_captured), Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery launcher for picking an image from the gallery
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bitmap = uriToBitmap(uri)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                runInference(bitmap)
            } else {
                Toast.makeText(this, getString(R.string.failed_gallery_image), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for requesting CAMERA permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Handle the system bars (status bar/navigation bar)
        try {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        } catch (_: Exception) {
            // Ignore if R.id.main is not found in layout
        }

        // Initialize UI elements
        imageView = findViewById(R.id.imageView)
        tvResult = findViewById(R.id.tvResult)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        btnCapture = findViewById(R.id.btnCapture)
        btnGallery = findViewById(R.id.btnGallery)
        btnLanguage = findViewById(R.id.btnLanguage)
        btnConfidenceDetails = findViewById(R.id.btnConfidenceDetails)
        tvConfidenceValue = findViewById(R.id.tvConfidenceValue)
        llThinkingState = findViewById(R.id.llThinkingState)

        // Load AI Model and Labels from assets
        loadModel()

        // Initialize ExecuTorch LLM runtime synchronously
        try {
            execuTorchRuntime = ExecuTorchRuntime.getInstance()
            execuTorchRuntime.loadModel(applicationContext, "llama_final.pte")
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        btnCapture.setOnClickListener {
            openCamera()
        }

        btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        btnLanguage.setOnClickListener {
            toggleLanguage()
        }

        btnConfidenceDetails.setOnClickListener {
            if (tvConfidenceValue.visibility == View.VISIBLE) {
                tvConfidenceValue.visibility = View.GONE
                btnConfidenceDetails.text = getString(R.string.btn_view_confidence)
            } else {
                tvConfidenceValue.visibility = View.VISIBLE
                btnConfidenceDetails.text = getString(R.string.btn_hide_confidence)
            }
        }

        val btnTabAi = findViewById<Button>(R.id.btnTabAi)
        val btnTabAbout = findViewById<Button>(R.id.btnTabAbout)
        val layoutAiContent = findViewById<android.widget.ScrollView>(R.id.layoutAiContent)
        val layoutAboutContent = findViewById<android.widget.ScrollView>(R.id.layoutAboutContent)

        btnTabAi.setOnClickListener {
            layoutAiContent.visibility = View.VISIBLE
            layoutAboutContent.visibility = View.GONE
            btnTabAi.setTextColor(Color.parseColor("#2E7D32"))
            btnTabAbout.setTextColor(Color.parseColor("#555555"))
        }

        btnTabAbout.setOnClickListener {
            layoutAiContent.visibility = View.GONE
            layoutAboutContent.visibility = View.VISIBLE
            btnTabAbout.setTextColor(Color.parseColor("#2E7D32"))
            btnTabAi.setTextColor(Color.parseColor("#555555"))
        }
    }

    private fun toggleLanguage() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = currentLocales.toLanguageTags()
        val isHindi = currentTag.contains("hi") || (!currentTag.contains("en") && resources.configuration.locales[0].language == "hi")
        val targetLang = if (isHindi) "en" else "hi"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(targetLang))
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            cameraLauncher.launch(null)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.unable_camera, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModel() {
        try {
            val modelName = "tomato_model.tflite"
            // Load .tflite model file from assets
            val modelBytes = assets.open(modelName).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(modelBytes)
                rewind()
            }

            tflite = Interpreter(buffer)

            val labelName = "labels.txt"
            // Load labels.txt file from assets
            labels = assets.open(labelName).bufferedReader().use { it.readLines() }

            android.util.Log.i("MODEL_DEBUG", "Successfully loaded model from assets:")
            android.util.Log.i("MODEL_DEBUG", " - Model File Name: $modelName")
            android.util.Log.i("MODEL_DEBUG", " - Model File Size: ${modelBytes.size} bytes (${modelBytes.size / (1024 * 1024)} MB)")
            android.util.Log.i("MODEL_DEBUG", " - Labels File Name: $labelName")
            android.util.Log.i("MODEL_DEBUG", " - Loaded Labels Count: ${labels.size}")
            android.util.Log.i("MODEL_DEBUG", " - Labels List: $labels")

            Toast.makeText(this, getString(R.string.model_loaded), Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            android.util.Log.e("MODEL_DEBUG", "Failed to load model or labels: ${t.message}", t)
            Toast.makeText(this, getString(R.string.model_error, t.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun generateAdvicePrompt(diseaseName: String): String {
        val cleanName = diseaseName.replace("Tomato___", "").replace("_", " ").trim()
        val isHindi = resources.configuration.locales[0].language == "hi"
        return if (isHindi) {
            "आप एक मित्रवत और अनुभवी गांव के बुजुर्ग और कृषि विशेषज्ञ हैं। टमाटर के पौधे में '$cleanName' बीमारी पाई गई है। हिंदी और अंग्रेजी दोनों में विस्तृत सलाह दें। अपना उत्तर 'मेरे प्रिय किसान भाई...' जैसे मित्रवत संबोधन से शुरू करें और एक कहानी सुनाने की शैली में सलाह समझाएं।"
        } else {
            "You are a friendly and experienced village elder and agriculture expert. The plant has $cleanName. Provide a detailed recommendation in both English and Hindi. Start your response with a friendly greeting like \"My dear farmer...\" and explain the advice in a storytelling, conversational style."
        }
    }

    private fun formatBilingualAdvice(rawAdvice: String): String {
        return rawAdvice
    }

    private fun generateLLMAdviceAsync(diseaseName: String, fallbackRecommendation: String) {
        // Display immediate fast local recommendation
        tvRecommendation.text = formatBilingualAdvice(fallbackRecommendation)

        // Show subtle 'AI Thinking...' progress state while ExecuTorch LLM evaluates
        llThinkingState.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!::execuTorchRuntime.isInitialized) {
                    execuTorchRuntime = ExecuTorchRuntime.getInstance()
                }
                val prompt = generateAdvicePrompt(diseaseName)
                val llmOutput = execuTorchRuntime.generateText(prompt)

                withContext(Dispatchers.Main) {
                    llThinkingState.visibility = View.GONE

                    if (llmOutput.isNotBlank()) {
                        // Smooth Alpha Fade-In Animation
                        tvRecommendation.alpha = 0f
                        tvRecommendation.text = formatBilingualAdvice(llmOutput)
                        tvRecommendation.animate()
                            .alpha(1.0f)
                            .setDuration(400)
                            .start()
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    llThinkingState.visibility = View.GONE
                }
            }
        }
    }

    private fun runInference(bitmap: Bitmap) {
        if (!::tflite.isInitialized || !::labels.isInitialized || labels.isEmpty()) {
            Toast.makeText(this, getString(R.string.model_not_loaded), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 1. Force Color Space to ARGB_8888 before resizing to prevent hardware bitmap issues
            val argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            // 2. Pre-process image: Resize to 224x224
            val resizedBitmap = Bitmap.createScaledBitmap(argbBitmap, 224, 224, true)

            // Create ByteBuffer: 1 image * 224 height * 224 width * 3 channels * 4 bytes per float
            val inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(224 * 224)
            resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

            // IMPORTANT: Normalization uses explicit Float casting matching rescale=1./255
            for (pixel in intValues) {
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()

                inputBuffer.putFloat(r / 255.0f)
                inputBuffer.putFloat(g / 255.0f)
                inputBuffer.putFloat(b / 255.0f)
            }

            // 3. Run the TFLite model
            val output = Array(1) { FloatArray(labels.size) }
            tflite.run(inputBuffer, output)

            // 4. Process the results (Smart handling of raw logits vs pre-calculated probabilities)
            val rawOutput = output[0]
            android.util.Log.i("TFLITE_DEBUG", "Raw TFLite output: ${rawOutput.joinToString(", ")}")

            val isLogits = rawOutput.any { it < 0.0f || it > 1.0f }

            val probabilities = if (isLogits) {
                val maxLogit = rawOutput.maxOrNull() ?: 0.0f
                var sumExp = 0.0f
                val probs = FloatArray(rawOutput.size)
                for (i in rawOutput.indices) {
                    probs[i] = kotlin.math.exp(rawOutput[i] - maxLogit)
                    sumExp += probs[i]
                }
                for (i in probs.indices) {
                    probs[i] /= sumExp
                }
                probs
            } else {
                rawOutput
            }
            android.util.Log.i("TFLITE_DEBUG", "Processed probabilities: ${probabilities.joinToString(", ")}")

            var maxIdx = -1
            var maxProb = -1.0f

            for (i in probabilities.indices) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxIdx = i
                }
            }

            if (maxIdx != -1) {
                val maxProbValue = maxProb
                val confidence = maxProbValue * 100.0f
                val diseaseName = labels[maxIdx]
                val localizedName = getLocalizedDiseaseName(diseaseName)

                // Populate technical confidence value and show 'View Confidence' toggle
                tvConfidenceValue.text = getString(R.string.confidence_value_format, confidence)
                tvConfidenceValue.visibility = View.GONE
                btnConfidenceDetails.visibility = View.VISIBLE
                btnConfidenceDetails.text = getString(R.string.btn_view_confidence)

                // --- CONFIDENCE CALIBRATION ZONES ---
                when {
                    // 1. High Confidence Zone (85% to 100%): Strong match
                    maxProbValue >= 0.85f -> {
                        tvResult.text = getString(R.string.result_high_confidence, localizedName)
                        generateLLMAdviceAsync(diseaseName, getRecommendation(diseaseName))
                    }

                    // 2. Moderate Confidence Zone (50% to 84%): Likely match with verification note
                    maxProbValue >= 0.50f -> {
                        tvResult.text = getString(R.string.result_moderate_confidence, localizedName)
                        val fallback = "${getRecommendation(diseaseName)}${getString(R.string.moderate_verify_note)}"
                        generateLLMAdviceAsync(diseaseName, fallback)
                    }

                    // 3. Unsure Zone (35% to 49%): Display disease name with AI advice
                    maxProbValue >= 0.35f -> {
                        tvResult.text = getString(R.string.result_unsure_confidence, localizedName)
                        val fallback = "${getRecommendation(diseaseName)}${getString(R.string.moderate_verify_note)}"
                        generateLLMAdviceAsync(diseaseName, fallback)
                    }

                    // 4. Extremely Low Zone (Below 35%): Do NOT display disease name or run AI
                    else -> {
                        tvResult.text = getString(R.string.unsure_result)
                        tvRecommendation.text = getString(R.string.extremely_low_unable)
                        llThinkingState.visibility = View.GONE
                    }
                }
            } else {
                btnConfidenceDetails.visibility = View.GONE
                tvConfidenceValue.visibility = View.GONE
                llThinkingState.visibility = View.GONE
                tvResult.text = getString(R.string.invalid_prediction)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            llThinkingState.visibility = View.GONE
            Toast.makeText(this, getString(R.string.inference_error, e.localizedMessage ?: e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun getLocalizedDiseaseName(disease: String): String {
        val d = disease.lowercase()
        val isHindi = resources.configuration.locales[0].language == "hi"
        if (!isHindi) {
            return disease.replace("Tomato___", "").replace("_", " ").trim()
        }

        return when {
            d.contains("bacterial") -> "जीवाणु धब्बा (Bacterial Spot)"
            d.contains("early") -> "अर्ली ब्लाइट (अगेती झुलसा)"
            d.contains("late") -> "पछेती झुलसा (Late Blight)"
            d.contains("mold") -> "लीफ मोल्ड (Leaf Mold)"
            d.contains("septoria") -> "सेप्टोरिया धब्बा (Septoria Leaf Spot)"
            d.contains("spider") -> "मकड़ी का प्रकोप (Spider Mites)"
            d.contains("target") -> "टारगेट धब्बा (Target Spot)"
            d.contains("yellow") -> "पत्ती मुड़न रोग (Yellow Leaf Curl)"
            d.contains("mosaic") -> "मोज़ेक वायरस (Mosaic Virus)"
            d.contains("healthy") -> "स्वस्थ पत्ता (Healthy)"
            else -> disease
        }
    }

    private fun getRecommendation(disease: String): String {
        val isHindi = resources.configuration.locales[0].language == "hi"
        return if (isHindi)
            "⏳ Llama 3.2 1B एआई विशेषज्ञ सलाह तैयार कर रहा है..."
            else "⏳ Generating Llama 3.2 1B AI expert advice..."
        
        /*
        val d = disease.lowercase()
        val isHindi = resources.configuration.locales[0].language == "hi"

        return when {
            d.contains("bacterial") -> if (isHindi) "" else ""
            else -> ""
        }
        */
    }
}
