package mx.reddeayuda.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WearPhoneClient {
    private val io = Executors.newSingleThreadExecutor()

    fun send(context: Context, path: String, data: ByteArray, onResult: (Boolean) -> Unit) {
        io.execute {
            val ok = try {
                val nodes = Tasks.await(
                    Wearable.getNodeClient(context).connectedNodes,
                    8,
                    TimeUnit.SECONDS
                )
                if (nodes.isNullOrEmpty()) {
                    false
                } else {
                    var any = false
                    nodes.forEach { node ->
                        Tasks.await(
                            Wearable.getMessageClient(context)
                                .sendMessage(node.id, path, data),
                            8,
                            TimeUnit.SECONDS
                        )
                        any = true
                    }
                    any
                }
            } catch (_: Exception) {
                false
            }
            onResult(ok)
        }
    }
}
