package com.geosensorx.data.mux;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApiModel
public class MuxInfoData<T> {
    private final T data;

    public MuxInfoData() {
        this(null);
    }

    @JsonCreator
    public MuxInfoData(@JsonProperty("data") T data) {
        this.data = data;
    }

    @ApiModelProperty(
            position = 1,
            value = "Info of the entities",
            readOnly = true
    )
    public T getData() {
        return this.data;
    }
    public static <T> MuxInfoData<T> emptyPageData() {
        return new MuxInfoData();
    }

}
