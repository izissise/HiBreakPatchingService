package net.izissise.hibreak_comp_service.command_runners

interface CommandRunner {
    fun runCommands(cmds: Array<String>)
    fun onDestroy()
}
