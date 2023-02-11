package cn.magicalsheep

import cn.magicalsheep.command.ReloadCommand
import cn.magicalsheep.model.Configuration
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
import cn.magicalsheep.model.request.CloseProxy
import cn.magicalsheep.model.request.FrpRequest
import cn.magicalsheep.model.request.Login
import cn.magicalsheep.model.request.NewProxy
import cn.magicalsheep.model.response.FrpResponse
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.Exception
import kotlin.io.path.exists

@Plugin(
    id = "mua-proxy-plugin",
    name = "MUA Union Proxy Plugin",
    version = "0.3.0-SNAPSHOT",
    url = "https://github.com/MagicalSheep/mua-proxy-plugin",
    description = "Register server when frp gets a new proxy connection",
    authors = ["MagicalSheep"]
)
class Main @Inject constructor(
    private val server: ProxyServer,
    val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {

    companion object {
        private fun getType(raw: Class<*>, vararg args: Type) = object : ParameterizedType {
            override fun getRawType(): Type = raw
            override fun getActualTypeArguments(): Array<out Type> = args
            override fun getOwnerType(): Type? = null
        }
    }

    private val initServers: Set<String>
    private val config = Configuration()
    private var api: Javalin
    private val frpServer: FrpServer
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
        // check forced hosts alias meta
        val domainAlias = if (metas.containsKey(META_DOMAIN_ALIAS)) {
            try {
                gson.fromJson<List<String>>(metas[META_DOMAIN_ALIAS], getType(List::class.java, String::class.java))
            } catch (ex: Exception) {
                listOf()
            }
        } else {
            listOf()
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
            val tmpMap = HashMap<String, ImmutableList<String>>()
            tmpMap[domain] = ImmutableList.copyOf(hostsList)
            domainAlias.forEach { tmpMap[it] = ImmutableList.copyOf(hostsList) }
            forcedHosts.putAll(tmpMap)
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
            if (domainAlias.isNotEmpty()) {
                logger.info("Register alias for domain <$domain>: $domainAlias")
            }
            ctx.json(FrpResponse(reject = false, unchanged = true))
        } catch (ex: Exception) {
            ctx.json(FrpResponse(reject = true, rejectReason = "Invalid forced hosts syntax"))
        }
    }

    private fun register(ctx: Context) {
        val req =
            ctx.bodyAsClass<FrpRequest<NewProxy>>(getType(FrpRequest::class.java, NewProxy::class.java)).content
        if (config.isRestrictPort && req.remotePort !in config.availableMinPort..config.availableMaxPort) {
            ctx.json(
                FrpResponse(
                    reject = true,
                    rejectReason = "You can only use remote ports from ${config.availableMinPort} to ${config.availableMaxPort}"
                )
            )
            return
        }
        if (req.proxyType != "tcp") {
            ctx.json(FrpResponse(reject = true, rejectReason = "Proxy type must be TCP"))
            return
        }
        if (server.allServers.any { it.serverInfo.name == req.proxyName }) {
            ctx.json(FrpResponse(reject = true, rejectReason = "Server name <${req.proxyName}> is already in use"))
            return
        }
        if (req.remotePort == config.port || server.allServers.any { it.serverInfo.address.port == req.remotePort }) {
            ctx.json(FrpResponse(reject = true, rejectReason = "Remote port ${req.remotePort} is already in use"))
            return
        }
        val registeredServer =
            server.registerServer(ServerInfo(req.proxyName, InetSocketAddress(config.frpAddress, req.remotePort)))
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
        val domain = metas[META_DOMAIN] ?: return
        val domainAlias = if (metas.containsKey(META_DOMAIN_ALIAS)) {
            try {
                gson.fromJson<List<String>>(metas[META_DOMAIN_ALIAS], getType(List::class.java, String::class.java))
            } catch (ex: Exception) {
                listOf()
            }
        } else {
            listOf()
        }
        // just check main domain because other domains are alias
        // we assume that no domain override happens
        // maybe fix it in the future
        if (!forcedHosts.containsKey(domain)) return
        val hosts = forcedHosts[domain] ?: return
        for (host in hosts) {
            if (server.allServers.any { it.serverInfo.name == host }) {
                return
            }
        }
        forcedHosts.remove(domain)
        domainAlias.forEach { forcedHosts.remove(it) }
        // if we can add forced hosts successfully, then it should not be failed
        try {
            updateForcedHosts(ImmutableMap.copyOf(forcedHosts))
        } catch (ex: Exception) {
            logger.error("Cannot remove the forced hosts, it must be a bug")
            ex.printStackTrace()
        }
        logger.info("Unregister forced hosts for domain <$domain>: $hosts")
        if (domainAlias.isNotEmpty()) {
            logger.info("Unregister alias for domain <$domain>: $domainAlias")
        }
    }

    private fun startFrp() {
        if (!config.isFrpIntegration) return
        try {
            frpServer.start()
        } catch (ex: Exception) {
            logger.error(ex.message)
            logger.error("Start frp server failed. Please start it manually or try to reload the plugin")
        }
    }

    private fun stopFrp() {
        if (!config.isFrpIntegration) return
        try {
            frpServer.stop()
        } catch (ex: Exception) {
            logger.error(ex.message)
            logger.error("Plugin close the frp server failed")
        }
    }

    private fun createApi(): Javalin {
        return Javalin.create {
            it.jsonMapper(gsonMapper)
        }
            .post("/register") { ctx -> register(ctx) }
            .post("/unregister") { ctx -> unregister(ctx) }
            .post("/login") { ctx -> login(ctx) }
            .events { event ->
                event.serverStarted { startFrp() }
                event.serverStopped { stopFrp() }
            }
    }

    fun reload(isAllReload: Boolean = false) {
        logger.info("Reloading MUA union proxy plugin configuration...")
        if (isAllReload) {
            server.allServers.forEach {
                if (!initServers.contains(it.serverInfo.name))
                    server.unregisterServer(it.serverInfo)
            }
            forcedHosts.clear()
            updateForcedHosts(ImmutableMap.copyOf(forcedHosts))
            api.close()
            this.api = createApi()
        }
        try {
            val configFile = File(dataDirectory.resolve(CONFIG_NAME).toUri())
            config.load(configFile)
        } catch (ex: Exception) {
            logger.error("Failed to reload configuration: ${ex.message}")
            return
        }
        frpServer.frpPath = config.frpPath
        frpServer.frpConfigPath = config.frpConfigPath
        if (isAllReload) api.start(config.port)
        logger.info("Reload configuration completed")
    }

    init {
        logger.info("Loading MUA union proxy plugin...")
        initServers = HashSet(server.allServers.map { it.serverInfo.name })
        if (!dataDirectory.exists()) {
            File(dataDirectory.toUri()).mkdir()
        }
        val configFile = File(dataDirectory.resolve(CONFIG_NAME).toUri())
        try {
            config.load(configFile)
        } catch (ex: Exception) {
            logger.error("Failed to load configuration: ${ex.message}")
        }
        frpServer = FrpServer(config.frpPath, config.frpConfigPath)
        this.api = createApi()
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
    @Suppress("UNUSED_PARAMETER")
    fun onProxyInitialization(event: ProxyInitializeEvent?) {
        api.start(config.port)
        server.commandManager.register("muaproxy", ReloadCommand(this), "muap")
        logger.info("MUA union proxy plugin loaded")
    }

    @Subscribe
    @Suppress("UNUSED_PARAMETER")
    fun onProxyShutdown(event: ProxyShutdownEvent?) {
        api.close()
        logger.info("MUA union proxy plugin exited")
    }

}