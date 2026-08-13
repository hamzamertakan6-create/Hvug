package com.orangex.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private data class Tab(
        val id: Int,
        var webView: WebView?,
        var title: String,
        var url: String,
        var isPrivate: Boolean
    )

    private val tabs = mutableListOf<Tab>()
    private var currentTabIndex = -1
    private var nextTabId = 1
    private var adblockEnabled = true

    private lateinit var contentFrame: FrameLayout
    private lateinit var urlField: EditText
    private lateinit var btnBack: ImageButton
    private lateinit var btnTabs: ImageButton
    private lateinit var btnPrivate: ImageButton
    private lateinit var btnAddFavorite: Button
    private lateinit var bottomBar: LinearLayout
    private lateinit var tabsPanel: LinearLayout
    private lateinit var tabsListContainer: LinearLayout
    private lateinit var btnNewTab: Button
    private lateinit var topToolsRow: LinearLayout
    private lateinit var btnFindInPage: Button
    private lateinit var btnSummarize: Button
    private lateinit var btnAdblock: Button

    private val uiHandler = Handler(Looper.getMainLooper())
    private var hideAddFavRunnable: Runnable? = null
    private val netExecutor = Executors.newSingleThreadExecutor()

    private val adBlockDomains = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "taboola.com", "outbrain.com",
        "popads.net", "propellerads.com", "adcolony.com"
    )

    private val prefs by lazy { getSharedPreferences("orangex_prefs", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contentFrame = findViewById(R.id.contentFrame)
        urlField = findViewById(R.id.urlField)
        btnBack = findViewById(R.id.btnBack)
        btnTabs = findViewById(R.id.btnTabs)
        btnPrivate = findViewById(R.id.btnPrivate)
        btnAddFavorite = findViewById(R.id.btnAddFavorite)
        bottomBar = findViewById(R.id.bottomBar)
        tabsPanel = findViewById(R.id.tabsPanel)
        tabsListContainer = findViewById(R.id.tabsListContainer)
        btnNewTab = findViewById(R.id.btnNewTab)
        topToolsRow = findViewById(R.id.topToolsRow)
        btnFindInPage = findViewById(R.id.btnFindInPage)
        btnSummarize = findViewById(R.id.btnSummarize)
        btnAdblock = findViewById(R.id.btnAdblock)

        openNewTab(private = false, showStart = true)

        urlField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitUrlField()
                true
            } else false
        }

        btnBack.setOnClickListener {
            val wv = currentTab()?.webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                showStartPage()
            }
        }

        btnTabs.setOnClickListener {
            tabsPanel.visibility = if (tabsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (tabsPanel.visibility == View.VISIBLE) refreshTabsPanel()
        }

        btnNewTab.setOnClickListener {
            openNewTab(private = currentTab()?.isPrivate ?: false, showStart = true)
            tabsPanel.visibility = View.GONE
        }

        // Private mode: opens a fresh private tab. Bottom bar recolors to signal the mode.
        btnPrivate.setOnClickListener {
            openNewTab(private = true, showStart = true)
        }

        btnAddFavorite.setOnClickListener { addCurrentPageToFavorites() }

        btnAdblock.text = "Adblock: Açık"

        btnFindInPage.setOnClickListener { showFindInPageDialog() }
        btnSummarize.setOnClickListener { summarizeCurrentPage() }
        btnSummarize.setOnLongClickListener { showApiKeyDialog(); true }
        btnAdblock.setOnClickListener {
            adblockEnabled = !adblockEnabled
            btnAdblock.text = if (adblockEnabled) "Adblock: Açık" else "Adblock: Kapalı"
            Toast.makeText(this, if (adblockEnabled) "Reklam engelleyici açık" else "Reklam engelleyici kapalı", Toast.LENGTH_SHORT).show()
        }

        // Swipe up from the bottom bar reveals the tab panel; swipe it back down to hide.
        var downY = 0f
        bottomBar.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> downY = event.rawY
                android.view.MotionEvent.ACTION_UP -> {
                    val dy = downY - event.rawY
                    if (dy > 60) {
                        tabsPanel.visibility = View.VISIBLE
                        refreshTabsPanel()
                    } else if (dy < -60) {
                        tabsPanel.visibility = View.GONE
                    }
                }
            }
            false
        }

        // Swipe down on the tab panel pushes it back into the bar.
        tabsPanel.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> downY = event.rawY
                android.view.MotionEvent.ACTION_UP -> {
                    if (event.rawY - downY > 60) tabsPanel.visibility = View.GONE
                }
            }
            false
        }

        // Top-right quick tools row (swipe down from top-right corner) and
        // X AI ask panel (swipe right starting from the left screen edge).
        var downX = 0f
        contentFrame.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    downX = event.rawX
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dy > 80 && downY < 250) {
                        topToolsRow.visibility = View.VISIBLE
                    } else if (downX < 40 && dx > 80 && kotlin.math.abs(dy) < 100) {
                        openXAiAsk()
                    }
                }
            }
            false
        }
    }

    override fun onBackPressed() {
        val wv = currentTab()?.webView
        if (tabsPanel.visibility == View.VISIBLE) {
            tabsPanel.visibility = View.GONE
        } else if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun currentTab(): Tab? = tabs.getOrNull(currentTabIndex)

    private fun openNewTab(private: Boolean, showStart: Boolean) {
        val tab = Tab(id = nextTabId++, webView = null, title = "Yeni sekme", url = "", isPrivate = private)
        tabs.add(tab)
        currentTabIndex = tabs.size - 1
        applyThemeForCurrentTab()
        if (showStart) showStartPage() else attachWebViewForCurrentTab()
    }

    private fun applyThemeForCurrentTab() {
        val isPrivate = currentTab()?.isPrivate ?: false
        val bg = if (isPrivate) R.color.bar_private_bg else R.color.bar_normal_bg
        val bgDrawable = bottomBar.background.mutate()
        (bgDrawable as? android.graphics.drawable.GradientDrawable)?.setColor(getColorCompat(bg))
        val tabsBg = tabsPanel.background.mutate()
        (tabsBg as? android.graphics.drawable.GradientDrawable)?.setColor(getColorCompat(bg))
        urlField.hint = if (isPrivate) "Gizli sekme • Bing'de ara" else "Bing'de ara veya adres yaz"
    }

    private fun getColorCompat(resId: Int) = resources.getColor(resId, theme)

    @SuppressLint("ClickableViewAccessibility")
    private fun buildWebViewFor(tab: Tab): WebView {
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.setSupportZoom(true)
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        if (tab.isPrivate) {
            wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            wv.settings.saveFormData = false
            CookieManager.getInstance().setAcceptCookie(false)
        } else {
            CookieManager.getInstance().setAcceptCookie(true)
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                if (adblockEnabled && adBlockDomains.any { host.contains(it) }) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tab.url = url ?: tab.url
                tab.title = view?.title ?: tab.title
                if (currentTab()?.id == tab.id) {
                    urlField.setText(url)
                    showAddFavoriteBrieflyIfFromSearch()
                }
            }
        }
        return wv
    }

    private fun attachWebViewForCurrentTab() {
        val tab = currentTab() ?: return
        contentFrame.removeAllViews()
        if (tab.webView == null) tab.webView = buildWebViewFor(tab)
        contentFrame.addView(tab.webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        urlField.setText(if (tab.url.isNotEmpty()) tab.url else "")
    }

    private fun showStartPage() {
        contentFrame.removeAllViews()
        urlField.setText("")

        val scroll = ScrollView(this)
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setPadding(24, 120, 24, 24)
        outer.gravity = Gravity.CENTER_HORIZONTAL

        val header = TextView(this)
        header.text = "Sık kullanılanlar"
        header.setTextColor(Color.WHITE)
        header.textSize = 18f
        header.setPadding(0, 0, 0, 24)
        outer.addView(header)

        val favorites = loadFavorites()
        val columns = if (favorites.size > 9) 4 else 3
        val grid = GridLayout(this)
        grid.columnCount = columns
        for (fav in favorites.take(16)) {
            val cell = TextView(this)
            cell.text = fav.optString("title", fav.optString("url"))
            cell.setTextColor(Color.WHITE)
            cell.setBackgroundColor(Color.parseColor("#1E1E1E"))
            cell.setPadding(28, 28, 28, 28)
            val lp = GridLayout.LayoutParams()
            lp.width = 220
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.setMargins(8, 8, 8, 8)
            cell.layoutParams = lp
            cell.maxLines = 2
            cell.setOnClickListener { loadUrlInCurrentTab(fav.optString("url")) }
            cell.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Kaldır")
                    .setMessage("${fav.optString("title")} favorilerden kaldırılsın mı?")
                    .setPositiveButton("Kaldır") { _, _ -> removeFavorite(fav.optString("url")); showStartPage() }
                    .setNegativeButton("Vazgeç", null)
                    .show()
                true
            }
            grid.addView(cell)
        }
        outer.addView(grid)

        if (favorites.isEmpty()) {
            val hint = TextView(this)
            hint.text = "Bir siteye girdikten sonra çıkan \"Favorilere ekle\" butonuna basarak buraya site ekleyebilirsin."
            hint.setTextColor(Color.GRAY)
            hint.setPadding(0, 24, 0, 0)
            outer.addView(hint)
        }

        scroll.addView(outer)
        contentFrame.addView(scroll)
    }

    private fun submitUrlField() {
        val input = urlField.text.toString().trim()
        if (input.isEmpty()) return
        loadUrlInCurrentTab(resolveInputToUrl(input))
    }

    private fun resolveInputToUrl(input: String): String {
        val looksLikeUrl = Patterns.WEB_URL.matcher(input).matches() && !input.contains(" ")
        return if (looksLikeUrl) {
            if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        } else {
            "https://www.bing.com/search?q=" + Uri.encode(input)
        }
    }

    private fun loadUrlInCurrentTab(url: String) {
        var tab = currentTab()
        if (tab == null) {
            openNewTab(private = false, showStart = false)
            tab = currentTab()
        }
        contentFrame.removeAllViews()
        if (tab!!.webView == null) tab.webView = buildWebViewFor(tab)
        contentFrame.addView(tab.webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        tab.webView!!.loadUrl(url)
        tab.url = url
        urlField.setText(url)
    }

    private fun showAddFavoriteBrieflyIfFromSearch() {
        hideAddFavRunnable?.let { uiHandler.removeCallbacks(it) }
        btnAddFavorite.visibility = View.VISIBLE
        val r = Runnable { btnAddFavorite.visibility = View.GONE }
        hideAddFavRunnable = r
        uiHandler.postDelayed(r, 10_000)
    }

    private fun addCurrentPageToFavorites() {
        val tab = currentTab() ?: return
        if (tab.url.isEmpty()) return
        val favorites = loadFavorites()
        for (i in 0 until favorites.length()) {
            if (favorites.getJSONObject(i).optString("url") == tab.url) {
                Toast.makeText(this, "Zaten favorilerde", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val obj = JSONObject()
        obj.put("title", tab.title.ifEmpty { tab.url })
        obj.put("url", tab.url)
        favorites.put(obj)
        prefs.edit().putString("favorites", favorites.toString()).apply()
        btnAddFavorite.visibility = View.GONE
        Toast.makeText(this, "Favorilere eklendi", Toast.LENGTH_SHORT).show()
    }

    private fun removeFavorite(url: String) {
        val favorites = loadFavorites()
        val updated = JSONArray()
        for (i in 0 until favorites.length()) {
            val obj = favorites.getJSONObject(i)
            if (obj.optString("url") != url) updated.put(obj)
        }
        prefs.edit().putString("favorites", updated.toString()).apply()
    }

    private fun loadFavorites(): JSONArray {
        val raw = prefs.getString("favorites", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }

    private fun refreshTabsPanel() {
        tabsListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        tabs.forEachIndexed { index, tab ->
            val row = inflater.inflate(R.layout.item_tab_row, tabsListContainer, false)
            val title = row.findViewById<TextView>(R.id.tabTitle)
            val close = row.findViewById<Button>(R.id.tabClose)
            val prefix = if (tab.isPrivate) "🕶 " else ""
            title.text = prefix + (tab.title.ifEmpty { if (tab.url.isEmpty()) "Yeni sekme" else tab.url })
            row.setOnClickListener {
                currentTabIndex = index
                applyThemeForCurrentTab()
                if (tab.url.isEmpty()) showStartPage() else attachWebViewForCurrentTab()
                tabsPanel.visibility = View.GONE
            }
            close.setOnClickListener {
                tab.webView?.destroy()
                tabs.removeAt(index)
                if (tabs.isEmpty()) {
                    openNewTab(private = false, showStart = true)
                } else {
                    currentTabIndex = (index - 1).coerceAtLeast(0)
                    applyThemeForCurrentTab()
                    if (currentTab()?.url?.isEmpty() == true) showStartPage() else attachWebViewForCurrentTab()
                }
                refreshTabsPanel()
            }
            tabsListContainer.addView(row)
        }
    }

    private fun showFindInPageDialog() {
        val wv = currentTab()?.webView ?: return
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Sayfada ara"
        AlertDialog.Builder(this)
            .setTitle("Sayfada bul")
            .setView(input)
            .setPositiveButton("Ara") { _, _ ->
                val q = input.text.toString()
                if (q.isNotEmpty()) wv.findAllAsync(q)
            }
            .setNegativeButton("Kapat") { _, _ -> wv.clearMatches() }
            .show()
    }

    // --- AI: page summarize + "X AI" quick ask, both call the Gemini API with a
    // user-supplied key. Nothing is hardcoded; add your key via long-press on "Özetle".

    private fun geminiApiKey(): String? {
        val key = prefs.getString("gemini_api_key", null)
        return if (key.isNullOrBlank()) null else key
    }

    private fun showApiKeyDialog() {
        val input = EditText(this)
        input.hint = "Gemini API anahtarı"
        input.setText(geminiApiKey() ?: "")
        AlertDialog.Builder(this)
            .setTitle("AI ayarları")
            .setMessage("Özetleme ve X AI özellikleri için kendi ücretsiz Gemini API anahtarını gir (ai.google.dev).")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.edit().putString("gemini_api_key", input.text.toString().trim()).apply()
                Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun summarizeCurrentPage() {
        val wv = currentTab()?.webView
        if (wv == null) {
            Toast.makeText(this, "Önce bir sayfa aç", Toast.LENGTH_SHORT).show()
            return
        }
        if (geminiApiKey() == null) {
            Toast.makeText(this, "Önce API anahtarı gir (Özetle'ye uzun bas)", Toast.LENGTH_LONG).show()
            showApiKeyDialog()
            return
        }
        wv.evaluateJavascript("(function(){return document.body ? document.body.innerText.slice(0,6000) : '';})();") { raw ->
            val text = raw?.trim('"')?.replace("\\n", " ")?.replace("\\\"", "\"") ?: ""
            if (text.isBlank()) {
                Toast.makeText(this, "Sayfa metni okunamadı", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            askGemini("Bu web sayfasını 5 madde halinde Türkçe özetle:\n\n$text") { result ->
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Özet (Gemini Flash Lite)")
                        .setMessage(result)
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            }
        }
    }

    /** "X AI" quick-ask panel: send the visible page text plus a typed question to Gemini. */
    private fun openXAiAsk() {
        val wv = currentTab()?.webView
        if (geminiApiKey() == null) {
            showApiKeyDialog()
            return
        }
        val input = EditText(this)
        input.hint = "Bu sayfa hakkında bir şey sor"
        AlertDialog.Builder(this)
            .setTitle("X AI")
            .setView(input)
            .setPositiveButton("Sor") { _, _ ->
                val question = input.text.toString()
                if (question.isBlank()) return@setPositiveButton
                if (wv == null) {
                    askGemini(question) { result -> runOnUiThread { showAiAnswer(result) } }
                } else {
                    wv.evaluateJavascript("(function(){return document.body ? document.body.innerText.slice(0,6000) : '';})();") { raw ->
                        val pageText = raw?.trim('"') ?: ""
                        askGemini("Sayfa içeriği:\n$pageText\n\nSoru: $question") { result ->
                            runOnUiThread { showAiAnswer(result) }
                        }
                    }
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showAiAnswer(result: String) {
        AlertDialog.Builder(this).setTitle("X AI").setMessage(result).setPositiveButton("Tamam", null).show()
    }

    private fun askGemini(prompt: String, onResult: (String) -> Unit) {
        val key = geminiApiKey() ?: return
        netExecutor.execute {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", key)
                conn.doOutput = true
                val body = JSONObject()
                val contents = JSONArray()
                val content = JSONObject()
                val parts = JSONArray()
                parts.put(JSONObject().put("text", prompt))
                content.put("parts", parts)
                contents.put(content)
                body.put("contents", contents)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) {
                    onResult("İstek başarısız oldu ($code): $responseText")
                    return@execute
                }
                val json = JSONObject(responseText)
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Yanıt alınamadı."
                onResult(text)
            } catch (e: Exception) {
                onResult("Hata: ${e.message}")
            }
        }
    }
}
