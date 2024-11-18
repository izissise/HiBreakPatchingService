package net.izissise.hibreak_comp_service.button_mapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.content.Context

class RecentsButtonAction : ButtonAction {
    override fun execute(context: Context) {
        (context as? AccessibilityService)?.performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
}