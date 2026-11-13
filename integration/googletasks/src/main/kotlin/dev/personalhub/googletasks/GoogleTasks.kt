package dev.personalhub.googletasks

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dev.personalhub.focus.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Google Tasks adapter (owner-requested, 13 Nov 2026). Uses the official Tasks REST API with the on-device
 * Google authorization flow; access tokens live only in memory for one sync and are never persisted or logged.
 * Only task title, due date, completion and deletion are exchanged.
 */
const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

sealed interface Auth {
    data class Token(val value: String) : Auth
    data class NeedsConsent(val intent: PendingIntent) : Auth
}

object GoogleAuth {
    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(TASKS_SCOPE))).build()

    /** Silent when already granted; otherwise returns the consent PendingIntent for an Activity to launch. */
    suspend fun authorize(context: Context): Auth = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) cont.resume(Auth.NeedsConsent(pi))
                else r.accessToken?.let { cont.resume(Auth.Token(it)) } ?: cont.resumeWithException(IllegalStateException("No access token returned."))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun tokenFromConsent(context: Context, data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
}

class ApiException(val code: Int, message: String) : Exception(message)

/** Remote operations used by sync (a fake implements this in tests). */
interface TasksApi {
    suspend fun lists(): List<String>
    suspend fun tasks(list: String, updatedMin: Instant?): List<RemoteTask>
    suspend fun insert(list: String, t: LocalSyncTask): RemoteTask
    suspend fun patch(list: String, id: String, t: LocalSyncTask): RemoteTask
    suspend fun delete(list: String, id: String)
}

/** Minimal REST client. PATCH is sent as POST + X-HTTP-Method-Override (supported by Google APIs). */
class GoogleTasksClient(private val token: String, private val base: String = "https://tasks.googleapis.com/tasks/v1") : TasksApi {
    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject? = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            if (body != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            if (code == 204 || method == "DELETE" && code in 200..299) return@withContext null
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(code, "Google Tasks HTTP $code")
            JSONObject(text)
        } finally { c.disconnect() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    override suspend fun lists(): List<String> {
        val out = mutableListOf<String>(); var page: String? = null
        do {
            val o = call("GET", "/users/@me/lists?maxResults=100" + (page?.let { "&pageToken=${enc(it)}" } ?: "")) ?: break
            o.optJSONArray("items")?.let { a -> for (i in 0 until a.length()) out += a.getJSONObject(i).getString("id") }
            page = o.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    override suspend fun tasks(list: String, updatedMin: Instant?): List<RemoteTask> {
        val out = mutableListOf<RemoteTask>(); var page: String? = null
        do {
            val q = "?maxResults=100&showCompleted=true&showHidden=true&showDeleted=true" +
                (updatedMin?.let { "&updatedMin=${enc(it.toString())}" } ?: "") + (page?.let { "&pageToken=${enc(it)}" } ?: "")
            val o = call("GET", "/lists/${enc(list)}/tasks$q") ?: break
            out += TaskJson.parse(list, o.optJSONArray("items"))
            page = o.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return out
    }

    override suspend fun insert(list: String, t: LocalSyncTask): RemoteTask =
        TaskJson.one(list, call("POST", "/lists/${enc(list)}/tasks", TaskJson.body(t))!!)
    override suspend fun patch(list: String, id: String, t: LocalSyncTask): RemoteTask =
        TaskJson.one(list, call("PATCH", "/lists/${enc(list)}/tasks/${enc(id)}", TaskJson.body(t))!!)
    override suspend fun delete(list: String, id: String) { call("DELETE", "/lists/${enc(list)}/tasks/${enc(id)}") }
}

/** Pure JSON mapping (JVM-tested). Google `due` is date-only, sent as midnight UTC. */
object TaskJson {
    fun parse(list: String, items: JSONArray?): List<RemoteTask> = (0 until (items?.length() ?: 0)).mapNotNull { i -> runCatching { one(list, items!!.getJSONObject(i)) }.getOrNull() }
    fun one(list: String, o: JSONObject) = RemoteTask(
        googleId = o.getString("id"), list = list, title = o.optString("title"),
        due = o.optString("due").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.substring(0, 10)) },
        completed = o.optString("status") == "completed", deleted = o.optBoolean("deleted") || o.optBoolean("hidden") && o.optString("status") != "completed",
        updated = o.optString("updated").takeIf { it.isNotEmpty() }?.let(Instant::parse) ?: Instant.EPOCH,
    )
    fun body(t: LocalSyncTask): JSONObject = JSONObject().put("title", t.title)
        .put("status", if (t.completed) "completed" else "needsAction")
        .put("due", t.due?.let { "${it}T00:00:00.000Z" } ?: JSONObject.NULL)
        .apply { if (!t.completed) put("completed", JSONObject.NULL) }
}

/**
 * One sync pass: pull every list (incremental after the first run), then push local one-off changes.
 * Recurring PersonalHub tasks are never pushed (the API has no recurrence).
 */
class GoogleTasksSync(private val store: TaskSyncStore, private val client: TasksApi) {
    suspend fun run(now: Instant = Instant.now()): Pair<Int, Int> {
        val since = store.lastSync()?.minusSeconds(120)
        var pulled = 0
        for (list in client.lists()) client.tasks(list, since).forEach { store.applyRemote(it); pulled++ }
        var pushed = 0
        for (t in store.syncCandidates()) {
            val gid = t.googleId; val list = t.list ?: "@default"
            when {
                t.deleted && gid == null -> if (t.dirty) store.unlink(t.localId)
                t.deleted -> { runCatching { client.delete(list, gid!!) }.onFailure { if ((it as? ApiException)?.code != 404) throw it }; store.unlink(t.localId); pushed++ }
                gid == null -> { val r = client.insert("@default", t); store.linkPushed(t.localId, r.googleId, r.list, r.updated); pushed++ }
                t.dirty -> {
                    val r = runCatching { client.patch(list, gid, t) }.getOrElse { e ->
                        if ((e as? ApiException)?.code == 404) { client.insert("@default", t) } else throw e
                    }
                    store.linkPushed(t.localId, r.googleId, r.list, r.updated); pushed++
                }
            }
        }
        store.setLastSync(now)
        return pulled to pushed
    }
}
