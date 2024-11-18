package net.izissise.hibreak_comp_service

import SystemSettingsManager
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.PreferenceManager
import net.izissise.hibreak_comp_service.button_mapper.ButtonActionManager
import net.izissise.hibreak_comp_service.command_runners.CommandRunner
import net.izissise.hibreak_comp_service.command_runners.Commands
import net.izissise.hibreak_comp_service.command_runners.UnixSocketCommandRunner
import net.izissise.hibreak_comp_service.databinding.FloatingMenuLayoutBinding
import kotlin.math.max


class HibreakAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var commandRunner: CommandRunner
    private lateinit var refreshModeManager: RefreshModeManager
    private lateinit var staticAODOpacityManager: StaticAODOpacityManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var buttonActionManager: ButtonActionManager
    private var isScreenOn = true

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    menuBinding.close()
                    if (sharedPreferences.getBoolean("refresh_on_lock", false))
                        handler.postDelayed({
                            commandRunner.runCommands(arrayOf(Commands.SPEED_CLEAR))
                        }, 150)
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (sharedPreferences.getBoolean("refresh_on_lock", false))
                        refreshModeManager.applyMode()
                }
            }
        }
    }
    private val receiverEink: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if(!sharedPreferences.getBoolean("allow_custom_broadcast", false))
                return

            when (intent.action) {
                "EINK_FORCE_CLEAR" -> {
                    commandRunner.runCommands(arrayOf(Commands.FORCE_CLEAR))
                }

                "EINK_REFRESH_SPEED_CLEAR" -> {
                    refreshModeManager.changeMode(RefreshMode.CLEAR)
                }

                "EINK_REFRESH_SPEED_BALANCED" -> {
                    refreshModeManager.changeMode(RefreshMode.BALANCED)
                }

                "EINK_REFRESH_SPEED_SMOOTH" -> {
                    refreshModeManager.changeMode(RefreshMode.SMOOTH)
                }

                "EINK_REFRESH_SPEED_FAST" -> {
                    refreshModeManager.changeMode(RefreshMode.SPEED)
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        commandRunner =
            UnixSocketCommandRunner()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        refreshModeManager = RefreshModeManager(
            sharedPreferences,
            commandRunner
        )
        staticAODOpacityManager = StaticAODOpacityManager(
            sharedPreferences,
            commandRunner,
            resources.getStringArray(R.array.static_lockscreen_opacity_values).map(Integer::parseInt).toIntArray(),
            resources.getStringArray(R.array.static_lockscreen_bg_opacity_values).map(Integer::parseInt).toIntArray(),
        )

        buttonActionManager = ButtonActionManager(commandRunner)

        val filterScreen = IntentFilter()
        filterScreen.addAction(Intent.ACTION_SCREEN_ON)
        filterScreen.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(receiver, filterScreen)

        val filterEink = IntentFilter()
        filterEink.addAction("EINK_FORCE_CLEAR")
        filterEink.addAction("EINK_REFRESH_SPEED_CLEAR")
        filterEink.addAction("EINK_REFRESH_SPEED_BALANCED")
        filterEink.addAction("EINK_REFRESH_SPEED_SMOOTH")
        filterEink.addAction("EINK_REFRESH_SPEED_FAST")
        registerReceiver(receiverEink, filterEink, RECEIVER_EXPORTED)

        updateColorScheme(sharedPreferences)
        staticAODOpacityManager.applyMode()
        staticAODOpacityManager.applyReader()
        updateMaxBrightness(sharedPreferences)
    }

    override fun onInterrupt() {

    }

    private val hardwareGestureDetector = HardwareGestureDetector(
        object: HardwareGestureDetector.OnGestureListener{
            override fun onSinglePress() {
                if(isScreenOn)
                    buttonActionManager.executeSinglePress(this@HibreakAccessibilityService)
                else
                    buttonActionManager.executeSinglePressScreenOff(this@HibreakAccessibilityService)
            }

            override fun onDoublePress() {
                if(isScreenOn)
                    buttonActionManager.executeDoublePress(this@HibreakAccessibilityService)
                else
                    buttonActionManager.executeDoublePressScreenOff(this@HibreakAccessibilityService)
            }

            override fun onLongPress() {
                if(isScreenOn)
                    buttonActionManager.executeLongPress(this@HibreakAccessibilityService)
                else
                    buttonActionManager.executeLongPressScreenOff(this@HibreakAccessibilityService)
            }
        }
    )

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if(event.scanCode == 766){
            if (!Settings.canDrawOverlays(baseContext))
                    requestOverlayPermission()
            else
                hardwareGestureDetector.onKeyEvent(event.action, event.eventTime)
        }
        return super.onKeyEvent(event)
    }


    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("OverlayPermission", "Activity not found exception", e)
        }
    }

    private fun getAppUsableScreenSize(context: Context): Point {
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        return size
    }

    private fun getRealScreenSize(context: Context): Point {
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getRealSize(size)
        return size
    }

    private fun getNavBarHeight(): Int {
        val appUsableSize: Point = getAppUsableScreenSize(this)
        val realScreenSize: Point = getRealScreenSize(this)

        if (appUsableSize.y < realScreenSize.y)
            return (realScreenSize.y - appUsableSize.y)

        return 0
    }

    private var menuBinding: FloatingMenuLayoutBinding? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun openFloatingMenu() {
        try {
            menuBinding?.run {
                if (root.visibility == View.VISIBLE) {
                    root.visibility = View.GONE
                } else {
                    root.visibility = View.VISIBLE
                    lightSeekbar.progress = max(SystemSettingsManager.getBrightnessFromSetting(this@HibreakAccessibilityService) - 1, 0)
                    buttonNight.text = when(SystemSettingsManager.getNightLightMode(this@HibreakAccessibilityService)){
                        SystemSettingsManager.NightLightMode.Manual -> "Manual"
                        SystemSettingsManager.NightLightMode.Auto -> "Auto"
                        else -> "OFF"
                    }
                    updateButtons(refreshModeManager.currentMode)
                    val staticAodVisibility =
                        if(!sharedPreferences.getBoolean("disable_show_per_app_aod_settings", false))
                            View.VISIBLE
                        else
                            View.GONE
                    enableReaderModeText.setIsReader(staticAODOpacityManager.isReader)
                    staticAodText.visibility = staticAodVisibility
                    staticAodLinearLayout.visibility = staticAodVisibility
                    updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)
                }
            } ?: run {

                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                val inflater = LayoutInflater.from(this)
                val view = inflater.inflate(R.layout.floating_menu_layout, null, false)

                val layoutParams = WindowManager.LayoutParams().apply {
                    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    format = PixelFormat.TRANSLUCENT
                    flags =
                        flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.BOTTOM
                    y = getNavBarHeight()
                }

                menuBinding = FloatingMenuLayoutBinding.bind(view).apply {
                    root.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_OUTSIDE)
                            close()
                        false
                    }

                    button1.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.CLEAR)
                        updateButtons(refreshModeManager.currentMode)
                    }
                    button2.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.BALANCED)
                        updateButtons(refreshModeManager.currentMode)
                    }
                    button3.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.SMOOTH)
                        updateButtons(refreshModeManager.currentMode)
                    }
                    button4.setOnClickListener {
                        refreshModeManager.changeMode(RefreshMode.SPEED)
                        updateButtons(refreshModeManager.currentMode)
                    }

                    buttonTransparent.setOnClickListener{
                        staticAODOpacityManager.changeMode(AODOpacity.CLEAR)
                        updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)
                    }
                    buttonSemiTransparent.setOnClickListener{
                        staticAODOpacityManager.changeMode(AODOpacity.SEMICLEAR)
                        updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)
                    }
                    buttonSemiOpaque.setOnClickListener{
                        staticAODOpacityManager.changeMode(AODOpacity.SEMIOPAQUE)
                        updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)
                    }
                    buttonOpaque.setOnClickListener{
                        staticAODOpacityManager.changeMode(AODOpacity.OPAQUE)
                        updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)
                    }
                    val staticAodVisibility =
                        if(!sharedPreferences.getBoolean("disable_show_per_app_aod_settings", false))
                            View.VISIBLE
                        else
                            View.GONE
                    staticAodText.visibility = staticAodVisibility
                    staticAodLinearLayout.visibility = staticAodVisibility

                    settingsIcon.setOnClickListener {
                        val settingsIntent = Intent(
                            this@HibreakAccessibilityService,
                            SettingsActivity::class.java
                        )
                        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        close()
                        startActivity(settingsIntent)
                    }

                    enableReaderModeText.run{
                        setOnClickListener{
                            setIsReader(staticAODOpacityManager.toggleIsReader())
                            updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)
                        }
                    }
                    enableReaderModeText.setIsReader(staticAODOpacityManager.isReader)

                    lightSeekbar.min = 0
                    lightSeekbar.max = 254
                    lightSeekbar.progress = max(SystemSettingsManager.getBrightnessFromSetting(this@HibreakAccessibilityService) - 1, 0)
                    lightSeekbar.setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                progress: Int,
                                fromUser: Boolean
                            ) {
                                if(fromUser)
                                    SystemSettingsManager.setBrightnessSetting(this@HibreakAccessibilityService, progress + 1)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            }

                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            }

                        }
                    )

                    buttonNight.text = when(SystemSettingsManager.getNightLightMode(this@HibreakAccessibilityService)){
                        SystemSettingsManager.NightLightMode.Manual -> "Manual"
                        SystemSettingsManager.NightLightMode.Auto -> "Auto"
                        else -> "OFF"
                    }

                    buttonNight.setOnClickListener {
                        val nextMode = SystemSettingsManager.setNextNightLightMode(this@HibreakAccessibilityService)
                        buttonNight.text = when(nextMode){
                            SystemSettingsManager.NightLightMode.Manual -> "Manual"
                            SystemSettingsManager.NightLightMode.Auto -> "Auto"
                            else -> "OFF"
                        }
                    }

                    updateButtons(refreshModeManager.currentMode)
                    updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)

                    wm.addView(root, layoutParams)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun onDestroy() {
        commandRunner.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onServiceConnected() {
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event.letPackageNameClassName { pkgName, clsName ->
            val componentName = ComponentName(
                pkgName,
                clsName
            )
            try {
                packageManager.getActivityInfo(componentName, 0)
                refreshModeManager.onAppChange(pkgName)
                menuBinding.updateButtons(refreshModeManager.currentMode)
                staticAODOpacityManager.onAppChange(pkgName)
                menuBinding.updateButtons(staticAODOpacityManager.currentOpacity, staticAODOpacityManager.isReader)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
    }

    private fun updateColorScheme(sharedPreferences: SharedPreferences) = sharedPreferences.run {
        val type = getString("color_scheme_type", "5")
        val colorString = getInt("color_scheme_color", 20).progressToHex()
        commandRunner.runCommands(arrayOf("theme $type $colorString"))
    }

    private fun updateMaxBrightness(sharedPreferences: SharedPreferences) = sharedPreferences.run {
        try{
            val brightness = Integer.parseInt(getString("override_max_brightness", "2000")?:"2000")
            commandRunner.runCommands(arrayOf("smb$brightness"))
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "override_max_brightness" -> {
                sharedPreferences?.let { updateMaxBrightness(it) }
            }

            "static_lockscreen_opacity", "static_lockscreen_type", "static_lockscreen_bg_opacity", "static_lockscreen_mix_color" -> {
                staticAODOpacityManager.applyMode()
            }

            "color_scheme_type", "color_scheme_color" -> {
                sharedPreferences?.let { updateColorScheme(it) }
            }

            "close_status_bar" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }

            "run_clear_screen" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                handler.postDelayed({
                    commandRunner.runCommands(arrayOf(Commands.FORCE_CLEAR))
                }, 700)
            }
        }
    }
}

fun AccessibilityEvent?.letPackageNameClassName(block: (String, String) -> Unit) {
    this?.run {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            this.className?.let { clsName ->
                this.packageName?.let { pkgName -> block(pkgName.toString(), clsName.toString()) }
            }
    }
}

fun FloatingMenuLayoutBinding?.close() = this?.run {
    root.visibility = View.GONE
}

fun FloatingMenuLayoutBinding?.updateButtons(mode: RefreshMode) = this?.run {
    listOf(button1, button2, button3, button4).forEach(Button::deselect)
    when (mode) {
        RefreshMode.CLEAR -> button1
        RefreshMode.BALANCED -> button2
        RefreshMode.SMOOTH -> button3
        RefreshMode.SPEED -> button4
    }.select()
}

fun FloatingMenuLayoutBinding?.updateButtons(mode: AODOpacity, isReader: Boolean) = this?.run {
    listOf(buttonTransparent, buttonSemiTransparent, buttonSemiOpaque, buttonOpaque).forEach(Button::deselect)
    if(!isReader)
        when (mode) {
            AODOpacity.CLEAR-> buttonTransparent
            AODOpacity.SEMICLEAR -> buttonSemiTransparent
            AODOpacity.SEMIOPAQUE -> buttonSemiOpaque
            AODOpacity.OPAQUE -> buttonOpaque
            else -> null
        }?.select()
}

fun Button.deselect() {
    setBackgroundResource(R.drawable.drawable_border_normal)
    setTextColor(Color.BLACK)
}

fun Button.select() {
    setBackgroundResource(R.drawable.drawable_border_pressed)
    setTextColor(Color.WHITE)
}

fun TextView.setIsReader(isReader: Boolean) {
    text = if(isReader)
        "ON"
    else
        "OFF"
    val endDrawableId = if(isReader)
        R.drawable.baseline_book_24
    else
        R.drawable.baseline_book_closed_24
    setCompoundDrawablesWithIntrinsicBounds(0, 0, endDrawableId, 0)
}
