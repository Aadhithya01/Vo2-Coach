package com.vo2coach.healthconnect

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.vo2coach.data.SessionRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Sends finished sessions to a (future) VO2 Coach phone companion over the Wearable Data Layer.
 * The phone app is the one that writes to Health Connect (Health Connect is phone-only).
 *
 * Safe to call anytime: every failure is caught and turned into a [SendResult]; it never throws
 * (except coroutine cancellation). If no phone / no companion is reachable it is a no-op.
 *
 * Routing: prefers nodes that advertise [PHONE_CAPABILITY] (declared by the companion app in
 * res/values/wear.xml). Without the companion, messages would be silently dropped, so we report
 * [SendResult.NoCompanionApp] instead of pretending it worked.
 *
 * Note: MessageClient is best-effort and requires the phone to be connected right now;
 * a `Sent` result means the transport accepted it, not that the phone stored it.
 */
class DataLayerSender(context: Context) {

    private val appContext = context.applicationContext

    sealed class SendResult {
        data class Sent(val nodeCount: Int, val sessionCount: Int) : SendResult()
        object NothingToSend : SendResult()
        object NoPhone : SendResult()
        object NoCompanionApp : SendResult()
        object TooLarge : SendResult()
        data class Failed(val reason: String) : SendResult()
    }

    /** Sends one session as JSON ([SessionJson.encodeRecord]) on [PATH_SESSION]. */
    suspend fun send(record: SessionRecord): SendResult = sendAll(listOf(record))

    /** Sends each record as its own message (keeps each payload far below the 100 KB limit). */
    suspend fun sendAll(records: List<SessionRecord>): SendResult {
        if (records.isEmpty()) return SendResult.NothingToSend
        val payloads = records.map { SessionJson.encodeRecord(it).toByteArray(Charsets.UTF_8) }
        if (payloads.any { it.size > MAX_MESSAGE_BYTES }) return SendResult.TooLarge

        return try {
            val (targets, miss) = resolveTargets()
            if (miss != null) return miss
            val messageClient = Wearable.getMessageClient(appContext)
            var sentNodes = 0
            var sentSessions = 0
            for (node in targets) {
                var okForNode = 0
                for (bytes in payloads) {
                    val ok = try {
                        messageClient.sendMessage(node.id, PATH_SESSION, bytes).await()
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }
                    if (ok) okForNode++
                }
                if (okForNode > 0) sentNodes++
                sentSessions = maxOf(sentSessions, okForNode)
            }
            if (sentNodes > 0) SendResult.Sent(sentNodes, sentSessions)
            else SendResult.Failed("phone did not accept the message")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ApiException when the Wearable API is unavailable (no Play services / never paired)
            SendResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** True if a phone running the companion is reachable right now. Never throws. */
    suspend fun isCompanionReachable(): Boolean = try {
        resolveTargets().second == null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    // ---------------------------------------------------------------- internals

    /** Nodes with the companion capability (nearby first), or an empty list plus the reason. */
    private suspend fun resolveTargets(): Pair<List<Node>, SendResult?> {
        val capable: Set<Node> = Wearable.getCapabilityClient(appContext)
            .getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
        if (capable.isNotEmpty()) return pickNodes(capable) to null

        val connected: List<Node> = Wearable.getNodeClient(appContext).connectedNodes.await()
        val miss = if (connected.isEmpty()) SendResult.NoPhone else SendResult.NoCompanionApp
        return emptyList<Node>() to miss
    }

    companion object {
        /** Message path; the phone's WearableListenerService filters on pathPrefix "/vo2coach". */
        const val PATH_SESSION = "/vo2coach/session"

        /** Capability string the phone companion must declare in res/values/wear.xml. */
        const val PHONE_CAPABILITY = "vo2coach_phone_receiver"

        /** MessageClient payload limit is 100 KB; keep margin. */
        const val MAX_MESSAGE_BYTES = 90_000

        /** Prefer directly-connected (nearby) nodes; fall back to all reachable ones. Pure. */
        fun pickNodes(nodes: Collection<Node>): List<Node> {
            val nearby = nodes.filter { it.isNearby }
            return nearby.ifEmpty { nodes.toList() }
        }
    }
}
