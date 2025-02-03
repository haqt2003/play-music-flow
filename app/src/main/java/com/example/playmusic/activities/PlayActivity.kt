package com.example.playmusic.activities

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.playmusic.services.MusicPlayerService
import com.example.playmusic.R
import com.example.playmusic.models.Track
import com.example.playmusic.composables.formatDuration
import com.example.playmusic.databinding.ActivityPlayBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayActivity : AppCompatActivity() {
    private val binding: ActivityPlayBinding by lazy {
        ActivityPlayBinding.inflate(layoutInflater)
    }

    private val isPlaying = MutableStateFlow(false)
    private val maxDuration = MutableStateFlow(0f)
    private val currentDuration = MutableStateFlow(0f)
    private val currentTrack = MutableStateFlow(Track())

    private lateinit var songs: MutableList<Track>

    private var service: MusicPlayerService? = null
    private var isBound = false

    private lateinit var rotateAnimator: ObjectAnimator
    private var isRotating = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as MusicPlayerService.MusicBinder).getService()
            binder.setMusicList(songs)
            lifecycleScope.launch {
                binder.isPlaying().collectLatest {
                    isPlaying.value = it
                }
            }

            lifecycleScope.launch {
                binder.maxDuration().collectLatest {
                    maxDuration.value = it
                }
            }

            lifecycleScope.launch {
                binder.currentDuration().collectLatest {
                    currentDuration.value = it
                }
            }

            lifecycleScope.launch {
                binder.getCurrentTrack().collectLatest {
                    currentTrack.value = it
                }
            }
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cl_play)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        rotateAnimator = ObjectAnimator.ofFloat(binding.ivThumbnail, "rotation", 0f, 360f).apply {
            duration = 12000
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
        }

        val receivedTracks = intent.getParcelableArrayListExtra<Track>("tracks")
        if (receivedTracks.isNullOrEmpty()) {
            finish()
            return
        }
        songs = receivedTracks.toMutableList()

        val track = intent.getParcelableExtra("track") ?: songs.first()

        val intent = Intent(this, MusicPlayerService::class.java).apply {
            putExtra("track", track)
        }
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        with(binding) {
            tvTitle.text = track.name
            sbTimeline.max = track.duration.toInt()
            tvDuration.text = formatDuration(track.duration)

            ivPre.setOnClickListener {
                service?.prev()
            }

            ivPlay.setOnClickListener {
                service?.playPause()
            }

            ivPause.setOnClickListener {
                service?.playPause()
            }

            ivNext.setOnClickListener {
                service?.next()
            }

            ivClose.setOnClickListener {
                service?.stop()
                finish()
            }

            sbTimeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.tvProgress.text = formatDuration(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    service?.seekTo(seekBar?.progress ?: 0)
                }
            })
        }

        lifecycleScope.launch {
            currentTrack.collect {
                binding.tvTitle.text = it.name
                binding.tvDuration.text = formatDuration(it.duration)
            }
        }

        lifecycleScope.launch {
            isPlaying.collect { playing ->
                binding.ivPlay.visibility = if (playing) View.GONE else View.VISIBLE
                binding.ivPause.visibility = if (playing) View.VISIBLE else View.GONE

                if (playing) {
                    if (!isRotating) {
                        rotateAnimator.start()
                        isRotating = true
                    } else {
                        rotateAnimator.resume()
                    }
                } else {
                    rotateAnimator.pause()
                }
            }
        }



        lifecycleScope.launch {
            maxDuration.collect {
                binding.sbTimeline.max = it.toInt()
            }
        }

        lifecycleScope.launch {
            currentDuration.collect {
                binding.sbTimeline.progress = it.toInt()
                binding.tvProgress.text = formatDuration(it.toLong())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}