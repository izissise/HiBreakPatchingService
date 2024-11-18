package net.izissise.hibreak_comp_service

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference


class BacklightSliderPreference(context: Context, attrs: AttributeSet) : SeekBarPreference(context, attrs) {
    companion object {
        const val ACTION_BACKLIGHT_CHANGED = "BACKLIGHT_CHANGED"
        const val EXTRA_BACKLIGHT_VALUE = "backlight_value"
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar
        val valueTextView = holder.findViewById(androidx.preference.R.id.seekbar_value) as? TextView
        seekBar?.run {
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueTextView.setValue(progress)
                    val intent = Intent(ACTION_BACKLIGHT_CHANGED).apply {
                        putExtra(EXTRA_BACKLIGHT_VALUE, progress)
                    }
                    context.sendBroadcast(intent)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    this@BacklightSliderPreference.value = progress
                }
            })
            valueTextView.setValue(progress)
        }
        valueTextView?.visibility = View.VISIBLE
    }
}

private fun TextView?.setValue(v: Int){
    val vStr = "${v}"
    this?.text = vStr
}

