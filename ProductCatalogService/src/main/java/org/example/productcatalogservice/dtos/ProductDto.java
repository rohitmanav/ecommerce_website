package org.example.productcatalogservice.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
public class ProductDto {
    private Long id;

    @NotBlank
    private String name;

    private String description;

    private String imageUrl;

    @NotNull
    @Positive
    private Double price;

    // Pass an existing category id, or a name to create the category if it does not exist.
    @NotNull
    private CategoryDto category;

    private List<String> images = new ArrayList<>();

    private Map<String, String> specifications = new HashMap<>();
}
