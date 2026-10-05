package com.sem3.planner

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Config {
    const val PROJECT = "qnea-aff56"
    const val API_KEY = "AIzaSyAHDMiGbQT6mgYON0mMmGTfmeGSkKMgPBM"
    const val DOC_URL =
        "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents/syllabus_tracker/sem3_planner?key=$API_KEY"
}

data class Topic(val subject: String, val text: String, val done: Boolean)

object Repo {
    private const val PREFS = "planner"
    private const val KEY_LIST = "starred"
    private const val KEY_STATUS = "status"

    // Must be called off the main thread.
    fun fetch(): List<Topic> {
        val conn = URL(Config.DOC_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            val code = conn.responseCode
            if (code == 404) return emptyList()
            if (code != 200) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<Topic> {
        val values = JSONObject(body)
            .optJSONObject("fields")
            ?.optJSONObject("starred")
            ?.optJSONObject("arrayValue")
            ?.optJSONArray("values") ?: return emptyList()
        val out = ArrayList<Topic>()
        for (i in 0 until values.length()) {
            val f = values.getJSONObject(i)
                .optJSONObject("mapValue")
                ?.optJSONObject("fields") ?: continue
            val subject = f.optJSONObject("subject")?.optString("stringValue") ?: ""
            val text = f.optJSONObject("text")?.optString("stringValue") ?: ""
            val done = f.optJSONObject("done")?.optBoolean("booleanValue") ?: false
            out.add(Topic(subject, text, done))
        }
        return out
    }

    fun save(ctx: Context, list: List<Topic>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("s", it.subject).put("t", it.text).put("d", it.done))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun load(ctx: Context): List<Topic> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Topic(o.optString("s"), o.optString("t"), o.optBoolean("d"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveStatus(ctx: Context, status: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATUS, status).apply()
    }

    fun loadStatus(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATUS, "Not synced yet") ?: "Not synced yet"
}

object Fmt {
    fun pending(list: List<Topic>): List<Topic> = list.filter { !it.done }

    fun body(list: List<Topic>, max: Int): String {
        if (list.isEmpty()) return "Nothing starred yet. Star topics in the planner."
        val p = pending(list)
        if (p.isEmpty()) return "All starred topics done"
        val shown = p.take(max).joinToString("\n") { "\u2022 " + it.text }
        return if (p.size > max) shown + "\n+" + (p.size - max) + " more" else shown
    }
}

object Notifier {
    private const val CHANNEL = "starred"
    private const val NOTIF_ID = 1

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Starred topics", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Keeps today's starred topics visible"
            nm.createNotificationChannel(ch)
        }
    }

    fun show(ctx: Context, list: List<Topic>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (list.isEmpty()) {
            nm.cancel(NOTIF_ID)
            return
        }
        ensureChannel(ctx)

        val pending = Fmt.pending(list)
        val title = if (pending.isEmpty()) "All starred topics done"
        else "Today: " + pending.size + (if (pending.size == 1) " topic" else " topics")
        val text = Fmt.body(list, 8)
        val firstLine = if (pending.isEmpty()) "Nice work" else pending[0].text

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(firstLine)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()

        try {
            nm.notify(NOTIF_ID, n)
        } catch (e: SecurityException) {
            // Notification permission missing; nothing to do.
        }
    }
}

object Refresher {
    // Fetches from Firestore, updates cache, notification and widget. Off the main thread only.
    fun run(ctx: Context): String {
        val app = ctx.applicationContext
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val status = try {
            Repo.save(app, Repo.fetch())
            "Updated $time"
        } catch (e: Exception) {
            "Offline, showing saved list ($time)"
        }
        Repo.saveStatus(app, status)

        val list = Repo.load(app)
        try {
            Notifier.show(app, list)
        } catch (e: Exception) {
        }
        try {
            TopicWidget.updateAll(app, list)
        } catch (e: Exception) {
        }
        return status
    }
}
