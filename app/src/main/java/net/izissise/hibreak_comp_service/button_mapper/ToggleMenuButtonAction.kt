package net.izissise.hibreak_comp_service.button_mapper

import android.content.Context
import net.izissise.hibreak_comp_service.HibreakAccessibilityService

class ToggleMenuButtonAction : ButtonAction {
    override fun execute(context: Context) {
        (context as? HibreakAccessibilityService)?.openFloatingMenu()
    }
}
