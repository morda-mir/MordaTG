package online.morda.mordatg.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {
    @Test
    fun `known digest matches`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digest("abc".toByteArray()),
        )
    }
}
