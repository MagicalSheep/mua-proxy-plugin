package cn.magicalsheep.request

import com.google.gson.annotations.SerializedName

data class CloseProxy(
    val user: User,
    @SerializedName(value = "proxy_name")
    val proxyName: String
)