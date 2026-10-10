package org.example.productcatalogservice.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class SearchRequestDto {
    private String query;

    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    @Min(0)
    private Integer pageNumber = 0;

    private List<SortParam> sortParamList = new ArrayList<>();
}
