package com.geosensorx.data.mux;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewAssetSettings {
    public ArrayList<String> playback_policies;
}
