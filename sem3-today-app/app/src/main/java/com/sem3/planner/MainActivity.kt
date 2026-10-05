package com.sem3.planner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var listView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        listView = findViewById(R.id.list)
        findViewById<Button>(R.id.refresh).setOnClickListener { refresh() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        Notifier.ensureChannel(this)
        Scheduler.schedule(this)
        showSaved()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun showSaved() {
        statusView.text = Repo.loadStatus(this)
        listView.text = Fmt.body(Repo.load(this), 50)
    }

    private fun refresh() {
        statusView.text = "Refreshing..."
        Thread {
            Refresher.run(applicationContext)
            runOnUiThread { showSaved() }
        }.start()
    }
}
