package mx.reddeayuda.app

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FirstAidDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_aid_detail)
        SosBanner.bind(this, findViewById(R.id.sosBanner))

        val topic = FirstAidCatalog.byId(intent.getStringExtra(EXTRA_TOPIC).orEmpty())
        if (topic == null) {
            finish()
            return
        }

        BackBar.bind(this, getString(topic.title))
        findViewById<ImageView>(R.id.hero).setImageResource(topic.cover)
        findViewById<TextView>(R.id.title).setText(topic.title)
        findViewById<TextView>(R.id.subtitle).setText(topic.subtitle)
        findViewById<TextView>(R.id.intro).setText(topic.intro)

        val box = findViewById<LinearLayout>(R.id.steps)
        val inflater = LayoutInflater.from(this)
        topic.steps.forEachIndexed { index, step ->
            val row = inflater.inflate(R.layout.item_first_aid_step, box, false)
            row.findViewById<ImageView>(R.id.stepImage).setImageResource(step.image)
            row.findViewById<TextView>(R.id.stepNumber).text =
                getString(R.string.fa_step_n, index + 1, topic.steps.size)
            row.findViewById<TextView>(R.id.stepTitle).setText(step.title)
            row.findViewById<TextView>(R.id.stepBody).setText(step.body)
            box.addView(row)
        }
    }

    companion object {
        const val EXTRA_TOPIC = "topic_id"
    }
}
