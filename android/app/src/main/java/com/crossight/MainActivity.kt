package com.crossight

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.crossight.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity(){

    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedLanguagePreferences: SharedPreferences

    private var currentLanguage: String = ""

    @SuppressLint("ClickableViewAccessibility", "UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLanguagePreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        currentLanguage = sharedLanguagePreferences.getString("currentLanguage", Locale.getDefault().language) ?: "en"
        setLocale(this@MainActivity,currentLanguage)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)

        sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)

        val settingsButton: ImageButton = activityMainBinding.appbar.settingsButton
        val settingsDrawer: NestedScrollView = activityMainBinding.appbarLayout.findViewById(R.id.settings_drawer)
        settingsButton.setOnClickListener {
            if (settingsDrawer.visibility == View.GONE) {
                settingsDrawer.visibility = View.VISIBLE
            } else {
                settingsDrawer.visibility = View.GONE
            }
        }

        val soundCueSwitch: Switch = settingsDrawer.findViewById(R.id.sound_cue_switch)
        val voiceCueSwitch: Switch = settingsDrawer.findViewById(R.id.voice_cue_switch)
        val visualCueSwitch: Switch = settingsDrawer.findViewById(R.id.visual_cue_switch)
        val vibrationCueSwitch: Switch = settingsDrawer.findViewById(R.id.vibration_switch)

        visualCueSwitch.isChecked = sharedPreferences.getBoolean("visualCue", false)
        soundCueSwitch.isChecked = sharedPreferences.getBoolean("soundCue", false)
        voiceCueSwitch.isChecked = sharedPreferences.getBoolean("voiceCue", false)
        vibrationCueSwitch.isChecked = sharedPreferences.getBoolean("vibrationCue", false)


        soundCueSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("soundCue", isChecked).apply()
        }

        voiceCueSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("voiceCue", isChecked).apply()
        }

        visualCueSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("visualCue", isChecked).apply()
        }

        vibrationCueSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("vibrationCue", isChecked).apply()
        }

        setContentView(activityMainBinding.root)

         // Default to English
        setupLanguageSpinner()

    } // fun onCreate()

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.languageSpinner)
        val languages = arrayOf("en", "zh-rTW") // Assuming these are the entries
        val languageArray = resources.getStringArray(R.array.language_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val selectedIndex = languages.indexOfFirst { it.startsWith(currentLanguage) }
        spinner.setSelection(selectedIndex, false) // Set false to prevent unnecessary event trigger

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedLanguage = when (position) {
                    1 -> "zh-rTW" // Chinese
                    else -> "en" // Default to English
                }
                if (selectedLanguage != currentLanguage) {
                    sharedLanguagePreferences.edit().putString("currentLanguage", selectedLanguage).apply()
                    setLocale(this@MainActivity,selectedLanguage)
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setLocale(context: Context, languageCode: String) {
        val locale = if(languageCode == "zh-rTW") Locale("zh", "TW") else Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        }
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        // Optional: Store the current language in SharedPreferences or a static variable
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }
}