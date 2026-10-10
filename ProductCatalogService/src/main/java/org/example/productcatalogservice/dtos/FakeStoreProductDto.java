package org.example.productcatalogservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class FakeStoreProductDto implements Serializable {
    private Long id;
    private String title;
    private String description;
    private String image;
    private String category;
    private Double price;
}
