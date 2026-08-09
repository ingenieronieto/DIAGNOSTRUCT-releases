package com.diagnostruct.os.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diagnostruct.os.KioskConfig
import java.util.concurrent.TimeUnit

/**
 * Comprueba el manifiesto de versiones y, si hay una posterior a la instalada,
 * la descarga e instala en silencio.
 *
 * El tecnico no tiene que decidir nada ni ver dialogos: en cuanto la tablet ve
 * red, se pone al dia sola. De paso reaplica la politica del kiosco, que es la
 * forma barata de corregir cualquier desvio acumulado.
 */
class UpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (!KioskConfig.autoUpdate(context)) {
            Log.i(TAG, "Actualizacion automatica desactivada")
            return Result.success()
        }

        val manifest = runCatching {
            VersionManifest.parse(SilentInstaller.fetchText(KioskConfig.VERSION_MANIFEST_URL))
        }.getOrElse { error ->
            // Sin cobertura es lo normal en obra: se reintenta mas tarde.
            Log.w(TAG, "No se pudo leer el manifiesto de versiones", error)
            return Result.retry()
        }

        val installed = installedVersion(context)
        if (installed == null) {
            Log.w(TAG, "${KioskConfig.APP_PACKAGE} no esta instalada; se instala ${manifest.version}")
        } else if (!VersionCompare.isNewer(manifest.version, installed)) {
            Log.i(TAG, "Version $installed al dia (publicada ${manifest.version})")
            return Result.success()
        } else {
            // Por debajo de la version minima el servidor puede rechazar las
            // subidas: conviene que quede en el registro del equipo.
            val obsoleta = manifest.minimumVersion.isNotBlank() &&
                VersionCompare.isNewer(manifest.minimumVersion, installed)
            if (obsoleta) {
                Log.w(TAG, "Version $installed por debajo de la minima ${manifest.minimumVersion}")
            }
            Log.i(TAG, "Actualizando de $installed a ${manifest.version}")
        }

        return when (val outcome =
            SilentInstaller.downloadAndInstall(context, manifest.apkUrl, manifest.version)) {
            is SilentInstaller.Result.Started -> Result.success()
            is SilentInstaller.Result.Failed -> {
                Log.e(TAG, "Instalacion fallida: ${outcome.reason}")
                Result.retry()
            }
        }
    }

    private fun installedVersion(context: Context): String? = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(KioskConfig.APP_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(KioskConfig.APP_PACKAGE, 0)
        }
        info.versionName
    }.getOrNull()

    private companion object {
        const val TAG = "UpdateWorker"
    }
}

/** Programacion del trabajo de actualizacion. */
object UpdateScheduler {

    private const val PERIODIC_WORK = "diagnostruct_actualizacion_periodica"
    private const val IMMEDIATE_WORK = "diagnostruct_actualizacion_inmediata"
    private const val INTERVAL_HOURS = 6L
    private const val BACKOFF_MINUTES = 15L

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Deja programada la comprobacion periodica y lanza una de inmediato. */
    fun schedule(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<UpdateWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
        checkNow(context)
    }

    /** Comprobacion inmediata, usada desde el panel tecnico y tras arrancar. */
    fun checkNow(context: Context) {
        val immediate = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            immediate,
        )
    }
}
