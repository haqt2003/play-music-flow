package com.example.playmusic.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.playmusic.composables.formatDuration
import com.example.playmusic.databinding.LayoutItemBinding
import com.example.playmusic.models.Track

class TrackAdapter(
    private val tracks: MutableList<Track>,
    private val listener: OnAdapterListener
) :
    RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TrackViewHolder {
        val binding = LayoutItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(track)

        holder.itemView.setOnClickListener {
            listener.onClick(track)
        }
    }

    override fun getItemCount(): Int {
        return tracks.size
    }

    interface OnAdapterListener {
        fun onClick(track: Track)
    }

    class TrackViewHolder(private val binding: LayoutItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(track: Track) {
            binding.tvName.text = track.name
            binding.tvDuration.text = formatDuration(track.duration)
        }
    }
}