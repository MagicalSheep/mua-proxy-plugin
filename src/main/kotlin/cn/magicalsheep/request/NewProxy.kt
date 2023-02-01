package cn.magicalsheep.request

import com.google.gson.annotations.SerializedName

data class NewProxy(
    val user: User,
    @SerializedName(value = "proxy_name")
    val proxyName: String,
    @SerializedName(value = "proxy_type")
    val proxyType: String,
    @SerializedName(value = "use_encryption")
    val useEncryption: Boolean,
    @SerializedName(value = "use_compression")
    val useCompression: Boolean,
    val group: String?,
    @SerializedName(value = "group_key")
    val groupKey: String?,

    // tcp and udp only
    @SerializedName(value = "remote_port")
    val remotePort: Int,

    // http and https only
    @SerializedName(value = "custom_domains")
    val customDomains: List<String>?,
    val subdomain: String?,
    val locations: String?,
    @SerializedName(value = "http_user")
    val httpUser: String?,
    @SerializedName(value = "http_pwd")
    val httpPwd: String?,
    @SerializedName(value = "host_header_rewrite")
    val hostHeaderRewrite: String?,
    val headers: Map<String, String>?,

    // stcp only
    val sk: String?,

    // tcpmux only
    val multiplexer: String?,

    val metas: Map<String, String>?
)
