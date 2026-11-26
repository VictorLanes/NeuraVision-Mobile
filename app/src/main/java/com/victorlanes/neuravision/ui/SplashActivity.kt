package com.victorlanes.neuravision.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.victorlanes.neuravision.data.AuthStore
import com.victorlanes.neuravision.data.Session
import com.victorlanes.neuravision.databinding.ActivitySplashBinding

/** Tela de abertura com animação do logo. Depois vai para criar conta ou desbloquear. */
class SplashActivity : AppCompatActivity() {

    private lateinit var b: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        animate()
        b.root.postDelayed({ route() }, 1700)
    }

    private fun animate() {
        // logo entra crescendo
        b.logo.alpha = 0f
        b.logo.scaleX = 0.6f
        b.logo.scaleY = 0.6f
        b.logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600).setInterpolator(OvershootInterpolator()).start()

        // linha de varredura (estilo câmera) passando pelo logo
        b.scan.alpha = 0f
        b.scan.translationY = -60f
        b.scan.animate().alpha(0.9f).setStartDelay(350).setDuration(200).start()
        b.scan.animate().translationY(60f).setStartDelay(450).setDuration(850)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                b.scan.animate().alpha(0f).setDuration(250).start()
            }.start()

        // título e subtítulo
        for (v in listOf(b.title, b.subtitle)) {
            v.alpha = 0f
            v.translationY = 18f
        }
        b.title.animate().alpha(1f).translationY(0f).setStartDelay(500).setDuration(500).start()
        b.subtitle.animate().alpha(1f).translationY(0f).setStartDelay(700).setDuration(500).start()
    }

    private fun route() {
        val auth = AuthStore(this)
        val next = if (!auth.hasAccount()) {
            Intent(this, OnboardingActivity::class.java)
        } else {
            Session.lock()
            Intent(this, LockActivity::class.java)
        }
        startActivity(next)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
