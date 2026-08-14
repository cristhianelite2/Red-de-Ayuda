package mx.reddeayuda.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun p(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("rda_prefs", Context.MODE_PRIVATE)

    fun onboardingDone(ctx: Context): Boolean = p(ctx).getBoolean("onboarding_done", false)

    fun setOnboardingDone(ctx: Context) {
        p(ctx).edit().putBoolean("onboarding_done", true).apply()
    }
}
