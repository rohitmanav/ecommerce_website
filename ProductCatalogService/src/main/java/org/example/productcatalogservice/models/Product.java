package org.example.productcatalogservice.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@Entity(name="products")
public class Product extends BaseModel {
    private String name;
    @Column(length = 2000)
    private String description;
    private String imageUrl;
    private Double price;
    @ManyToOne
    @JsonManagedReference
    private Category category;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images")
    @Column(name = "image_url")
    @OrderColumn(name = "position")
    @Fetch(FetchMode.SUBSELECT)
    private List<String> images = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_specifications")
    @MapKeyColumn(name = "spec_name")
    @Column(name = "spec_value")
    @Fetch(FetchMode.SUBSELECT)
    private Map<String, String> specifications = new HashMap<>();
}
