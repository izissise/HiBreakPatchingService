package net.izissise.hibreak_comp_service.command_runners

object Commands {
    fun BACKLIGHT_BRIGHTNESS(v: Int) = "bl ${v}"
    fun AUTO_CLEAR(v: Boolean) = "ac ${v}"
    fun ANTI_FLICKER(v: Boolean) = "af ${v}"
}
