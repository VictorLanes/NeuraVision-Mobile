package com.victorlanes.neuravision.data

/** Estado de desbloqueio durante o uso. Reinicia a cada abertura do app (processo novo = bloqueado). */
object Session {
    @Volatile var unlocked = false
    @Volatile var pausedAt = 0L

    const val RELOCK_MS = 45_000L   // volta a pedir desbloqueio após este tempo em segundo plano

    fun lock() { unlocked = false }

    fun shouldRelock(): Boolean =
        unlocked && pausedAt > 0 && System.currentTimeMillis() - pausedAt > RELOCK_MS
}
