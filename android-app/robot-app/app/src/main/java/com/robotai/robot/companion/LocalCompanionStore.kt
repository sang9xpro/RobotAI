package com.robotai.robot.companion

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import com.robotai.robot.MainActivity
import com.robotai.robot.auth.UserSession
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class Reminder(val id: String, val text: String, val at: Long)
class LocalCompanionStore(private val context: Context, val scope: String) {
    val prefs = context.getSharedPreferences("companion-$scope", 0)
    fun reminders(): List<Reminder> = runCatching {
        val a = JSONArray(prefs.getString("reminders", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { j -> Reminder(j.getString("id"), j.getString("text"), j.getLong("at")) } }
    }.getOrDefault(emptyList())
    private fun save(items: List<Reminder>) {
        val a = JSONArray(); items.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.at)) }
        check(prefs.edit().putString("reminders", a.toString()).commit()) { "Không lưu được nhắc việc" }
    }
    fun add(text: String, at: Long) {
        require(text.isNotBlank() && text.length <= 200 && at > System.currentTimeMillis())
        val r = Reminder(UUID.randomUUID().toString(), text.trim(), at)
        save(reminders() + r); schedule(r)
    }
    fun remove(id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(id))
        save(reminders().filterNot { it.id == id })
    }
    private fun pending(id: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("robotai://reminder/$scope/$id"))
            .putExtra("scope", scope).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(r: Reminder) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            maxOf(System.currentTimeMillis() + 1000, r.at), pending(r.id))
    }
    companion object {
        fun key(session: UserSession) = MessageDigest.getInstance("SHA-256")
            .digest("${session.baseUrl}:${session.userId}".toByteArray()).joinToString("") { "%02x".format(it) }
        fun activate(context: Context, session: UserSession?) {
            val active = context.getSharedPreferences("companion-active", 0)
            val next = session?.let { key(it) }
            val old = active.getString("scope", null)
            if (old != next) {
                context.getSystemService(NotificationManager::class.java).cancelAll()
                active.edit().putString("scope", next).commit()
            }
            if (next != null) LocalCompanionStore(context, next).let { store -> store.reminders().forEach { store.schedule(it) } }
        }
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val active = context.getSharedPreferences("companion-active", 0).getString("scope", null) ?: return
        val store = LocalCompanionStore(context, active)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            store.reminders().forEach { store.schedule(it) }; return
        }
        if (intent.getStringExtra("scope") != active) return
        val r = store.reminders().find { it.id == intent.getStringExtra("id") } ?: return
        if (System.currentTimeMillis() < r.at) { store.schedule(r); return }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminders", "Nhắc việc", NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(r.id.hashCode(), Notification.Builder(context, "reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Ken nhắc bạn")
            .setContentText(r.text).setContentIntent(open).setAutoCancel(true).build())
        store.remove(r.id)
    }
}
