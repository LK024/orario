package io.github.lk024.orario.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Riga della scheda "Supplenze" come la restituisce lo script ([hour] parte da 1). */
@Serializable
data class RemoteSubstitution(
    val date: String,
    val hour: Int,
    val title: String? = null,
    val room: String? = null,
    val teacher: String? = null,
) {
    fun toOverride() = Override(date, hour - 1, title?.let { Lesson(it, room, teacher) })
}

/** Modifica fatta sul telefono e non ancora arrivata nel foglio. */
@Serializable
data class PendingChange(
    val date: String,
    val hour: Int,
    val lesson: Lesson? = null,
    val delete: Boolean = false,
) {
    val key get() = Override(date, hour, lesson).key
}

@Serializable
private data class Change(
    val date: String,
    val hour: Int,
    val title: String?,
    val room: String?,
    val teacher: String?,
    val delete: Boolean,
)

@Serializable
private data class Request(val token: String, val changes: List<Change>)

@Serializable
private data class Response(val substitutions: List<RemoteSubstitution>? = null, val error: String? = null)

/** Pausa nel foglio, come la restituisce lo script (scheda "Pausa"). */
sealed interface RemotePause {
    /** Script non ancora aggiornato: la risposta non parla di pausa, quindi resta tutto com'è. */
    data object Unsupported : RemotePause
    data object None : RemotePause
    data class Period(val from: LocalDate, val to: LocalDate) : RemotePause
}

/** Legge la chiave "pause" della risposta dello script: assente, null oppure `{from, to}` in formato ISO. */
fun parseRemotePause(text: String): RemotePause {
    val response = Json.parseToJsonElement(text).jsonObject
    response["error"]?.jsonPrimitive?.contentOrNull?.let { error("Script: $it") }
    val pause = response["pause"] ?: return RemotePause.Unsupported
    if (pause !is JsonObject) return RemotePause.None
    fun date(key: String) = pause[key]?.jsonPrimitive?.contentOrNull?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val from = date("from")
    val to = date("to")
    return if (from != null && to != null) RemotePause.Period(from, to) else RemotePause.None
}

/**
 * Client dello script Apps Script del foglio (vedi apps-script/README.md).
 * Senza URL o chiave in local.properties la sincronizzazione è spenta e le supplenze restano sul telefono.
 */
class SubstitutionsApi(private val url: String, private val token: String) {

    private val json = Json { ignoreUnknownKeys = true }

    val enabled get() = url.isNotBlank() && token.isNotBlank()

    fun fetch(): List<Override> =
        read(open("$url?token=${URLEncoder.encode(token, "UTF-8")}"))

    /** Invia le modifiche e restituisce l'elenco aggiornato. */
    fun push(changes: List<PendingChange>): List<Override> {
        val body = json.encodeToString(Request(token, changes.map {
            Change(it.date, it.hour + 1, it.lesson?.title, it.lesson?.room, it.lesson?.teacher, it.delete)
        }))
        val connection = open(url).apply {
            requestMethod = "POST"
            doOutput = true
            // text/plain: niente preflight se un giorno lo usa anche la PWA dal browser
            setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return read(connection)
    }

    /** Legge la pausa dal foglio. */
    fun fetchPause(): RemotePause =
        readPause(open("$url?token=${URLEncoder.encode(token, "UTF-8")}"))

    /** Scrive la pausa nel foglio (null la toglie) e restituisce quella salvata. */
    fun pushPause(from: LocalDate?, to: LocalDate?): RemotePause {
        val body = buildJsonObject {
            put("token", token)
            if (from != null && to != null) {
                putJsonObject("pause") {
                    put("from", from.toString())
                    put("to", to.toString())
                }
            } else {
                put("pause", JsonNull)
            }
        }.toString()
        val connection = open(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return readPause(connection)
    }

    private fun readPause(connection: HttpURLConnection): RemotePause {
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            return parseRemotePause(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun open(address: String) = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        instanceFollowRedirects = true
    }

    private fun read(connection: HttpURLConnection): List<Override> {
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val response = json.decodeFromString<Response>(connection.inputStream.bufferedReader().use { it.readText() })
            check(response.error == null) { "Script: ${response.error}" }
            return response.substitutions.orEmpty().map { it.toOverride() }
        } finally {
            connection.disconnect()
        }
    }
}

/** Le modifiche non ancora inviate vincono su quello che c'è nel foglio. */
fun mergeWithPending(remote: List<Override>, pending: List<PendingChange>): Map<OverrideKey, Override> {
    val merged = remote.associateBy { it.key }.toMutableMap()
    pending.forEach { change ->
        if (change.delete) merged.remove(change.key)
        else merged[change.key] = Override(change.date, change.hour, change.lesson)
    }
    return merged
}
