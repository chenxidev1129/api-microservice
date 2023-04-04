package com.geosensorx.data.mux;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LiveStream {
    public String stream_key;
    public String status;
    public int reconnect_window;
    public ArrayList<PlaybackId> playback_ids;
    public NewAssetSettings new_asset_settings;
    public int max_continuous_duration;
    public String latency_mode;
    public String id;
    public long created_at;
    public ArrayList<String> recent_asset_ids;
}