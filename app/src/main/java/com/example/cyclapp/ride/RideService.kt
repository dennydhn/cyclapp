package com.example.cyclapp.ride

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.cyclapp.R
import com.example.cyclapp.data.db.DatabaseProvider
import com.example.cyclapp.location.LocationTracker
import kotlinx.coroutines.*

class RideService : Service(){
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var tracker: LocationTracker
    private lateinit var engine: RideEngine

    override fun onCreate(){
        super.onCreate()
        val dao = DatabaseProvider.get(this).rideDao()
        engine = RideEngine(dao, scope)
        tracker = LocationTracker(this){ location ->
            engine.onLocation(location)
        }
        createChannel()
    }
    override fun onStartCommand(intent: Intent?, flags:Int, startId: Int): Int{
        startForeground(NOTIFICATION_ID, notification("Ride recording active"))

        when (intent?.action){
            ACTION_START -> scope.launch {
                engine.start(intent.getStringExtra(EXTRA_ROUTE))
                tracker.start()
            }
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.resume()
            ACTION_STOP -> scope.launch {
                tracker.stop()
                engine.finish()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }
    private fun notification(text: String):Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Cycle Tracker")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL,
                "Cycling recording",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onDestroy(){
        tracker.stop()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "cycling_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "cycle.START"
        const val ACTION_PAUSE = "cucle.PAUSE"
        const val ACTION_RESUME = "cycle.RESUME"
        const val ACTION_STOP = "cycle.STOP"
        const val EXTRA_ROUTE = "route_name"
    }
}