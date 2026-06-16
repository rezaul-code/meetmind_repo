package com.office.meetmind;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.office.meetmind.databinding.ItemRecordingBinding;

import java.util.ArrayList;
import java.util.List;

public class RecordingAdapter extends RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder> {

    public interface OnRecordingClickListener {
        void onRecordingClicked(RecordingModel recordingModel);
    }

    private final List<RecordingModel> recordings = new ArrayList<>();
    private final OnRecordingClickListener listener;

    public RecordingAdapter(OnRecordingClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<RecordingModel> newRecordings) {
        recordings.clear();
        if (newRecordings != null) {
            recordings.addAll(newRecordings);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecordingBinding binding = ItemRecordingBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new RecordingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingViewHolder holder, int position) {
        holder.bind(recordings.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    static class RecordingViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecordingBinding binding;

        RecordingViewHolder(ItemRecordingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(RecordingModel recordingModel, OnRecordingClickListener listener) {
            binding.fileNameText.setText(recordingModel.getDisplayName());
            binding.dateText.setText(recordingModel.getDateText());
            binding.durationText.setText(recordingModel.getDurationText());
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRecordingClicked(recordingModel);
                }
            });
        }
    }

}
