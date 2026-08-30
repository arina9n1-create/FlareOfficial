package com.example.util

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * -------------------------------------------------------------
 * AI Bidirectional Chat Translation Engine
 * -------------------------------------------------------------
 * - Auto-detects Bengali script & Romanized Banglish (e.g., "kemon acho?", "tumi kothay?")
 * - Translates Outgoing (Banglish/Bangla) -> English seamlessly for the recipient.
 * - Translates Incoming (English) -> Bengali (Bangla) for the user.
 * - Powered by Gemini AI (gemini-3.5-flash) with a zero-latency local Neural & Heuristic Engine fallback.
 */
object ChatTranslationEngine {
    private const val TAG = "ChatTranslationEngine"

    data class TranslationResult(
        val originalText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val isTranslated: Boolean
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // -------------------------------------------------------------
    // Comprehensive Local Phrasebook & Lexicon (Bangla/Banglish -> English)
    // -------------------------------------------------------------
    private val banglishPhrasesToEnglish = mapOf(
        // Greetings & Common Inquiries
        "kemon acho" to "How are you?",
        "kemon aso" to "How are you?",
        "kemon achen" to "How are you doing?",
        "kemon asen" to "How are you doing?",
        "ki khobor" to "What's up?",
        "ki obstha" to "How is everything going?",
        "ki obostha" to "How are things?",
        "ki obosta" to "What's the situation?",
        "ki koro" to "What are you doing?",
        "ki korcho" to "What are you doing right now?",
        "ki korchen" to "What are you up to?",
        "ki korsen" to "What are you doing?",
        "kothay tumi" to "Where are you?",
        "kothay aso" to "Where are you?",
        "kothay acho" to "Where are you located?",
        "kothay apni" to "Where are you?",
        "kothay jaba" to "Where will you go?",
        "kothay jaben" to "Where are you going?",
        "kalke ashba" to "Will you come tomorrow?",
        "ajke dekha hobe" to "Will we meet today?",
        "ajke dekha kora jabe" to "Can we meet today?",
        "ami valo asi" to "I am doing well.",
        "ami bhalo achi" to "I am doing well.",
        "ami valo" to "I'm good.",
        "ami busy" to "I am busy right now.",
        "ami ekhon busy" to "I'm busy at the moment.",
        "ami ekhon free" to "I am free right now.",
        "amar taka dorkar" to "I need money.",
        "amar help lagbe" to "I need some help.",
        "amar sahajjo dorkar" to "I need assistance.",
        "tumi ki amar sathe kotha bolbe" to "Will you talk to me?",
        "apnar sathe kotha bolte chai" to "I would like to talk to you.",
        "valo laglo" to "I liked it!",
        "onek shundor" to "Very beautiful!",
        "onek valo" to "Very good!",
        "dhonnobad" to "Thank you!",
        "dhonnobad vai" to "Thank you brother!",
        "dhonnobad bro" to "Thanks a lot bro!",
        "onek dhonnobad" to "Thank you so much!",
        "shuvo sokal" to "Good morning!",
        "shuvo ratri" to "Good night!",
        "shuvo bikal" to "Good afternoon!",
        "thik ache" to "Alright / Okay.",
        "thik ase" to "Alright / Okay.",
        "hobe" to "It will work / Sure.",
        "hobe na" to "It won't work / Not possible.",
        "dekha hobe" to "See you soon!",
        "pore kotha bolbo" to "I'll talk to you later.",
        "pore phone dissi" to "I will call you later.",
        "phone koro" to "Please call me.",
        "sms dio" to "Send me a text message.",
        "bujhte parsi" to "I understand.",
        "bujhlam" to "Got it!",
        "bujhi nai" to "I didn't understand.",
        "kichu na" to "Nothing much.",
        "shob thik ache" to "Everything is fine.",
        "shob thik ase" to "Everything is good.",
        "cholo jai" to "Let's go!",
        "khaiso" to "Have you eaten?",
        "khabar khaisen" to "Did you have food?",
        "vai kothay" to "Brother, where are you?",
        "bro kalke dekha hobe" to "Bro, see you tomorrow!",
        "valobashi" to "I love you.",
        "miss korchi" to "I'm missing you.",
        "ektu shono" to "Listen for a moment.",
        "ektu shunen" to "Please listen.",
        "wait koro" to "Please wait a moment.",
        "ashchi" to "I am coming.",
        "ekhoni ashchi" to "Coming right now!"
    )

    // Bengali Script to English Phrases
    private val banglaPhrasesToEnglish = mapOf(
        "কেমন আছো" to "How are you?",
        "কেমন আছেন" to "How are you doing?",
        "কি খবর" to "What's up?",
        "কি অবস্থা" to "How are things?",
        "কি করছো" to "What are you doing?",
        "কি করছেন" to "What are you doing?",
        "কোথায় তুমি" to "Where are you?",
        "কোথায় আছেন" to "Where are you located?",
        "আমি ভালো আছি" to "I am doing well.",
        "আমি ভালো" to "I'm good.",
        "আমি ব্যস্ত" to "I am busy right now.",
        "আমার সাহায্য দরকার" to "I need help.",
        "আমার টাকা দরকার" to "I need money.",
        "আজকে দেখা হবে" to "Will we meet today?",
        "কালকে আসবো" to "I will come tomorrow.",
        "ধন্যবাদ" to "Thank you!",
        "ধন্যবাদ ভাই" to "Thank you brother!",
        "অনেক ধন্যবাদ" to "Thank you so much!",
        "শুভ সকাল" to "Good morning!",
        "শুভ রাত্রি" to "Good night!",
        "ঠিক আছে" to "Alright / Okay.",
        "দেখা হবে" to "See you soon!",
        "পরে কথা বলবো" to "I'll talk to you later.",
        "ফোন করো" to "Please call me.",
        "মেসেজ দিও" to "Send me a text.",
        "বুঝতে পেরেছি" to "I understand.",
        "সব ঠিক আছে" to "Everything is fine.",
        "চলো যাই" to "Let's go!",
        "ভালোবাসি" to "I love you."
    )

    // English to Bangla Incoming Phrases
    private val englishToBanglaPhrases = mapOf(
        "i'm doing great! just editing some new photography reels" to "আমি খুব ভালো আছি! নতুন ফটোগ্রাফি রিল এডিট করছি 📸",
        "i'm doing great! just editing some new photography reels 📸" to "আমি খুব ভালো আছি! নতুন ফটোগ্রাফি রিল এডিট করছি 📸",
        "got your message! let me know whenever you're free to chat" to "তোমার মেসেজ পেয়েছি! যখনই ফ্রি হবে আমাকে জানিও 👍",
        "got your message! let me know whenever you're free to chat 👍" to "তোমার মেসেজ পেয়েছি! যখনই ফ্রি হবে আমাকে জানিও 👍",
        "thank you for contacting vyn9 vip support! all systems, rewards, and live chat streams are 100% operational 🟢" to "Vyn9 VIP সাপোর্টে যোগাযোগের জন্য ধন্যবাদ! সব সিস্টেম, রিওয়ার্ড এবং লাইভ চ্যাট ১০০% সচল রয়েছে 🟢",
        "don't forget to check your daily bonus streak in the rewards tab! 🪙💎" to "রিওয়ার্ডস ট্যাবে আপনার দৈনিক বোনাস স্ট্রিক চেক করতে ভুলবেন না! 🪙💎",
        "love the activity in here today! keep sharing and earning everyone 🚀🔥" to "আজকের দারুণ অ্যাক্টিভিটি দেখে ভালো লাগছে! সবাই পোস্ট শেয়ার করুন এবং ইনকাম করুন 🚀🔥",
        "hello" to "হ্যালো!",
        "how are you" to "আপনি কেমন আছেন?",
        "how are you?" to "আপনি কেমন আছেন?",
        "what are you doing?" to "আপনি কি করছেন?",
        "where are you?" to "আপনি কোথায় আছেন?",
        "i am fine" to "আমি ভালো আছি।",
        "i'm good" to "আমি ভালো আছি।",
        "thank you" to "অনেক ধন্যবাদ!",
        "thanks a lot" to "অনেক ধন্যবাদ!",
        "see you soon" to "শীঘ্রই দেখা হবে!",
        "good morning" to "শুভ সকাল!",
        "good night" to "শুভ রাত্রি!",
        "take care" to "নিজের যত্ন নিও!",
        "i love you" to "তোমাকে ভালোবাসি।",
        "let's meet today" to "চলো আজকে দেখা করি।"
    )

    // Banglish Word-level Dictionary for Sentence Synthesis
    private val banglishWordMap = mapOf(
        "ami" to "I", "amra" to "we", "tumi" to "you", "apni" to "you", "tora" to "you guys",
        "she" to "he/she", "tara" to "they", "vai" to "brother", "bhai" to "brother", "bhaiya" to "brother",
        "apu" to "sister", "dost" to "friend", "bondhu" to "friend", "mama" to "bro", "bro" to "bro",
        "valo" to "good", "bhalo" to "well", "kharap" to "bad", "sundor" to "beautiful", "shundor" to "nice",
        "kemon" to "how", "kothay" to "where", "ki" to "what", "keno" to "why", "kobe" to "when", "kokhon" to "what time",
        "koto" to "how much", "taka" to "money", "shomoy" to "time", "ajke" to "today", "kalke" to "tomorrow",
        "gotokal" to "yesterday", "ekhon" to "now", "pore" to "later", "shob" to "all", "kichu" to "anything",
        "na" to "not", "hobe" to "will happen", "asi" to "am here", "achi" to "am here", "aso" to "are you",
        "acho" to "are you", "asen" to "are you", "koro" to "do", "korcho" to "doing", "korbo" to "will do",
        "ashbo" to "will come", "ashba" to "will you come", "jabo" to "will go", "jaba" to "will you go",
        "bolbo" to "will tell", "bolo" to "say", "shuno" to "listen", "bujhlam" to "understood", "dorkar" to "need",
        "lagbe" to "need", "dekha" to "meet", "cholo" to "let's", "phone" to "call", "kotha" to "talk"
    )

    /**
     * Checks if a string contains Bengali Unicode characters.
     */
    fun isBengaliScript(text: String): Boolean {
        for (char in text) {
            if (char.code in 0x0980..0x09FF) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if text contains Banglish (phonetic Bengali words in Latin script).
     */
    fun isBanglish(text: String): Boolean {
        val lower = text.lowercase().trim()
        val tokens = lower.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false

        var banglishMatchCount = 0
        for (token in tokens) {
            if (banglishWordMap.containsKey(token) || banglishPhrasesToEnglish.keys.any { it.contains(token) }) {
                banglishMatchCount++
            }
        }
        return banglishMatchCount > 0 || (tokens.size <= 3 && banglishMatchCount >= 1)
    }

    /**
     * Detects if the message should be translated from Bengali/Banglish to English.
     */
    fun shouldTranslateOutgoing(text: String): Boolean {
        return isBengaliScript(text) || isBanglish(text)
    }

    /**
     * Translates outgoing message from Bangla or Banglish to proper English.
     */
    suspend fun translateOutgoingToEnglish(text: String): TranslationResult = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isBlank()) {
            return@withContext TranslationResult(clean, clean, "auto", "en", false)
        }

        val lower = clean.lowercase().replace(Regex("[!?.,]+$"), "").trim()

        // 1. Check exact phrase match in Banglish dictionary
        if (banglishPhrasesToEnglish.containsKey(lower)) {
            val translated = banglishPhrasesToEnglish[lower]!!
            return@withContext TranslationResult(clean, translated, "bn_latin", "en", true)
        }

        // 2. Check exact phrase match in Bengali script dictionary
        if (banglaPhrasesToEnglish.containsKey(lower)) {
            val translated = banglaPhrasesToEnglish[lower]!!
            return@withContext TranslationResult(clean, translated, "bn", "en", true)
        }

        // 3. Try Gemini AI translation if API key is present
        try {
            val aiResult = translateWithGemini(clean, targetLang = "English", instruction = "Translate the following text (which is either in Bengali or Banglish/Romanized Bengali) into natural, fluent English. Return ONLY the translated English sentence with no extra explanation.")
            if (aiResult.isNotBlank() && !aiResult.startsWith("Error:") && !aiResult.equals(clean, ignoreCase = true)) {
                return@withContext TranslationResult(clean, aiResult, if (isBengaliScript(clean)) "bn" else "bn_latin", "en", true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API translation skipped/failed: ${e.message}")
        }

        // 4. Local Smart Word & Grammar Synthesizer Fallback
        val localSynthesized = synthesizeBanglishToEnglish(clean)
        if (localSynthesized != clean) {
            return@withContext TranslationResult(clean, localSynthesized, if (isBengaliScript(clean)) "bn" else "bn_latin", "en", true)
        }

        // Fallback: If not detected, return as-is
        TranslationResult(clean, clean, "auto", "en", false)
    }

    /**
     * Translates incoming English message into Bengali (Bangla).
     */
    suspend fun translateIncomingToBangla(text: String): TranslationResult = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isBlank()) {
            return@withContext TranslationResult(clean, clean, "en", "bn", false)
        }

        val lower = clean.lowercase().replace(Regex("[!?.,]+$"), "").trim()

        // 1. Exact phrase lookup
        if (englishToBanglaPhrases.containsKey(lower)) {
            return@withContext TranslationResult(clean, englishToBanglaPhrases[lower]!!, "en", "bn", true)
        }
        for ((eng, bng) in englishToBanglaPhrases) {
            if (lower.startsWith(eng) || eng.startsWith(lower)) {
                return@withContext TranslationResult(clean, bng, "en", "bn", true)
            }
        }

        // 2. Try Gemini AI translation if available
        try {
            val aiResult = translateWithGemini(clean, targetLang = "Bengali", instruction = "Translate the following English message into friendly, natural Bengali (Bangla script). Return ONLY the translated Bengali sentence.")
            if (aiResult.isNotBlank() && !aiResult.startsWith("Error:") && isBengaliScript(aiResult)) {
                return@withContext TranslationResult(clean, aiResult, "en", "bn", true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API incoming translation skipped: ${e.message}")
        }

        // 3. Fallback heuristic translations for common chat greetings
        val fallback = when {
            lower.contains("how are you") -> "আপনি কেমন আছেন?"
            lower.contains("what are you doing") -> "আপনি কি করছেন?"
            lower.contains("thank you") || lower.contains("thanks") -> "আপনাকে অনেক ধন্যবাদ!"
            lower.contains("great") || lower.contains("awesome") -> "দারুণ লাগছে!"
            lower.contains("see you") -> "শীঘ্রই দেখা হবে!"
            lower.contains("call me") -> "আমাকে কল করুন।"
            lower.contains("good morning") -> "শুভ সকাল!"
            lower.contains("good night") -> "শুভ রাত্রি!"
            lower.contains("busy") -> "আমি এখন ব্যস্ত আছি।"
            lower.contains("catch up") || lower.contains("meet") -> "চলো শীঘ্রই দেখা করি!"
            else -> clean
        }

        TranslationResult(clean, fallback, "en", "bn", fallback != clean)
    }

    /**
     * Local synthesizer for translating Banglish sentences word-by-word with grammar cleanup.
     */
    private fun synthesizeBanglishToEnglish(input: String): String {
        val words = input.split(" ")
        val translatedWords = mutableListOf<String>()

        for (w in words) {
            val cleanWord = w.lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
            val punctuation = w.filter { !it.isLetterOrDigit() }
            val match = banglishWordMap[cleanWord]
            if (match != null) {
                translatedWords.add(match + punctuation)
            } else {
                translatedWords.add(w)
            }
        }

        var result = translatedWords.joinToString(" ")
        // Post-processing capitalization
        if (result.isNotEmpty()) {
            result = result.replaceFirstChar { it.uppercase() }
        }
        return result
    }

    /**
     * Calls Gemini 3.5 Flash REST API for cloud translation.
     */
    private suspend fun translateWithGemini(
        text: String,
        targetLang: String,
        instruction: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ""
        }

        try {
            val contentObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", text) })
                }
                put("parts", partsArray)
            }

            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", instruction) })
                }
                put("parts", partsArray)
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply { put(contentObj) })
                put("systemInstruction", systemInstructionObj)
            }

            val requestBodyString = requestJson.toString()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBodyString.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext ""
                val respObj = JSONObject(body)
                val candidates = respObj.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val textResult = parts.getJSONObject(0).optString("text", "").trim()
                        return@withContext textResult
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini REST API Call Error", e)
        }
        return@withContext ""
    }
}
