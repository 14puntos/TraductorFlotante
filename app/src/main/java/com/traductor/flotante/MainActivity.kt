package com.traductor.flotante

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val show = Button(this).apply { text = "Mostrar icono flotante" }
        val hide = Button(this).apply { text = "Ocultar icono flotante" }

        show.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingService::class.java))
            } else {
                Toast.makeText(
                    this,
                    "Activa el permiso y vuelve a tocar el botón",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        hide.setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
        }

        layout.addView(show)
        layout.addView(hide)
        setContentView(layout)
    }
}
