package com.diagnostruct.os.policy

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aplica la politica del kiosco fuera del hilo principal.
 *
 * Repasar todos los paquetes instalados para ocultarlos tarda lo suficiente
 * como para bloquear el arranque si se hace en `Application.onCreate` o dentro
 * de un receptor. No lleva ninguna restriccion de red a proposito: el blindaje
 * del equipo tiene que quedar puesto tambien en una obra sin cobertura.
 */
class PolicyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        runCatching { KioskPolicy(applicationContext).applyAll() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { error ->
                    Log.e(TAG, "No se pudo aplicar la politica", error)
                    Result.retry()
                },
            )
    }

    private companion object {
        const val TAG = "PolicyWorker"
    }
}

object PolicyScheduler {

    private const val WORK_NAME = "diagnostruct_politica"

    /**
     * Encola la aplicacion de la politica. Las llamadas seguidas se colapsan en
     * una sola ejecucion, que es lo deseable cuando el arranque dispara varias
     * a la vez.
     */
    fun applyNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PolicyWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
