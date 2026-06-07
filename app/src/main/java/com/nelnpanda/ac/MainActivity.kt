package com.nelnpanda.ac

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.nelnpanda.ac.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isServiceEnabled()

        binding.tvServiceStatus.text = if (enabled)
            "✓  Accessibility Service: Enabled"
        else
            "✗  Accessibility Service: Disabled"

        binding.tvServiceStatus.setTextColor(
            getColor(if (enabled) R.color.status_ok else R.color.status_error)
        )

        binding.btnEnableService.isEnabled = !enabled

        binding.tvHint.text = if (enabled)
            "Tap the floating ▶ button to open the control panel, then set your interval and target."
        else
            "Enable the accessibility service and return here. A floating button will appear once enabled."
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.contains(packageName) }
    }
}
