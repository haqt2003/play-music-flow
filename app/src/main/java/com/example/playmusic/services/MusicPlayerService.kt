package com.example.playmusic.services

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.playmusic.CHANNEL_ID
import com.example.playmusic.R
import com.example.playmusic.composables.formatDuration
import com.example.playmusic.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PREV = "prev"
const val PLAY_PAUSE = "play_pause"
const val NEXT = "next"
const val STOP = "stop"

class MusicPlayerService : Service() {

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {

        fun getService() = this@MusicPlayerService

        fun setMusicList(list: List<Track>) {
            this@MusicPlayerService.musicList = list.toMutableList()
        }

        fun currentDuration() = this@MusicPlayerService.currentDuration

        fun maxDuration() = this@MusicPlayerService.maxDuration

        fun isPlaying() = this@MusicPlayerService.isPlaying

        fun getCurrentTrack() = this@MusicPlayerService.currentTrack
    }

    private var mediaPlayer = MediaPlayer()

    private val currentTrack = MutableStateFlow(Track())

    private val maxDuration = MutableStateFlow(0f)
    private val currentDuration = MutableStateFlow(0f)

    private val scope = CoroutineScope(Dispatchers.Main)

    private var musicList = mutableListOf<Track>()

    private val isPlaying = MutableStateFlow(false)

    private var isRepeatOne = false

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val track = intent?.getParcelableExtra<Track>("track")
        currentTrack.value = track ?: currentTrack.value

        intent?.let {
            when (it.action) {
                PREV -> {
                    prev()
                }

                NEXT -> {
                    next()
                }

                PLAY_PAUSE -> {
                    playPause()
                }

                STOP -> {
                    stop()
                }

                else -> {
                    play(currentTrack.value)
                }
            }
        }
        return START_STICKY
    }

    fun prev() {
        job?.cancel()
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        val index = musicList.indexOf(currentTrack.value)
        val prevIndex = if (index == 0) musicList.size.minus(1) else index.minus(1)
        val prevItem = musicList[prevIndex]
        currentTrack.update { prevItem }
        mediaPlayer.setDataSource(this, getUri(currentTrack.value.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(currentTrack.value)
            updateDurations()
        }
    }

    fun next() {
        job?.cancel()
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        val index = musicList.indexOf(currentTrack.value)
        val nextIndex = index.plus(1).mod(musicList.size)
        val nextItem = musicList[nextIndex]
        currentTrack.update { nextItem }
        mediaPlayer.setDataSource(this, getUri(nextItem.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(currentTrack.value)
            updateDurations()
        }
    }

    fun repeat() {
        isRepeatOne = !isRepeatOne
    }

    fun playPause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        } else {
            mediaPlayer.start()
        }
        sendNotification(currentTrack.value)
    }

    private fun updateDurations() {
        job = scope.launch {
            if (mediaPlayer.isPlaying.not()) return@launch

            maxDuration.update { mediaPlayer.duration.toFloat() }

            while (true) {
                currentDuration.update { mediaPlayer.currentPosition.toFloat() }
                sendNotification(currentTrack.value)
                delay(1000)
                mediaPlayer.setOnCompletionListener {
                    if (isRepeatOne) {
                        mediaPlayer.start()
                    } else {
                        next()
                    }
                }
            }
        }
    }

    private fun play(track: Track) {
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(this, getUri(track.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(track)
            updateDurations()
        }
    }

    fun stop() {
        job?.cancel()
        mediaPlayer.stop()
        mediaPlayer.release()
        stopForeground(true)
        stopSelf()
    }

    fun seekTo(position: Int) {
        mediaPlayer.seekTo(position)
    }

    private fun getUri(id: Long) = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    private fun sendNotification(track: Track) {

        val session = MediaSessionCompat(this, "music")

        isPlaying.update { mediaPlayer.isPlaying }
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(session.sessionToken)

        val remainingTime = formatDuration((mediaPlayer.duration - mediaPlayer.currentPosition).toLong())

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(style)
            .setContentTitle(track.name)
            .setContentText("- $remainingTime")
            .addAction(R.drawable.ic_pre, "prev", createPrevPendingIntent())
            .addAction(
                if (mediaPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                "play_pause",
                createPlayPausePendingIntent()
            )
            .addAction(R.drawable.ic_next, "next", createNextPendingIntent())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.banner
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startForeground(1, notification)
            }
        } else {
            startForeground(1, notification)
        }
    }

    private fun createPrevPendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = PREV
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createPlayPausePendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = PLAY_PAUSE
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNextPendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = NEXT
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createStopPendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = STOP
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}