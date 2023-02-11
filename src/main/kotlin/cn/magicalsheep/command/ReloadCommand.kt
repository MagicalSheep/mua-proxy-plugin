package cn.magicalsheep.command

import cn.magicalsheep.Main
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ConsoleCommandSource

class ReloadCommand(private val plugin: Main) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation?) {
        val args = invocation!!.arguments()
        if (args.isEmpty() || args[0] != "reload") {
            plugin.logger.info("Unknown command!")
            return
        }
        if (invocation.source() !is ConsoleCommandSource) return
        if (args.size >= 2 && args[1] == "all") plugin.reload(true)
        else plugin.reload()
    }
}