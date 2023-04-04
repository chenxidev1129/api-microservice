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
public class MuxListData<T> {
    private final List<T> data;
    private final boolean hasNext;

    public MuxListData() {
        this(Collections.emptyList(), false);
    }

    @JsonCreator
    public MuxListData(@JsonProperty("data") List<T> data, @JsonProperty("hasNext") boolean hasNext) {
        this.data = data;
        this.hasNext = hasNext;
    }

    @ApiModelProperty(
            position = 1,
            value = "Array of the entities",
            readOnly = true
    )
    public List<T> getData() {
        return this.data;
    }

    @ApiModelProperty(
            position = 2,
            value = "'false' value indicates the end of the result set",
            readOnly = true
    )
    @JsonProperty("hasNext")
    public boolean hasNext() {
        return this.hasNext;
    }

    public static <T> MuxListData<T> emptyPageData() {
        return new MuxListData();
    }

    public <D> MuxListData<D> mapData(Function<T, D> mapper) {
        return new MuxListData((List)this.getData().stream().map(mapper).collect(Collectors.toList()), this.hasNext());
    }
}
