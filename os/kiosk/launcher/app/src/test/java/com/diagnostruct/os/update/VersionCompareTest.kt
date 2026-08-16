package com.diagnostruct.os.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * De esta comparacion depende que una tablet en obra se actualice sola o se
 * quede atras sin que nadie se entere. Un fallo aqui no da error: simplemente
 * la actualizacion nunca llega.
 */
class VersionCompareTest {

    @Test
    fun `detecta una version posterior`() {
        assertTrue(VersionCompare.isNewer("1.85.0", "1.84.0"))
        assertTrue(VersionCompare.isNewer("1.85.1", "1.85.0"))
        assertTrue(VersionCompare.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `no actualiza a la misma version ni hacia atras`() {
        assertFalse(VersionCompare.isNewer("1.85.0", "1.85.0"))
        assertFalse(VersionCompare.isNewer("1.84.0", "1.85.0"))
    }

    /**
     * El caso que rompe la comparacion de cadenas: "1.9.0" es mayor que
     * "1.10.0" alfabeticamente, pero anterior como version. Publicar la 1.10
     * despues de la 1.9 dejaria a todo el parque sin actualizar.
     */
    @Test
    fun `compara por numero y no por texto`() {
        assertTrue(VersionCompare.isNewer("1.10.0", "1.9.0"))
        assertFalse(VersionCompare.isNewer("1.9.0", "1.10.0"))
        assertTrue(VersionCompare.isNewer("1.100.0", "1.99.0"))
    }

    @Test
    fun `tolera versiones con distinto numero de segmentos`() {
        assertEquals(0, VersionCompare.compare("1.85", "1.85.0"))
        assertEquals(0, VersionCompare.compare("1.85.0.0", "1.85"))
        assertTrue(VersionCompare.isNewer("1.85.1", "1.85"))
    }

    @Test
    fun `ignora la v inicial y los espacios`() {
        assertEquals(0, VersionCompare.compare("v1.85.0", "1.85.0"))
        assertEquals(0, VersionCompare.compare("  1.85.0  ", "1.85.0"))
    }

    /**
     * Un manifiesto mal escrito no debe dejar la tablet sin poder actualizarse
     * nunca mas: los segmentos ilegibles valen cero en vez de lanzar.
     */
    @Test
    fun `un manifiesto mal formado no lanza`() {
        assertEquals(0, VersionCompare.compare("", ""))
        assertEquals(0, VersionCompare.compare("abc", "0.0.0"))
        assertTrue(VersionCompare.isNewer("1.85.0", "no-es-una-version"))
        assertEquals(0, VersionCompare.compare("1.85.0-beta", "1.85.0"))
    }

    @Test
    fun `el signo de compare es coherente`() {
        assertTrue(VersionCompare.compare("1.85.0", "1.84.0") > 0)
        assertTrue(VersionCompare.compare("1.84.0", "1.85.0") < 0)
        assertEquals(0, VersionCompare.compare("1.85.0", "1.85.0"))
    }
}
