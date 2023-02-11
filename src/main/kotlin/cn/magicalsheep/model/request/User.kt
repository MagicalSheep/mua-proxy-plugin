package cn.magicalsheep.model.request

import com.google.gson.annotations.SerializedName

data class User(
    val user: String,
    val metas: Map<String, String>?,
    @SerializedName(value = "run_id")
    val runId: String
)
