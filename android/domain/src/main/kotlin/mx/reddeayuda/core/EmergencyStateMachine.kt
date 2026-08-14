package mx.reddeayuda.core

import mx.reddeayuda.protocol.DeviceRole
import mx.reddeayuda.protocol.DeviceState
import mx.reddeayuda.protocol.StateEvent

class EmergencyStateMachine(
    role: DeviceRole = DeviceRole.CIVILIAN,
    state: DeviceState = DeviceState.NORMAL
) {
    var role: DeviceRole = role
        private set
    var state: DeviceState = state
        private set

    fun snapshot(): StateSnapshot = StateSnapshot(role, state)

    fun onEvent(event: StateEvent): Boolean {
        when (event) {
            StateEvent.ENTER_RESCUE_ROLE -> {
                role = DeviceRole.RESCUER
                return true
            }
            StateEvent.LEAVE_RESCUE_ROLE -> {
                role = DeviceRole.CIVILIAN
                return true
            }
            else -> Unit
        }
        val next = nextState(state, event) ?: return false
        state = next
        if (state == DeviceState.RESOLVED) {
            state = DeviceState.NORMAL
        }
        return true
    }

    companion object {
        fun nextState(current: DeviceState, event: StateEvent): DeviceState? = when (current) {
            DeviceState.NORMAL -> when (event) {
                StateEvent.DISASTER_ALERT -> DeviceState.DISASTER
                StateEvent.START_SAFETY_CHECK -> DeviceState.SAFETY_CHECK
                StateEvent.USER_NEED_HELP -> DeviceState.SOS
                else -> null
            }
            DeviceState.DISASTER -> when (event) {
                StateEvent.START_SAFETY_CHECK, StateEvent.SAFETY_TIMEOUT -> DeviceState.SAFETY_CHECK
                StateEvent.USER_NEED_HELP, StateEvent.SEVERE_NO_RESPONSE -> DeviceState.SOS
                StateEvent.DISASTER_CLEAR -> DeviceState.NORMAL
                else -> null
            }
            DeviceState.SAFETY_CHECK -> when (event) {
                StateEvent.USER_IM_OK -> DeviceState.NORMAL
                StateEvent.USER_NEED_HELP, StateEvent.SEVERE_NO_RESPONSE -> DeviceState.SOS
                else -> null
            }
            DeviceState.SOS -> when (event) {
                StateEvent.PACKET_RESCUE_PING -> DeviceState.RESCUE_CONTACT
                StateEvent.USER_RESOLVE, StateEvent.USER_IM_OK -> DeviceState.RESOLVED
                else -> null
            }
            DeviceState.RESCUE_CONTACT -> when (event) {
                StateEvent.USER_NEED_HELP -> DeviceState.SOS
                StateEvent.USER_RESOLVE, StateEvent.USER_IM_OK -> DeviceState.RESOLVED
                else -> null
            }
            DeviceState.RESOLVED -> when (event) {
                else -> DeviceState.NORMAL
            }
        }
    }
}
