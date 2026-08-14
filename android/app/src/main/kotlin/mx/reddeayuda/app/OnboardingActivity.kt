package mx.reddeayuda.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {
    private var step = 0
    private val images = intArrayOf(R.drawable.onboard_1, R.drawable.onboard_2, R.drawable.onboard_3)
    private val titles = intArrayOf(R.string.onboard_1_title, R.string.onboard_2_title, R.string.onboard_3_title)
    private val bodies = intArrayOf(R.string.onboard_1_body, R.string.onboard_2_body, R.string.onboard_3_body)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        findViewById<View>(R.id.btnNext).setOnClickListener {
            if (step >= 2) {
                Prefs.setOnboardingDone(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                step++
                bind()
            }
        }
        bind()
    }

    private fun bind() {
        findViewById<ImageView>(R.id.image).setImageResource(images[step])
        findViewById<TextView>(R.id.title).setText(titles[step])
        findViewById<TextView>(R.id.body).setText(bodies[step])
        findViewById<TextView>(R.id.stepLabel).text = getString(R.string.onboard_step, step + 1)
        val btn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNext)
        btn.setText(if (step >= 2) R.string.onboard_start else R.string.onboard_next)
        val dots = findViewById<LinearLayout>(R.id.dots)
        dots.removeAllViews()
        repeat(3) { i ->
            val v = View(this)
            val size = (8 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = size / 2
            lp.marginEnd = size / 2
            v.layoutParams = lp
            v.setBackgroundResource(if (i == step) R.drawable.dot_on else R.drawable.dot)
            dots.addView(v)
        }
    }
}
