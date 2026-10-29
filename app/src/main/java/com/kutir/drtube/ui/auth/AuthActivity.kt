package com.kutir.drtube.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kutir.drtube.BuildConfig
import com.kutir.drtube.R
import com.kutir.drtube.data.local.AuthSession
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    companion object {
        private const val MODE_LOGIN = 0
        private const val MODE_REGISTER = 1
        private const val YOUTUBE_SCOPE = "oauth2:https://www.googleapis.com/auth/youtube.force-ssl"
    }

    @Inject
    lateinit var auth: FirebaseAuth

    private var mode = MODE_LOGIN
    private var pendingGoogleAccount: GoogleSignInAccount? = null

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirm: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnGoogle: Button
    private lateinit var tvSwitch: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var progress: ProgressBar

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                showLoading(false)
                return@registerForActivityResult
            }
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                completeGoogleSignIn(account)
            } catch (e: ApiException) {
                showLoading(false)
                showError(googleErrorMessage(e))
            }
        }

    private val recoveryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val account = pendingGoogleAccount
            if (result.resultCode == RESULT_OK && account != null) {
                completeGoogleSignIn(account)
            } else {
                showLoading(false)
                showError("Izin YouTube diperlukan untuk bisa berkomentar")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirm = findViewById(R.id.et_confirm)
        btnSubmit = findViewById(R.id.btn_submit)
        btnGoogle = findViewById(R.id.btn_google)
        tvSwitch = findViewById(R.id.tv_switch_mode)
        tvTitle = findViewById(R.id.tv_auth_title)
        tvSubtitle = findViewById(R.id.tv_auth_subtitle)
        progress = findViewById(R.id.progress)

        findViewById<ImageView>(R.id.btn_close).setOnClickListener { finish() }

        btnSubmit.setOnClickListener { submitEmailAuth() }
        btnGoogle.setOnClickListener { startGoogleSignIn() }
        tvSwitch.setOnClickListener { toggleMode() }

        renderMode()
    }

    private fun toggleMode() {
        mode = if (mode == MODE_LOGIN) MODE_REGISTER else MODE_LOGIN
        renderMode()
    }

    private fun renderMode() {
        val isRegister = mode == MODE_REGISTER
        tvTitle.text = if (isRegister) "Buat akun baru" else "Masuk ke akun Anda"
        tvSubtitle.text = if (isRegister) {
            "Daftar untuk menyimpan dan mengomentari video"
        } else {
            "Komentari video dan simpan favorit Anda"
        }
        btnSubmit.text = if (isRegister) "DAFTAR" else "MASUK"
        tvSwitch.text = if (isRegister) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar"
        etName.visibility = if (isRegister) View.VISIBLE else View.GONE
        etConfirm.visibility = if (isRegister) View.VISIBLE else View.GONE
        etConfirm.setText("")
    }

    private fun submitEmailAuth() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (email.isBlank() || !email.contains("@")) {
            showError("Masukkan email yang valid")
            return
        }
        if (password.length < 6) {
            showError("Kata sandi minimal 6 karakter")
            return
        }
        if (mode == MODE_REGISTER && etConfirm.text.toString() != password) {
            showError("Ulangi kata sandi tidak sama")
            return
        }

        showLoading(true)
        val task = if (mode == MODE_REGISTER) {
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            auth.signInWithEmailAndPassword(email, password)
        }

        task.addOnCompleteListener { result ->
            if (isFinishing) return@addOnCompleteListener
            if (result.isSuccessful) {
                val name = etName.text.toString().trim()
                val user = result.result?.user
                if (mode == MODE_REGISTER && name.isNotBlank() && user != null) {
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    )
                }
                showLoading(false)
                finishSuccess()
            } else {
                showLoading(false)
                showError(authErrorMessage(result.exception))
            }
        }
    }

    private fun startGoogleSignIn() {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/youtube.force-ssl"),
                Scope(Scopes.EMAIL),
                Scope(Scopes.PROFILE)
            )
        val webClientId = resolveWebClientId()
        if (webClientId.isNotBlank()) {
            builder.requestIdToken(webClientId)
        }
        val client = GoogleSignIn.getClient(this, builder.build())
        showLoading(true)
        googleLauncher.launch(client.signInIntent)
    }

    private fun completeGoogleSignIn(account: GoogleSignInAccount) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val googleAccount = account.account
                val accessToken = if (googleAccount != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            GoogleAuthUtil.getToken(this@AuthActivity, googleAccount, YOUTUBE_SCOPE)
                        }.getOrNull()
                    }
                } else null

                if (!accessToken.isNullOrBlank()) {
                    AuthSession.youtubeAccessToken = accessToken
                }
                val credential = GoogleAuthProvider.getCredential(account.idToken, accessToken)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { result ->
                        if (isFinishing) return@addOnCompleteListener
                        showLoading(false)
                        if (result.isSuccessful) {
                            finishSuccess()
                        } else {
                            // Fallback: sign in with idToken alone if accessToken failed
                            if (accessToken != null) {
                                val fallbackCredential = GoogleAuthProvider.getCredential(account.idToken, null)
                                auth.signInWithCredential(fallbackCredential).addOnCompleteListener { fbResult ->
                                    if (fbResult.isSuccessful) {
                                        finishSuccess()
                                    } else {
                                        showError(
                                            "Gagal masuk ke Firebase: " + (fbResult.exception?.message ?: "Unknown error")
                                        )
                                    }
                                }
                            } else {
                                showError(
                                    "Google berhasil, tetapi Firebase menolak kredensial. " +
                                        "Aktifkan provider Google di Firebase Console.\n" +
                                        (result.exception?.message ?: "")
                                )
                            }
                        }
                    }
            } catch (e: UserRecoverableAuthException) {
                pendingGoogleAccount = account
                val intent = e.intent
                if (intent != null) {
                    recoveryLauncher.launch(intent)
                } else {
                    showLoading(false)
                    // Proceed with ID token even if recoverable auth exception occurs
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential).addOnCompleteListener { result ->
                        showLoading(false)
                        if (result.isSuccessful) finishSuccess()
                        else showError("Gagal masuk dengan Google")
                    }
                }
            } catch (e: Exception) {
                showLoading(false)
                // Fallback attempt with idToken only
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { result ->
                    if (result.isSuccessful) {
                        finishSuccess()
                    } else {
                        showError(
                            "Gagal masuk dengan Google. Pastikan OAuth client Android " +
                                "(SHA-1) terdaftar di Firebase.\n${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun resolveWebClientId(): String {
        val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
        val fromResource = if (resId != 0) getString(resId) else ""
        return fromResource.ifBlank { BuildConfig.GOOGLE_WEB_CLIENT_ID }
    }

    private fun finishSuccess() {
        setResult(RESULT_OK, Intent())
        finish()
    }

    private fun showLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !loading
        btnGoogle.isEnabled = !loading
        tvSwitch.isEnabled = !loading
        etEmail.isEnabled = !loading
        etPassword.isEnabled = !loading
        etConfirm.isEnabled = !loading
        etName.isEnabled = !loading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun googleErrorMessage(e: ApiException): String {
        return when (e.statusCode) {
            10 -> "Google Sign-In belum dikonfigurasi untuk aplikasi ini. " +
                "Tambahkan OAuth client Android (SHA-1 debug) di Firebase Console, " +
                "aktifkan provider Google, lalu unduh ulang google-services.json."
            12500 -> "Google Sign-In gagal. Periksa konfigurasi Firebase/Google Cloud."
            12501 -> "Masuk dengan Google dibatalkan"
            7 -> "Tidak ada koneksi internet"
            else -> "Gagal masuk dengan Google (kode ${e.statusCode})"
        }
    }

    private fun authErrorMessage(exception: Exception?): String {
        val errorCode = (exception as? FirebaseAuthException)?.errorCode
        return when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "Format email tidak valid"
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
                "Email atau kata sandi salah"
            "ERROR_USER_NOT_FOUND" -> "Akun tidak ditemukan"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Email sudah terdaftar"
            "ERROR_WEAK_PASSWORD" -> "Kata sandi minimal 6 karakter"
            "ERROR_NETWORK_REQUEST_FAILED" -> "Tidak ada koneksi internet"
            "ERROR_TOO_MANY_REQUESTS" -> "Terlalu banyak percobaan, coba lagi nanti"
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Metode masuk email belum diaktifkan di Firebase Console"
            else -> exception?.localizedMessage ?: "Gagal memproses autentikasi"
        }
    }
}
