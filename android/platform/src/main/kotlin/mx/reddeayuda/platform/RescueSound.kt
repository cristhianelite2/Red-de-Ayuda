package mx.reddeayuda.platform

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import mx.reddeayuda.protocol.RescueAction
import kotlin.concurrent.thread

class RescueSound(private val context: Context) {
    fun handle(action: RescueAction) {
        vibrate()
        if (action == RescueAction.SOUND || action == RescueAction.CONTACT || action == RescueAction.SCREEN) {
            playRescuePattern()
        }
    }

    fun playRescuePattern() {
        thread(name = "rda-rescue-audio") {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            try {
                repeat(3) {
                    repeat(3) {
                        tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                        Thread.sleep(300)
                    }
                    Thread.sleep(700)
                }
            } catch (_: InterruptedException) {
            } finally {
                tg.release()
            }
        }
    }

    fun playSafetyCheck() {
        thread {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            try {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 600)
                Thread.sleep(700)
            } finally {
                tg.release()
            }
        }
    }

    @Suppress("DEPRECATION")
    fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 200), -1))
        } else {
            vibrator.vibrate(longArrayOf(0, 200, 100, 200, 100, 200), -1)
        }
    }
}
