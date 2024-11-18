package net.izissise.hibreak_comp_service.button_mapper

import SystemSettingsManager
import android.content.Context

class ToggleNightLightAction : ButtonAction {
    override fun execute(context: Context) {
        SystemSettingsManager.toggleNightLightMode(context)
    }
}
