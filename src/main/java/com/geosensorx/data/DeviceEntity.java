package com.geosensorx.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceEntity {

    private String name;
    private String newName;
    private String type;
    private String label;
    private String sim;
    private Long inactivityTimeout;
    private String accessToken;

}
