package com.ecotech.store

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Gestiona la autenticación con Firebase Auth, los roles en Cloud Firestore
 * y el inicio de sesión biométrico mediante huella dactilar.
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("ecotech_session", Context.MODE_PRIVATE)

    /**
     * Registra un usuario en Firebase Auth y guarda su perfil y rol en Firestore.
     */
    fun register(
        name: String,
        email: String,
        password: String,
        role: String,
        onSuccess: (UserModel) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                val newUser = UserModel(uid = uid, name = name.trim(), email = email.trim(), role = role)

                db.collection("users").document(uid).set(newUser)
                    .addOnSuccessListener {
                        saveLastSession(email.trim(), role)
                        onSuccess(newUser)
                    }
                    .addOnFailureListener { e -> onError("Error guardando perfil: ${e.message}") }
            }
            .addOnFailureListener { e -> onError("Error en registro: ${e.message}") }
    }

    /**
     * Inicia sesión con correo y contraseña y consulta el rol en Firestore.
     */
    fun login(
        email: String,
        password: String,
        onSuccess: (UserModel) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                fetchUserProfile(uid, onSuccess, onError)
            }
            .addOnFailureListener { e -> onError("Credenciales inválidas: ${e.message}") }
    }

    /**
     * Consulta los datos y rol del usuario autenticado en la colección "users".
     */
    fun fetchUserProfile(
        uid: String,
        onSuccess: (UserModel) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(UserModel::class.java)
                if (user != null) {
                    saveLastSession(user.email, user.role)
                    onSuccess(user)
                } else {
                    onError("No se encontró el perfil del usuario en Firestore.")
                }
            }
            .addOnFailureListener { e -> onError("Error consultando rol: ${e.message}") }
    }

    /**
     * Envía un correo oficial de Firebase para restablecer la contraseña.
     */
    fun resetPassword(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank()) {
            onError("Escribe tu correo electrónico primero.")
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError("No se pudo enviar el correo: ${e.message}") }
    }

    /**
     * Cierra la sesión activa en Firebase Auth.
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Ejecuta la autenticación biométrica real (huella dactilar) en dispositivo físico.
     */
    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Este dispositivo no tiene huella dactilar configurada.")
            return
        }

        val lastRole = prefs.getString("last_role", null)
        if (lastRole == null) {
            onError("Inicia sesión con correo y contraseña una vez para activar la huella.")
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(lastRole)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError("Autenticación cancelada: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Huella no reconocida. Intenta de nuevo.")
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Acceso Biométrico - EcoTech Store")
            .setSubtitle("Usa tu huella dactilar para ingresar rápidamente")
            .setNegativeButtonText("Usar contraseña")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun saveLastSession(email: String, role: String) {
        prefs.edit()
            .putString("last_email", email)
            .putString("last_role", role)
            .apply()
    }
}