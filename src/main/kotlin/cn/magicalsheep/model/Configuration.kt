package cn.magicalsheep.model

import cn.magicalsheep.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import kotlin.jvm.Throws
import kotlin.math.max
import kotlin.math.min

class Configuration {
    var port: Int = DEFAULT_PORT; private set
    var frpAddress: String = DEFAULT_FRP_ADDRESS; private set
    var isRestrictPort: Boolean = DEFAULT_RESTRICT_PORT; private set
    var availableMinPort: Int = DEFAULT_AVAILABLE_MIN_PORT; private set
    var availableMaxPort: Int = DEFAULT_AVAILABLE_MAX_PORT; private set
    var isFrpIntegration: Boolean = DEFAULT_FRP_INTEGRATION; private set
    var frpPath: String = DEFAULT_FRP_PATH; private set
    var frpConfigPath: String = DEFAULT_FRP_CONFIG_PATH; private set

    private fun update(newConfig: Configuration) {
        port = newConfig.port
        frpAddress = newConfig.frpAddress
        isRestrictPort = newConfig.isRestrictPort
        availableMaxPort = newConfig.availableMaxPort
        availableMinPort = newConfig.availableMinPort
        isFrpIntegration = newConfig.isFrpIntegration
        frpPath = newConfig.frpPath
        frpConfigPath = newConfig.frpConfigPath
    }

    private fun toProperties(): Properties {
        val properties = Properties()
        properties[CONFIG_PORT_FIELD] = port.toString()
        properties[CONFIG_FRP_ADDRESS_FIELD] = frpAddress
        properties[CONFIG_RESTRICT_PORT_FIELD] = isRestrictPort.toString()
        properties[CONFIG_AVAILABLE_PORTS_FIELD] = "$availableMinPort-$availableMaxPort"
        properties[CONFIG_FRP_INTEGRATION_FIELD] = isFrpIntegration.toString()
        properties[CONFIG_FRP_PATH_FIELD] = frpPath
        properties[CONFIG_FRP_CONFIG_PATH_FIELD] = frpConfigPath
        return properties
    }

    @Throws(Exception::class)
    private fun fromProperties(properties: Properties): Boolean {
        // Atomically update
        val newConfig = Configuration()
        var isNeedToStore = false

        properties.getProperty(CONFIG_FRP_ADDRESS_FIELD).let {
            if (it == null) {
                isNeedToStore = true
                return@let
            }
            newConfig.frpAddress = it
        }
        properties.getProperty(CONFIG_FRP_PATH_FIELD).let {
            if (it == null) {
                isNeedToStore = true
                return@let
            }
            newConfig.frpPath = it
        }
        properties.getProperty(CONFIG_FRP_CONFIG_PATH_FIELD).let {
            if (it == null) {
                isNeedToStore = true
                return@let
            }
            newConfig.frpConfigPath = it
        }
        properties.getProperty(CONFIG_AVAILABLE_PORTS_FIELD).let { rawPorts ->
            if (rawPorts == null) {
                isNeedToStore = true
                return@let
            }
            val tmpList = rawPorts.split("-")
            if (tmpList.size >= 2) {
                val val0 = tmpList[0].toInt()
                val val1 = tmpList[1].toInt()
                newConfig.availableMinPort = min(val0, val1)
                newConfig.availableMaxPort = max(val0, val1)
            }
        }
        properties.getProperty(CONFIG_PORT_FIELD).let {
            if (it == null) {
                isNeedToStore = true
                return@let
            }
            newConfig.port = it.toInt()
        }
        properties.getProperty(CONFIG_RESTRICT_PORT_FIELD).let {
            if (it == null) {
                isNeedToStore = true
                return@let
            }
            newConfig.isRestrictPort = it.toBoolean()
        }
        properties.getProperty(CONFIG_FRP_INTEGRATION_FIELD).let {
            if (it == null) {
                isNeedToStore = true
                return@let
            }
            newConfig.isFrpIntegration = it.toBoolean()
        }

        update(newConfig)
        return isNeedToStore
    }

    @Throws(Exception::class)
    fun load(config: File) {
        if (config.createNewFile()) {
            save(config)
            return
        }
        val properties = Properties()
        properties.load(FileInputStream(config))
        val isNeedToStore = fromProperties(properties)
        if (isNeedToStore) {
            save(config)
        }
    }

    private fun save(config: File) {
        toProperties().store(FileOutputStream(config), "MUA Proxy Plugin Configuration")
    }
}
