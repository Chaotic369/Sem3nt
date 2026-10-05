package com.sem3.planner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TopicWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Show the saved list immediately, then refresh from Firestore in the background.
        updateAll(context, Repo.load(context))
        val result = goAsync()
        Thread {
            try {
                Refresher.run(context)
            } finally {
                result.finish()
            }
        }.start()
    }

    companion object {
        fun updateAll(ctx: Context, list: List<Topic>) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TopicWidget::class.java))
            if (ids.isEmpty()) return

            val rv = RemoteViews(ctx.packageName, R.layout.topic_widget)
            rv.setTextViewText(R.id.widget_list, Fmt.body(list, 7))

            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            mgr.updateAppWidget(ids, rv)
        }
    }
}
