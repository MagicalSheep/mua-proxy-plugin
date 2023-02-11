package cn.magicalsheep.model.response

import com.google.gson.annotations.SerializedName

data class FrpResponse(
    val reject: Boolean? = null,
    @SerializedName(value = "reject_reason")
    val rejectReason: String? = null,
    @SerializedName(value = "unchange")
    val unchanged: Boolean? = null,
    val content: Any? = null
)