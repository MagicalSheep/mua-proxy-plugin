package cn.magicalsheep.request

import com.google.gson.annotations.SerializedName

data class Login(
    val version: String,
    val hostname: String,
    val os: String,
    val arch: String,
    val user: String,
    val timestamp: Long,
    @SerializedName(value = "privilege_key")
    val privilegeKey: String,
    @SerializedName(value = "run_id")
    val runId: String,
    @SerializedName(value = "pool_count")
    val poolCount: Int,
    val metas: Map<String, String>?,
    @SerializedName(value = "client_address")
    val clientAddress: String
)
