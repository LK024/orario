package io.github.lk024.orario.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
