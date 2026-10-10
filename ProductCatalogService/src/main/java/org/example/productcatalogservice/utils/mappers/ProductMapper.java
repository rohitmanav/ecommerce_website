package org.example.productcatalogservice.utils.mappers;

import org.example.productcatalogservice.dtos.CategoryDto;
import org.example.productcatalogservice.dtos.ProductDto;
import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.models.ProductDocument;

import java.util.ArrayList;
import java.util.HashMap;

public class ProductMapper {

    public static ProductDto from(Product product) {
        ProductDto productDto = new ProductDto();
        productDto.setId(product.getId());
        productDto.setName(product.getName());
        productDto.setDescription(product.getDescription());
        productDto.setPrice(product.getPrice());
        productDto.setImageUrl(product.getImageUrl());
        if (product.getImages() != null) {
            productDto.setImages(new ArrayList<>(product.getImages()));
        }
        if (product.getSpecifications() != null) {
            productDto.setSpecifications(new HashMap<>(product.getSpecifications()));
        }
        if(product.getCategory() != null) {
            productDto.setCategory(from(product.getCategory()));
        }
        return productDto;
    }

    public static Product from(ProductDto productDto) {
        Product product = new Product();
        product.setId(productDto.getId());
        product.setName(productDto.getName());
        product.setPrice(productDto.getPrice());
        product.setImageUrl(productDto.getImageUrl());
        product.setDescription(productDto.getDescription());
        if (productDto.getImages() != null) {
            product.setImages(new ArrayList<>(productDto.getImages()));
        }
        if (productDto.getSpecifications() != null) {
            product.setSpecifications(new HashMap<>(productDto.getSpecifications()));
        }
        if(productDto.getCategory() != null) {
            product.setCategory(from(productDto.getCategory()));
        }
        return product;
    }

    public static CategoryDto from(Category category) {
        CategoryDto categoryDto = new CategoryDto();
        categoryDto.setId(category.getId());
        categoryDto.setName(category.getName());
        categoryDto.setDescription(category.getDescription());
        return categoryDto;
    }

    public static Category from(CategoryDto categoryDto) {
        Category category = new Category();
        category.setId(categoryDto.getId());
        category.setName(categoryDto.getName());
        category.setDescription(categoryDto.getDescription());
        return category;
    }

    public static ProductDocument toDocument(Product product) {
        ProductDocument document = new ProductDocument();
        document.setId(String.valueOf(product.getId()));
        document.setName(product.getName());
        document.setDescription(product.getDescription());
        document.setPrice(product.getPrice());
        if (product.getCategory() != null) {
            document.setCategoryId(product.getCategory().getId());
            document.setCategoryName(product.getCategory().getName());
        }
        return document;
    }
}
