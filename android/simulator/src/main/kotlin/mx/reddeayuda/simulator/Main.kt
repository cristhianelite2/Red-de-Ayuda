package mx.reddeayuda.simulator

import mx.reddeayuda.core.FixedClock
import mx.reddeayuda.core.RadioWorld
import mx.reddeayuda.core.SimNode
import mx.reddeayuda.core.deviceId
import mx.reddeayuda.protocol.AckKind
import mx.reddeayuda.protocol.DeviceRole
import mx.reddeayuda.protocol.RescueAction

/**
 * Demostración A → B → C → D sin BLE.
 * A y D están fuera de rango; el SOS debe saltar por B y C.
 */
fun main() {
    val result = runMeshScenario()
    println(result.report)
    if (!result.ok) {
        error("Simulador: escenario A→B→C→D FALLÓ")
    }
}

data class MeshScenarioResult(val ok: Boolean, val report: String)

fun runMeshScenario(): MeshScenarioResult {
    val clock = FixedClock(1_700_000_000L)
    val world = RadioWorld(range = 12.0)
    val a = SimNode("A", deviceId(1), x = 0.0, y = 0.0, clock = clock)
    val b = SimNode("B", deviceId(2), x = 10.0, y = 0.0, clock = clock)
    val c = SimNode("C", deviceId(3), x = 20.0, y = 0.0, clock = clock)
    val d = SimNode("D", deviceId(4), x = 30.0, y = 0.0, clock = clock, role = DeviceRole.RESCUER)
    world.add(a); world.add(b); world.add(c); world.add(d)
    d.asRescue()

    a.sos()
    val sosId = a.engine.packetsToSend().first { it.type.name == "SOS" }.messageIdHex()

    // A alcanza B, no C ni D (rango 12; A-C=20).
    world.tick()
    world.tick()
    world.tick()

    val lines = mutableListOf<String>()
    fun check(name: String, cond: Boolean) {
        lines += if (cond) "OK  $name" else "FAIL $name"
    }

    val dSos = d.rescueVisible.filter { it.messageIdHex() == sosId }
    check("D recibió el SOS", dSos.isNotEmpty())
    check("B no muestra SOS al civil (rescueVisible vacío)", b.rescueVisible.isEmpty())
    check("C no muestra SOS al civil", c.rescueVisible.isEmpty())
    check("A no se auto-lista como rescue", a.rescueVisible.isEmpty())
    val hop = dSos.firstOrNull()?.hopCount ?: -1
    check("hopCount en D es 3 (A→B→C→D)", hop == 3)
    check("messageId único en D", dSos.size == 1)

    world.tick()
    val before = d.rescueVisible.count { it.messageIdHex() == sosId }
    world.tick()
    check("dedup: D no duplica el SOS", d.rescueVisible.count { it.messageIdHex() == sosId } == before)

    val target = a.engine.localDeviceId
    d.pingSound(target)
    world.tick()
    world.tick()
    world.tick()
    check("A recibió RESCUE_PING SOUND", a.pings.contains(RescueAction.SOUND))
    check("B no ejecuta ping ajeno", b.pings.isEmpty())
    check("A recibió ACK (DELIVERED o CONTACT)", a.acks.any { it == AckKind.MESSAGE_DELIVERED || it == AckKind.RESCUE_CONTACT })

    val ok = lines.none { it.startsWith("FAIL") }
    val report = buildString {
        appendLine("=== Simulador Red de Ayuda A→B→C→D ===")
        appendLine("SOS id=$sosId hopAtD=$hop")
        lines.forEach { appendLine(it) }
        appendLine(if (ok) "RESULTADO: PASS" else "RESULTADO: FAIL")
    }
    return MeshScenarioResult(ok, report)
}
