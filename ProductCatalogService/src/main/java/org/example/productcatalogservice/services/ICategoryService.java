package org.example.productcatalogservice.services;

import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.models.Product;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ICategoryService {

    List<Category> getAllCategories();

    Category getCategoryById(Long id);

    Category createCategory(Category category);

    Category updateCategory(Long id, Category input);

    Page<Product> getProductsInCategory(Long categoryId, Integer pageNumber, Integer pageSize);
}
