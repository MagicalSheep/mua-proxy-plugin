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
import cn.magicalsheep.request.Login
import cn.magicalsheep.request.NewProxy
import cn.magicalsheep.response.FrpResponse
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.Exception
import kotlin.io.path.exists

@Plugin(
    id = "mua-proxy-plugin",
    name = "MUA Union Proxy Plugin",
    version = "0.2.0-SNAPSHOT",
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

        const val CONFIG_PORT_FIELD = "port"
        const val CONFIG_FRP_ADDRESS_FIELD = "frp_address"

        const val META_DOMAIN = "domain"
        const val META_FORCED_HOSTS = "forced_hosts"
    }

    private var port = DEFAULT_PORT
    private var frpAddress = DEFAULT_FRP_ADDRESS

    private val api: Javalin
    private val forcedHosts = ConcurrentHashMap<String, ImmutableList<String>>()

    private val gson = GsonBuilder().create()
    private val gsonMapper = object : JsonMapper {
        override fun <T : Any> fromJsonString(json: String, targetType: Type): T =
            gson.fromJson(json, targetType)

        override fun toJsonString(obj: Any, type: Type) =
            gson.toJson(obj)
    }

    private fun login(ctx: Context) {
        val req =
            ctx.bodyAsClass<FrpRequest<Login>>(getType(FrpRequest::class.java, Login::class.java)).content

        // check forced hosts meta
        val metas = req.metas
        if (metas == null || !metas.containsKey(META_DOMAIN) || !metas.containsKey(META_FORCED_HOSTS)) {
            ctx.json(FrpResponse(reject = false, unchanged = true))
            return
        }

        val hosts = metas[META_FORCED_HOSTS]
        try {
            val hostsList = gson.fromJson<List<String>>(hosts, getType(List::class.java, String::class.java))
            val domain = metas[META_DOMAIN]
            if (domain == null) {
                ctx.json(FrpResponse(reject = true, rejectReason = "Domain cannot be null"))
                return
            }
//            if (forcedHosts.containsKey(domain)) {
//                ctx.json(
//                    FrpResponse(
//                        reject = true,
//                        rejectReason = "This domain has already registered a forced hosts list"
//                    )
//                )
//                return
//            }
            // override it
            forcedHosts[domain] = ImmutableList.copyOf(hostsList)
            try {
                updateForcedHosts(ImmutableMap.copyOf(forcedHosts))
            } catch (ex: Exception) {
                // rollback
                forcedHosts.remove(domain)
                ctx.json(
                    FrpResponse(
                        reject = true,
                        rejectReason = "There is an error when update forced hosts, please contact administrator. (${ex.message})"
                    )
                )
                logger.error("Error happen when try to update the forced hosts, this is often caused by updating Velocity")
                ex.printStackTrace()
                return
            }
            logger.info("Register forced hosts for domain <$domain>: $hostsList")
            ctx.json(FrpResponse(reject = false, unchanged = true))
        } catch (ex: Exception) {
            ctx.json(FrpResponse(reject = true, rejectReason = "Invalid forced hosts syntax"))
        }
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

        // remove forced hosts if needed
        val metas = req.user.metas ?: return
        if (!metas.containsKey(META_DOMAIN) || !metas.containsKey(META_FORCED_HOSTS)) return
        val domain = metas[META_DOMAIN]
        if (!forcedHosts.containsKey(domain)) return
        val hosts = forcedHosts[domain] ?: return
        for (host in hosts) {
            if (server.allServers.any { it.serverInfo.name == host }) {
                return
            }
        }
        forcedHosts.remove(domain)
        // if we can add forced hosts successfully, then it should not be failed
        try {
            updateForcedHosts(ImmutableMap.copyOf(forcedHosts))
        } catch (ex: Exception) {
            logger.error("Cannot remove the forced hosts, it must be a bug")
            ex.printStackTrace()
        }
        logger.info("Unregister forced hosts for domain <$domain>: $hosts")
    }

    init {
        this.api = Javalin.create {
            it.jsonMapper(gsonMapper)
        }
            .post("/register") { ctx -> register(ctx) }
            .post("/unregister") { ctx -> unregister(ctx) }
            .post("/login") { ctx -> login(ctx) }

        val properties = Properties()
        if (!dataDirectory.exists()) {
            File(dataDirectory.toUri()).mkdir()
        }
        val config = File(dataDirectory.resolve(CONFIG_NAME).toUri())
        if (config.createNewFile()) {
            properties[CONFIG_PORT_FIELD] = DEFAULT_PORT.toString()
            properties[CONFIG_FRP_ADDRESS_FIELD] = DEFAULT_FRP_ADDRESS
            properties.store(FileOutputStream(config), "MUA Proxy Plugin Configuration")
        } else {
            properties.load(FileInputStream(config))
            try {
                port = properties.getProperty(CONFIG_PORT_FIELD).toInt()
                frpAddress = properties.getProperty(CONFIG_FRP_ADDRESS_FIELD) ?: DEFAULT_FRP_ADDRESS
            } catch (ex: Exception) {
                // ignored
            }
        }
        logger.info("Loading MUA union proxy plugin...")
    }

    @Throws(ClassNotFoundException::class, NoSuchFieldException::class, Exception::class)
    private fun updateForcedHosts(newForcedHosts: ImmutableMap<String, ImmutableList<String>>) {
        val configClass = Class.forName("com.velocitypowered.proxy.config.VelocityConfiguration")
        val forcedHostsField = configClass.getDeclaredField("forcedHosts")
        forcedHostsField.isAccessible = true
        val forcedHosts = forcedHostsField.get(server.configuration)
        val forcedHostsClass = Class.forName("com.velocitypowered.proxy.config.VelocityConfiguration\$ForcedHosts")
        val forcedHostsMapField =
            forcedHostsClass.getDeclaredField("forcedHosts")
        forcedHostsMapField.isAccessible = true
        forcedHostsMapField.set(forcedHosts, newForcedHosts)
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