package mx.reddeayuda.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FirstAidMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_aid_menu)
        SosBanner.bind(this, findViewById(R.id.sosBanner))
        BackBar.bind(this, getString(R.string.guide_rcp_title))

        val list = findViewById<LinearLayout>(R.id.topicList)
        val inflater = LayoutInflater.from(this)
        FirstAidCatalog.topics.forEach { topic ->
            val row = inflater.inflate(R.layout.item_guide_card, list, false)
            row.findViewById<ImageView>(R.id.guideImage).setImageResource(topic.cover)
            row.findViewById<TextView>(R.id.guideTitle).setText(topic.title)
            row.findViewById<TextView>(R.id.guideSub).setText(topic.subtitle)
            row.setOnClickListener {
                startActivity(
                    Intent(this, FirstAidDetailActivity::class.java)
                        .putExtra(FirstAidDetailActivity.EXTRA_TOPIC, topic.id)
                )
            }
            list.addView(row)
        }
    }
}
