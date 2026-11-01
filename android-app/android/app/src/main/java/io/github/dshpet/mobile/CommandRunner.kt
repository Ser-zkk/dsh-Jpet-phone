package io.github.dshpet.mobile

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build

/**
 * Local, offline action layer: the pet answers a handful of imperative phrases by
 * actually doing the thing instead of forwarding them to the model. Anything that
 * is not matched returns null and the normal chat path takes over.
 */
object CommandRunner {

    fun run(context: Context, raw: String): String? {
        val text = raw.trim().trim('。', '！', '!', '，', ',', ' ', '？', '?')
        if (text.isEmpty()) return null

        Regex("^(打开|启动|开|运行)(一下|个)?(.+)$").find(text)?.let { match ->
            val name = match.groupValues[3].trim()
            if (name.isNotEmpty()) {
                return launch(context, name) ?: "没找到「$name」这个应用。"
            }
        }
        if (text.contains("回桌面") || text == "桌面" || text.contains("回到桌面")) {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(home)
            return "回桌面了。"
        }
        if (text.contains("静音")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            return "静音了。"
        }
        if (text.contains("音量") || text.contains("声音")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            when {
                text.contains("大") || text.contains("加") || text.contains("响") ->
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                text.contains("小") || text.contains("减") || text.contains("轻") ->
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            }
            val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            return "音量现在是 ${if (max > 0) now * 100 / max else 0}%。"
        }
        if (text.contains("电量") || text.contains("多少电")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return "还剩 $level%，够用就别慌。"
        }
        if (Regex("(要不要|需要|帮我)?(查|说)?(一下)?我的?手机(型号|是什么)").containsMatchIn(text)) {
            return "这台是 ${Build.MANUFACTURER} ${Build.MODEL}，安卓 ${Build.VERSION.RELEASE}。"
        }
        return null
    }

    private fun launch(context: Context, name: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = runCatching {
            pm.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        val needle = name.lowercase()
        val hit = candidates.firstOrNull {
            val label = runCatching { it.loadLabel(pm).toString() }.getOrDefault("")
            label.contains(name) || label.lowercase().contains(needle) ||
                it.activityInfo.packageName.lowercase().contains(needle)
        } ?: return null
        val call = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(call)
            "开了，自己看。"
        }.getOrElse { "打不开「${runCatching { hit.loadLabel(pm) }.getOrDefault(name)}」，系统拦了。" }
    }
}
