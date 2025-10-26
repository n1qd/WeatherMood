package com.example.weathermood.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

sealed class AuthUser {
    data class LoggedIn(val uid: String, val email: String?, val isAnonymous: Boolean) : AuthUser()
    data object LoggedOut : AuthUser()
}

interface AuthService {
    suspend fun currentUser(): AuthUser
    suspend fun signInAnonymously(): AuthUser
    suspend fun signIn(email: String, password: String): AuthUser?
    suspend fun register(email: String, password: String): AuthUser?
    suspend fun signOut()
}

class FirebaseAuthService(context: Context) : AuthService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun currentUser(): AuthUser {
        val u = auth.currentUser
        return if (u != null) AuthUser.LoggedIn(u.uid, u.email, u.isAnonymous) else AuthUser.LoggedOut
    }

    override suspend fun signInAnonymously(): AuthUser {
        return try {
            val res = auth.signInAnonymously().await().user
            if (res != null) {
                AuthUser.LoggedIn(res.uid, res.email, res.isAnonymous)
            } else {
                throw Exception("Не удалось создать анонимного пользователя")
            }
        } catch (e: Exception) {
            // Логируем ошибку для отладки
            android.util.Log.e("FirebaseAuthService", "Ошибка анонимной аутентификации: ${e.message}", e)
            throw e // Пробрасываем ошибку наверх для обработки
        }
    }

    override suspend fun signIn(email: String, password: String): AuthUser? {
        return try {
            val res = auth.signInWithEmailAndPassword(email, password).await().user
            if (res != null) AuthUser.LoggedIn(res.uid, res.email, res.isAnonymous) else null
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Ошибка входа: ${e.message}", e)
            null
        }
    }

    override suspend fun register(email: String, password: String): AuthUser? {
        return try {
            val res = auth.createUserWithEmailAndPassword(email, password).await().user
            if (res != null) AuthUser.LoggedIn(res.uid, res.email, res.isAnonymous) else null
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Ошибка регистрации: ${e.message}", e)
            null
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}

// Simple fallback that keeps a fake anonymous user in memory
class FakeAuthService : AuthService {
    private var uid: String? = null
    
    companion object {
        // Простое хранилище зарегистрированных пользователей (email -> password)
        private val registeredUsers = mutableMapOf<String, String>()
    }

    override suspend fun currentUser(): AuthUser =
        uid?.let { AuthUser.LoggedIn(it, null, true) } ?: AuthUser.LoggedOut

    override suspend fun signInAnonymously(): AuthUser {
        uid = "anon-" + System.currentTimeMillis()
        return AuthUser.LoggedIn(uid!!, null, true)
    }

    override suspend fun signIn(email: String, password: String): AuthUser? {
        // Проверяем, зарегистрирован ли пользователь
        val storedPassword = registeredUsers[email]
        
        if (storedPassword != null && storedPassword == password) {
            // Пользователь найден и пароль совпадает
            uid = "user-" + email.hashCode()
            return AuthUser.LoggedIn(uid!!, email, false)
        }
        
        // Пользователь не найден или неверный пароль
        return null
    }

    override suspend fun register(email: String, password: String): AuthUser? {
        // Проверяем валидность данных
        if (email.isEmpty() || password.length < 6) {
            return null
        }
        
        // Проверяем, не зарегистрирован ли уже этот email
        if (registeredUsers.containsKey(email)) {
            return null // Email уже существует
        }
        
        // Регистрируем нового пользователя
        registeredUsers[email] = password
        uid = "user-" + email.hashCode()
        return AuthUser.LoggedIn(uid!!, email, false)
    }

    override suspend fun signOut() {
        uid = null
    }
}




