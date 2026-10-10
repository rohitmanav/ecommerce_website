package org.example.productcatalogservice.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SortParam {
    // One of: price, name.
    private String paramName;
    private SortOrder sortOrder;
}
