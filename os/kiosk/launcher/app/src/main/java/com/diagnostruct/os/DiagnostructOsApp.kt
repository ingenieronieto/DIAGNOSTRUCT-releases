package com.diagnostruct.os

import android.app.Application
import android.util.Log
import com.diagnostruct.os.policy.KioskPolicy
import com.diagnostruct.os.policy.PolicyScheduler
import com.diagnostruct.os.update.UpdateScheduler

class DiagnostructOsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!KioskPolicy(this).isDeviceOwner) {
            Log.w(TAG, "Sin Device Owner: el kiosco funciona en modo limitado")
            return
        }
        // Ambos trabajos se ejecutan fuera del hilo principal; aqui solo se
        // encolan, para no retrasar el arranque del proceso.
        runCatching { PolicyScheduler.applyNow(this) }
            .onFailure { Log.e(TAG, "No se pudo encolar la politica", it) }
        runCatching { UpdateScheduler.schedule(this) }
            .onFailure { Log.e(TAG, "No se pudo programar la actualizacion", it) }
    }

    private companion object {
        const val TAG = "DiagnostructOS"
    }
}
