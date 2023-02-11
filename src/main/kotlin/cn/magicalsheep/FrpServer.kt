package cn.magicalsheep

import java.io.IOException
import java.lang.UnsupportedOperationException
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

class FrpServer(var frpPath: String, var frpConfigPath: String) {

    private var process: Process? = null

    @Synchronized
    @Throws(IOException::class, UnsupportedOperationException::class)
    fun start() {
        if (process != null) throw IOException("Frp server is running")
        val cmd = listOf(frpPath, "-c", frpConfigPath)
        process = ProcessBuilder(cmd).inheritIO().start()
    }

    @Synchronized
    fun stop() {
        // process is not running
        if (process == null) return
        // process killed by system
        if (!process!!.isAlive) {
            process = null
            return
        }
        // process cannot be killed gracefully
        val supportsNormalTermination = try {
            process!!.supportsNormalTermination()
        } catch (ex: UnsupportedOperationException) {
            false
        }
        if (supportsNormalTermination) {
            process!!.destroy()
        } else {
            process!!.destroyForcibly()
        }
        // wait for process killed
        process!!.waitFor(3000, TimeUnit.MILLISECONDS)
        if (!process!!.isAlive) {
            process = null
            return
        }

        // plugin cannot kill the process, throw exception
        val pid = try {
            process!!.pid()
        } catch (ex: UnsupportedOperationException) {
            -1
        }
        process = null
        throw Exception("Close frp server failed, please kill the task(PID $pid) manually")
    }

}