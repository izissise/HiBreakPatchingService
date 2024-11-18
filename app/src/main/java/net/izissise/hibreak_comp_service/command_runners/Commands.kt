package net.izissise.hibreak_comp_service.command_runners

object Commands {
    fun BACKLIGHT_BRIGHTNESS(v: Int) = "bl ${v}"
    fun AUTO_CLEAR(v: Boolean) = "ac ${v}"
    fun ANTI_FLICKER(v: Boolean) = "af ${v}"

    const val FORCE_CLEAR = "_unimplemented_"
    const val COMMIT_BITMAP = "_unimplemented_"
    const val SPEED_CLEAR = "_unimplemented_"
    const val SPEED_BALANCED = "_unimplemented_"
    const val SPEED_SMOOTH = "_unimplemented_"
    const val SPEED_FAST = "_unimplemented_"
}
