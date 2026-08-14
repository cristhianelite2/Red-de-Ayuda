package mx.reddeayuda.app

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)
        SosBanner.bind(this, findViewById(R.id.sosBanner))

        when (intent.getStringExtra(EXTRA_ID)) {
            "inundacion" -> bind(
                R.drawable.guide_inundacion,
                R.string.guide_flood_title,
                R.string.guide_flood_sub,
                R.string.flood_body
            )
            else -> bind(
                R.drawable.guide_sismo,
                R.string.guide_sismo_title,
                R.string.guide_sismo_sub,
                R.string.sismo_body
            )
        }
    }

    private fun bind(image: Int, title: Int, sub: Int, body: Int) {
        BackBar.bind(this, getString(title))
        findViewById<ImageView>(R.id.hero).setImageResource(image)
        findViewById<TextView>(R.id.title).setText(title)
        findViewById<TextView>(R.id.subtitle).setText(sub)
        findViewById<TextView>(R.id.body).setText(body)
    }

    companion object {
        const val EXTRA_ID = "guide_id"
    }
}
