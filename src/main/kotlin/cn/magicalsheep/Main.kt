package cn.magicalsheep

import com.google.gson.GsonBuilder
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.json.JsonMapper
import org.slf4j.Logger
import cn.magicalsheep.request.CloseProxy
import cn.magicalsheep.request.FrpRequest
import cn.magicalsheep.request.NewProxy
import cn.magicalsheep.response.FrpResponse
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import org.apache.commons.validator.routines.InetAddressValidator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Exception
import java.lang.reflect.Type
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists

@Plugin(
    id = "mua-proxy-plugin",
    name = "MUA Union Proxy Plugin",
    version = "0.1.2-SNAPSHOT",
    url = "https://github.com/MagicalSheep/mua-proxy-plugin",
    description = "Register server when frp gets a new proxy connection",
    authors = ["MagicalSheep"]
)
class Main @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory dataDirectory: Path
) {

    companion object {
        const val CONFIG_NAME = "config.properties"
        const val DEFAULT_PORT = 8080
        const val DEFAULT_FRP_ADDRESS = "127.0.0.1"
        const val PORT_CONFIG = "port"
        const val FRP_ADDRESS_CONFIG = "frp_address"
    }

    private var port = DEFAULT_PORT
    private var frpAddress = DEFAULT_FRP_ADDRESS
    private val api: Javalin

    private val gson = GsonBuilder().create()
    private val gsonMapper = object : JsonMapper {
        override fun <T : Any> fromJsonString(json: String, targetType: Type): T =
            gson.fromJson(json, targetType)

        override fun toJsonString(obj: Any, type: Type) =
            gson.toJson(obj)
    }

    private fun register(ctx: Context) {
        val req =
            ctx.bodyAsClass<FrpRequest<NewProxy>>(getType(FrpRequest::class.java, NewProxy::class.java)).content
        if (req.proxyType != "tcp") {
            ctx.json(FrpResponse(reject = true, rejectReason = "Proxy type must be TCP"))
            return
        }
        if (server.allServers.any { it.serverInfo.name == req.proxyName }) {
            ctx.json(FrpResponse(reject = true, rejectReason = "Server name <${req.proxyName}> is already in use"))
            return
        }
        if (req.remotePort == port || server.allServers.any { it.serverInfo.address.port == req.remotePort }) {
            ctx.json(FrpResponse(reject = true, rejectReason = "Remote port ${req.remotePort} is already in use"))
            return
        }
        val registeredServer =
            server.registerServer(ServerInfo(req.proxyName, InetSocketAddress(frpAddress, req.remotePort)))
        logger.info("${registeredServer.serverInfo} registered")
        ctx.json(FrpResponse(reject = false, unchanged = true))
    }

    private fun unregister(ctx: Context) {
        ctx.json(FrpResponse(reject = false, unchanged = true))
        val req =
            ctx.bodyAsClass<FrpRequest<CloseProxy>>(getType(FrpRequest::class.java, CloseProxy::class.java)).content
        val target = server.allServers.find { it.serverInfo.name == req.proxyName } ?: return
        server.unregisterServer(target.serverInfo)
        logger.info("${target.serverInfo} unregistered")
    }

    init {
        this.api = Javalin.create {
            it.jsonMapper(gsonMapper)
        }
            .post("/register") { ctx -> register(ctx) }
            .post("/unregister") { ctx -> unregister(ctx) }

        val properties = Properties()
        if (!dataDirectory.exists()) {
            File(dataDirectory.toUri()).mkdir()
        }
        val config = File(dataDirectory.resolve(CONFIG_NAME).toUri())
        if (config.createNewFile()) {
            properties[PORT_CONFIG] = DEFAULT_PORT.toString()
            properties[FRP_ADDRESS_CONFIG] = DEFAULT_FRP_ADDRESS
            properties.store(FileOutputStream(config), "MUA Proxy Plugin Configuration")
        } else {
            properties.load(FileInputStream(config))
            try {
                port = properties.getProperty(PORT_CONFIG).toInt()
                frpAddress = properties.getProperty(FRP_ADDRESS_CONFIG) ?: DEFAULT_FRP_ADDRESS
                InetAddressValidator.getInstance().isValidInet4Address(frpAddress)
            } catch (ex: Exception) {
                frpAddress = DEFAULT_FRP_ADDRESS
            }
        }
        logger.info("Loading MUA union proxy plugin...")
    }

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent?) {
        api.start(port)
        logger.info("MUA union proxy plugin loaded")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent?) {
        api.stop()
        logger.info("MUA union proxy plugin exited")
    }

}