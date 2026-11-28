package com.diagnostruct.os.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.diagnostruct.os.KioskConfig
import com.diagnostruct.os.policy.KioskPolicy

/**
 * Vuelve a poner el kiosco al abrir DIAGNOSTRUCT desde el escritorio.
 *
 * En mantenimiento el tecnico usa la tablet libremente; cuando vuelve a abrir
 * la aplicacion, ella llama aqui y el kiosco se reactiva solo, sin pasar por
 * el panel. La aplicacion se ancla a si misma despues (startLockTask), porque
 * solo ella puede anclar su propia tarea sin cerrarla.
 *
 * Es publica a proposito y no pide PIN: lo unico que puede hacer es VOLVER a
 * bloquear el equipo, nunca soltarlo. Si el kiosco ya esta activo no hace nada.
 */
class ReactivarActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val policy = KioskPolicy(this)
        if (policy.isDeviceOwner && !KioskConfig.kioskEnabled(this)) {
            Log.i(TAG, "DIAGNOSTRUCT abierta en mantenimiento: se reactiva el kiosco")
            KioskConfig.setKioskEnabled(this, true)
            KioskConfig.endMaintenanceWindow(this)
            policy.reactivate()
        }
        finish()
    }

    private companion object {
        const val TAG = "Reactivar"
    }
}
