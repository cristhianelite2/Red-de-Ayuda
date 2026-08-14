package mx.reddeayuda.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object BackBar {
    fun bind(activity: Activity, title: CharSequence? = null) {
        applyStatusBarPadding(activity)

        val back = activity.findViewById<View>(R.id.btnBack)
            ?: error("btnBack missing in ${activity.javaClass.simpleName}")
        back.setOnClickListener { activity.finish() }

        // La barra no debe ser clicable: si el botón y la barra llaman finish(),
        // se cierran dos pantallas de golpe (p. ej. detalle + menú RCP).
        activity.findViewById<View>(R.id.backBar)?.apply {
            isClickable = false
            isFocusable = false
            setOnClickListener(null)
        }

        title?.let { label ->
            activity.findViewById<TextView>(R.id.barTitle)?.text = label
        }
    }

    private fun applyStatusBarPadding(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
