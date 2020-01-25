package com.geeksville.mesh

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.geeksville.android.DebugLogFile
import com.geeksville.android.Logging
import com.google.protobuf.util.JsonFormat

/**
 * Handles the bluetooth link with a mesh radio device.  Does not cache any device state,
 * just does bluetooth comms etc...
 *
 * This service is not exposed outside of this process.
 *
 * Note - this class intentionally dumb.  It doesn't understand protobuf framing etc...
 * It is designed to be simple so it can be stubbed out with a simulated version as needed.
 */
class RadioInterfaceService : JobIntentService(), Logging {

    companion object {
        /**
         * Unique job ID for this service.  Must be the same for all work.
         */
        private const val JOB_ID = 1001

        /**
         * The SEND_TORADIO
         * Payload will be the raw bytes which were contained within a MeshProtos.ToRadio protobuf
         */
        const val SEND_TORADIO_ACTION = "$prefix.SEND_TORADIO"

        /**
         * The RECEIVED_FROMRADIO
         * Payload will be the raw bytes which were contained within a MeshProtos.FromRadio protobuf
         */
        const val RECEIVE_FROMRADIO_ACTION = "$prefix.RECEIVE_FROMRADIO"

        /**
         * Convenience method for enqueuing work in to this service.
         */
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(
                context,
                RadioInterfaceService::class.java, JOB_ID, work
            )
        }

        /// Helper function to send a packet to the radio
        fun sendToRadio(context: Context, a: ByteArray) {
            val i = Intent(SEND_TORADIO_ACTION)
            i.putExtra(EXTRA_PAYLOAD, a)
            enqueueWork(context, i)
        }

        // for debug logging only
        private val jsonPrinter = JsonFormat.printer()
        private val jsonParser = JsonFormat.parser()

        /**
         * When simulating we parse these MeshPackets as if they arrived at startup
         * Send broadcast them after we receive a ToRadio.WantNodes message.
         *
         * Our fake net has three nodes
         *
         * +16508675309, nodenum 9 - our node
         * +16508675310, nodenum 10 - some other node, name Bob One/BO
         * (eventually) +16508675311, nodenum 11 - some other node
         */
        /*

2020-01-25 11:02:05.279 1162-1280/com.geeksville.mesh D/com.geeksville.mesh.RadioInterfaceService: Executing work: Intent { act=com.geeksville.mesh.SEND_TORADIO (has extras) }
2020-01-25 11:02:05.282 1162-1273/com.geeksville.mesh D/EGL_emulation: eglMakeCurrent: 0xebb2b500: ver 2 0 (tinfo 0xc9748bc0)
2020-01-25 11:02:05.449 1162-1280/com.geeksville.mesh I/com.geeksville.mesh.RadioInterfaceService: TODO sending to radio: {   "wantNodes": {   } }
2020-01-25 11:02:05.452 1162-1280/com.geeksville.mesh D/com.geeksville.mesh.RadioInterfaceService: Executing work: Intent { act=com.geeksville.mesh.SEND_TORADIO (has extras) }
2020-01-25 11:02:05.479 1162-1280/com.geeksville.mesh I/com.geeksville.mesh.RadioInterfaceService: TODO sending to radio: {   "setOwner": {     "id": "+16508675309",     "longName": "Kevin Xter",     "shortName": "kx"   } }
2020-01-25 11:02:05.480 1162-1280/com.geeksville.mesh D/com.geeksville.mesh.RadioInterfaceService: Executing work: Intent { act=com.geeksville.mesh.SEND_TORADIO (has extras) }
2020-01-25 11:02:05.504 1162-1280/com.geeksville.mesh I/com.geeksville.mesh.RadioInterfaceService: TODO sending to radio: {   "packet": {     "from": -1,     "to": 10,     "payload": {       "subPackets": [{         "data": {           "payload": "aGVsbG8gd29ybGQ="         }       }]     }   } }
2020-01-25 11:02:05.505 1162-1280/com.geeksville.mesh D/com.geeksville.mesh.RadioInterfaceService: Executing work: Intent { act=com.geeksville.mesh.SEND_TORADIO (has extras) }
2020-01-25 11:02:05.510 1162-1280/com.geeksville.mesh I/com.geeksville.mesh.RadioInterfaceService: TODO sending to radio: {   "packet": {     "from": -1,     "to": 10,     "payload": {       "subPackets": [{         "data": {           "payload": "aGVsbG8gd29ybGQ="         }       }]     }   } }
2020-01-25 11:02:08.232 1162-1273/com.geeksville.mesh D/EGL_emulation: eglMakeCurrent: 0xebb2b500: ver 2 0 (tinfo 0xc9748bc0)

         */
        val simInitPackets =
            arrayOf(
                """ { "from": 10, "to": 9, "payload": {  "subPackets": [{ "user": { "id": "+16508675310", "longName": "Bob One", "shortName": "BO" }}]}}  """,
                """ { "from": 10, "to": 9, "payload": {  "subPackets": [{ "data": { "payload": "aGVsbG8gd29ybGQ=", "typ": 0 }}]}}  """, // SIGNAL_OPAQUE
                """ { "from": 10, "to": 9, "payload": {  "subPackets": [{ "data": { "payload": "aGVsbG8gd29ybGQ=", "typ": 1 }}]}}  """, // CLEAR_TEXT
                """ { "from": 10, "to": 9, "payload": {  "subPackets": [{ "data": { "payload": "", "typ": 2 }}]}}  """ // CLEAR_READACK
            )

        // FIXME, move into a subclass?
        val isSimulating = true
    }

    lateinit var sentPacketsLog: DebugLogFile // inited in onCreate

    private fun broadcastReceivedFromRadio(payload: ByteArray) {
        val intent = Intent(RECEIVE_FROMRADIO_ACTION)
        intent.putExtra("$prefix.Payload", payload)
        sendBroadcast(intent)
    }

    fun broadcastConnectionChanged(isConnected: Boolean) {
        val intent = Intent("$prefix.CONNECTION_CHANGED")
        intent.putExtra(EXTRA_CONNECTED, isConnected)
        sendBroadcast(intent)
    }

    /// Send a packet/command out the radio link
    private fun handleSendToRadio(p: ByteArray) {

        // For debugging/logging purposes ONLY we convert back into a protobuf for readability
        val proto = MeshProtos.ToRadio.parseFrom(p)

        val json = jsonPrinter.print(proto).replace('\n', ' ')
        info("TODO sending to radio: $json")
        sentPacketsLog.log(json)

        if (isSimulating)
            simInitPackets.forEach { json ->
                val fromRadio = MeshProtos.FromRadio.newBuilder().apply {
                    packet = MeshProtos.MeshPacket.newBuilder().apply {
                        jsonParser.merge(json, this)
                    }.build()
                }.build()

                broadcastReceivedFromRadio(fromRadio.toByteArray())
            }
    }

    // Handle an incoming packet from the radio, broadcasts it as an android intent
    private fun handleFromRadio(p: ByteArray) {
        broadcastReceivedFromRadio(p)
    }

    override fun onCreate() {
        super.onCreate()

        sentPacketsLog = DebugLogFile(this, "sent_log.json")
    }

    override fun onDestroy() {
        sentPacketsLog.close()
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) { // We have received work to do.  The system or framework is already
        // holding a wake lock for us at this point, so we can just go.
        debug("Executing work: $intent")
        when (intent.action) {
            SEND_TORADIO_ACTION -> handleSendToRadio(intent.getByteArrayExtra(EXTRA_PAYLOAD)!!)
            else -> TODO("Unhandled case")
        }
    }

}