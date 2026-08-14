package mx.reddeayuda.app

object FirstAidCatalog {

    data class Step(
        val image: Int,
        val title: Int,
        val body: Int
    )

    data class Topic(
        val id: String,
        val title: Int,
        val subtitle: Int,
        val cover: Int,
        val intro: Int,
        val steps: List<Step>
    )

    val topics: List<Topic> = listOf(
        Topic(
            id = "scene",
            title = R.string.fa_scene_title,
            subtitle = R.string.fa_scene_sub,
            cover = R.drawable.rcp_escena,
            intro = R.string.fa_scene_intro,
            steps = listOf(
                Step(R.drawable.rcp_escena, R.string.fa_scene_s1_t, R.string.fa_scene_s1_b),
                Step(R.drawable.rcp_llamar, R.string.fa_scene_s2_t, R.string.fa_scene_s2_b),
                Step(R.drawable.rcp_revisar, R.string.fa_scene_s3_t, R.string.fa_scene_s3_b)
            )
        ),
        Topic(
            id = "cpr",
            title = R.string.fa_cpr_title,
            subtitle = R.string.fa_cpr_sub,
            cover = R.drawable.rcp_compresiones,
            intro = R.string.fa_cpr_intro,
            steps = listOf(
                Step(R.drawable.rcp_escena, R.string.fa_cpr_s1_t, R.string.fa_cpr_s1_b),
                Step(R.drawable.rcp_revisar, R.string.fa_cpr_s2_t, R.string.fa_cpr_s2_b),
                Step(R.drawable.rcp_llamar, R.string.fa_cpr_s3_t, R.string.fa_cpr_s3_b),
                Step(R.drawable.rcp_compresiones, R.string.fa_cpr_s4_t, R.string.fa_cpr_s4_b),
                Step(R.drawable.rcp_ventilaciones, R.string.fa_cpr_s5_t, R.string.fa_cpr_s5_b),
                Step(R.drawable.rcp_dea, R.string.fa_cpr_s6_t, R.string.fa_cpr_s6_b)
            )
        ),
        Topic(
            id = "aed",
            title = R.string.fa_aed_title,
            subtitle = R.string.fa_aed_sub,
            cover = R.drawable.rcp_dea,
            intro = R.string.fa_aed_intro,
            steps = listOf(
                Step(R.drawable.rcp_llamar, R.string.fa_aed_s1_t, R.string.fa_aed_s1_b),
                Step(R.drawable.rcp_dea, R.string.fa_aed_s2_t, R.string.fa_aed_s2_b),
                Step(R.drawable.rcp_dea, R.string.fa_aed_s3_t, R.string.fa_aed_s3_b),
                Step(R.drawable.rcp_compresiones, R.string.fa_aed_s4_t, R.string.fa_aed_s4_b)
            )
        ),
        Topic(
            id = "choke",
            title = R.string.fa_choke_title,
            subtitle = R.string.fa_choke_sub,
            cover = R.drawable.rcp_ahogo,
            intro = R.string.fa_choke_intro,
            steps = listOf(
                Step(R.drawable.rcp_ahogo, R.string.fa_choke_s1_t, R.string.fa_choke_s1_b),
                Step(R.drawable.rcp_golpes_espalda, R.string.fa_choke_s2_t, R.string.fa_choke_s2_b),
                Step(R.drawable.rcp_ahogo, R.string.fa_choke_s3_t, R.string.fa_choke_s3_b),
                Step(R.drawable.rcp_llamar, R.string.fa_choke_s4_t, R.string.fa_choke_s4_b),
                Step(R.drawable.rcp_compresiones, R.string.fa_choke_s5_t, R.string.fa_choke_s5_b)
            )
        ),
        Topic(
            id = "bleed",
            title = R.string.fa_bleed_title,
            subtitle = R.string.fa_bleed_sub,
            cover = R.drawable.rcp_hemorragia,
            intro = R.string.fa_bleed_intro,
            steps = listOf(
                Step(R.drawable.rcp_escena, R.string.fa_bleed_s1_t, R.string.fa_bleed_s1_b),
                Step(R.drawable.rcp_hemorragia, R.string.fa_bleed_s2_t, R.string.fa_bleed_s2_b),
                Step(R.drawable.rcp_hemorragia, R.string.fa_bleed_s3_t, R.string.fa_bleed_s3_b),
                Step(R.drawable.rcp_llamar, R.string.fa_bleed_s4_t, R.string.fa_bleed_s4_b)
            )
        ),
        Topic(
            id = "recovery",
            title = R.string.fa_recovery_title,
            subtitle = R.string.fa_recovery_sub,
            cover = R.drawable.rcp_lateral,
            intro = R.string.fa_recovery_intro,
            steps = listOf(
                Step(R.drawable.rcp_revisar, R.string.fa_recovery_s1_t, R.string.fa_recovery_s1_b),
                Step(R.drawable.rcp_lateral, R.string.fa_recovery_s2_t, R.string.fa_recovery_s2_b),
                Step(R.drawable.rcp_llamar, R.string.fa_recovery_s3_t, R.string.fa_recovery_s3_b),
                Step(R.drawable.rcp_compresiones, R.string.fa_recovery_s4_t, R.string.fa_recovery_s4_b)
            )
        ),
        Topic(
            id = "neck",
            title = R.string.fa_neck_title,
            subtitle = R.string.fa_neck_sub,
            cover = R.drawable.rcp_no_cuello,
            intro = R.string.fa_neck_intro,
            steps = listOf(
                Step(R.drawable.rcp_escena, R.string.fa_neck_s1_t, R.string.fa_neck_s1_b),
                Step(R.drawable.rcp_no_cuello, R.string.fa_neck_s2_t, R.string.fa_neck_s2_b),
                Step(R.drawable.rcp_llamar, R.string.fa_neck_s3_t, R.string.fa_neck_s3_b),
                Step(R.drawable.rcp_revisar, R.string.fa_neck_s4_t, R.string.fa_neck_s4_b)
            )
        )
    )

    fun byId(id: String): Topic? = topics.find { it.id == id }
}
