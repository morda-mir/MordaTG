package online.morda.mordatg.service

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun `package replacement restores a previously running proxy`() {
        assertTrue(BootReceiver.shouldRestoreProxy(Intent.ACTION_MY_PACKAGE_REPLACED, false, true))
        assertFalse(BootReceiver.shouldRestoreProxy(Intent.ACTION_MY_PACKAGE_REPLACED, true, false))
    }

    @Test
    fun `device boot also requires autostart`() {
        assertTrue(BootReceiver.shouldRestoreProxy(Intent.ACTION_BOOT_COMPLETED, true, true))
        assertFalse(BootReceiver.shouldRestoreProxy(Intent.ACTION_BOOT_COMPLETED, false, true))
        assertFalse(BootReceiver.shouldRestoreProxy(Intent.ACTION_BOOT_COMPLETED, true, false))
    }
}
