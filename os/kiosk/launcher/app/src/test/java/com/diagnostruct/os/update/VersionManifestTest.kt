package com.diagnostruct.os.update

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionManifestTest {

    /** El manifiesto real publicado en la raiz del repositorio. */
    private val manifiestoPublicado = """
        {
          "version": "1.85.0",
          "fecha": "2026-08-03",
          "minima": "1.70.0",
          "notas": [
            "Los cambios del proyecto ya no se pierden sin señal.",
            "Los firmantes están disponibles sin internet para los informes."
          ],
          "apkUrl": "https://raw.githubusercontent.com/ingenieronieto/DIAGNOSTRUCT-releases/main/DIAGNOSTRUCT-SDK.apk",
          "apkNombre": "DIAGNOSTRUCT-SDK V.1.85.0.apk"
        }
    """.trimIndent()

    @Test
    fun `lee el manifiesto publicado`() {
        val manifiesto = VersionManifest.parse(manifiestoPublicado)

        assertEquals("1.85.0", manifiesto.version)
        assertEquals("1.70.0", manifiesto.minimumVersion)
        assertTrue(manifiesto.apkUrl.endsWith("DIAGNOSTRUCT-SDK.apk"))
        assertEquals(2, manifiesto.notes.size)
    }

    /**
     * `fecha` y `apkNombre` no se usan, y el manifiesto puede ganar campos
     * nuevos con el tiempo. Que aparezcan no debe romper a las tablets que ya
     * estan en obra con una version anterior del launcher.
     */
    @Test
    fun `ignora los campos que no usa`() {
        val conCamposNuevos = """
            {
              "version": "1.86.0",
              "apkUrl": "https://ejemplo/app.apk",
              "campoQueAunNoExiste": { "anidado": true },
              "otro": [1, 2, 3]
            }
        """.trimIndent()

        val manifiesto = VersionManifest.parse(conCamposNuevos)

        assertEquals("1.86.0", manifiesto.version)
        assertEquals("https://ejemplo/app.apk", manifiesto.apkUrl)
    }

    @Test
    fun `sin notas devuelve una lista vacia, no nulo`() {
        val sinNotas = """{ "version": "1.86.0", "apkUrl": "https://ejemplo/app.apk" }"""

        val manifiesto = VersionManifest.parse(sinNotas)

        assertTrue(manifiesto.notes.isEmpty())
        assertEquals("", manifiesto.minimumVersion)
    }

    /**
     * Si falta `apkUrl` no hay nada que descargar. Tiene que fallar de forma
     * visible: el worker lo captura y reintenta mas tarde, en vez de intentar
     * instalar desde una cadena vacia.
     */
    @Test(expected = JSONException::class)
    fun `falla si falta la url del apk`() {
        VersionManifest.parse("""{ "version": "1.86.0" }""")
    }

    @Test(expected = JSONException::class)
    fun `falla si falta la version`() {
        VersionManifest.parse("""{ "apkUrl": "https://ejemplo/app.apk" }""")
    }
}
