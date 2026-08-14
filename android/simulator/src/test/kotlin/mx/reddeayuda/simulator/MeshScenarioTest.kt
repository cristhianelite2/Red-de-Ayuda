package mx.reddeayuda.simulator

import org.junit.Assert.assertTrue
import org.junit.Test

class MeshScenarioTest {
    @Test
    fun aToDThroughRepeaters() {
        val result = runMeshScenario()
        assertTrue(result.report, result.ok)
    }
}
