# El sistema instancia estas clases por nombre desde el manifiesto; R8 no puede
# ver esas referencias y las eliminaria.
-keep class com.diagnostruct.os.policy.DiagnostructDeviceAdminReceiver { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
