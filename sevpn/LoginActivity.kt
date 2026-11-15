package io.nekohasekai.sfa.sevpn

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val API_BASE = "__API_BASE__"
private const val PROFILE_NAME = "SE VPN"

class LoginActivity : AppCompatActivity() {
    private var registerMode = false
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var error: TextView
    private lateinit var go: Button
    private lateinit var toggle: Button
    private lateinit var title: TextView
    private lateinit var spinner: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("sevpn", MODE_PRIVATE)
        if (prefs.getString("token", null) != null) {
            lifecycleScope.launch {
                val has = withContext(Dispatchers.IO) { ProfileManager.list().any { it.name == PROFILE_NAME } }
                if (has) openMain() else buildUi()
            }
        } else {
            buildUi()
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(64), dp(28), dp(28))
        }
        title = TextView(this).apply { textSize = 28f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        val sub = TextView(this).apply {
            text = "Быстрый и безопасный доступ к серверам"
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(28))
        }
        email = EditText(this).apply {
            hint = "Email"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
        }
        password = EditText(this).apply {
            hint = "Пароль (от 8 символов)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        error = TextView(this).apply { setTextColor(Color.parseColor("#E5484D")); setPadding(0, dp(10), 0, dp(6)) }
        go = Button(this).apply { setOnClickListener { submit() } }
        toggle = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            setOnClickListener { registerMode = !registerMode; refresh() }
        }
        spinner = ProgressBar(this).apply { visibility = View.GONE }
        val w = LinearLayout.LayoutParams.MATCH_PARENT
        val h = LinearLayout.LayoutParams.WRAP_CONTENT
        listOf(title, sub, email, password, error, go, toggle, spinner).forEach { col.addView(it, LinearLayout.LayoutParams(w, h)) }
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun refresh() {
        title.text = if (registerMode) "Регистрация в SE VPN" else "Вход в SE VPN"
        go.text = if (registerMode) "Создать аккаунт" else "Войти"
        toggle.text = if (registerMode) "Уже есть аккаунт" else "Регистрация"
        error.text = ""
    }

    private fun busy(b: Boolean) {
        spinner.visibility = if (b) View.VISIBLE else View.GONE
        go.isEnabled = !b; toggle.isEnabled = !b
    }

    private fun submit() {
        val em = email.text.toString().trim()
        val pw = password.text.toString()
        if (em.isEmpty() || pw.length < 8) {
            error.text = "Введите email и пароль (от 8 символов)"
            return
        }
        error.text = ""
        busy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { doLogin(em, pw) }
                openMain()
            } catch (e: Exception) {
                busy(false)
                error.text = e.message ?: "Не удалось подключиться к серверу"
            }
        }
    }

    private fun request(path: String, method: String, body: String? = null, token: String? = null): String {
        val c = URL(API_BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("Content-Type", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) {
            val msg = try { JSONObject(text).optString("detail") } catch (e: Exception) { "" }
            throw Exception(if (msg.isNotEmpty()) msg else "Ошибка сервера ($code)")
        }
        return text
    }

    private suspend fun doLogin(em: String, pw: String) {
        val creds = JSONObject().put("email", em).put("password", pw).toString()
        val auth = try {
            request(if (registerMode) "/api/auth/register" else "/api/auth/login", "POST", creds)
        } catch (e: java.io.IOException) {
            throw Exception("Нет связи с сервером. Проверьте интернет")
        }
        val token = JSONObject(auth).getString("token")
        val sub = JSONObject(request("/api/phone-sub", "POST", "{}", token))
        val url = API_BASE + sub.getString("sb_path")
        val config = request(sub.getString("sb_path"), "GET")
        JSONObject(config) // проверка, что это JSON

        for (p in ProfileManager.list().filter { it.name == PROFILE_NAME }) ProfileManager.delete(p)
        val typed = TypedProfile().apply {
            type = TypedProfile.Type.Remote
            remoteURL = url
            autoUpdate = true
            autoUpdateInterval = 60
            lastUpdated = Date()
        }
        val profile = Profile(name = PROFILE_NAME, typed = typed).apply { userOrder = ProfileManager.nextOrder() }
        val fileID = ProfileManager.nextFileID()
        val dir = File(filesDir, "configs").also { it.mkdirs() }
        val file = File(dir, "$fileID.json")
        file.writeText(config)
        typed.path = file.path
        ProfileManager.create(profile, andSelect = true)
        getSharedPreferences("sevpn", MODE_PRIVATE).edit().putString("token", token).apply()
    }
}
