package org.example.productcatalogservice.services;

import org.example.productcatalogservice.exceptions.CategoryNotFoundException;
import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.repos.CategoryRepo;
import org.example.productcatalogservice.repos.ProductRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService implements ICategoryService {

    @Autowired
    private CategoryRepo categoryRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private ProductSearchIndexer productSearchIndexer;

    @Override
    public List<Category> getAllCategories() {
        return categoryRepo.findAll(Sort.by("name"));
    }

    @Override
    public Category getCategoryById(Long id) {
        Optional<Category> optionalCategory = categoryRepo.findById(id);
        if (optionalCategory.isEmpty()) {
            throw new CategoryNotFoundException("Category with id " + id + " not found");
        }
        return optionalCategory.get();
    }

    @Override
    public Category createCategory(Category category) {
        if (category.getName() == null || category.getName().isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (categoryRepo.findByNameIgnoreCase(category.getName().trim()).isPresent()) {
            throw new IllegalArgumentException("Category '" + category.getName() + "' already exists");
        }
        category.setId(null);
        category.setName(category.getName().trim());
        return categoryRepo.save(category);
    }

    @Override
    public Category updateCategory(Long id, Category input) {
        Category category = getCategoryById(id);
        if (input.getName() != null && !input.getName().isBlank()) {
            Optional<Category> optionalCategory = categoryRepo.findByNameIgnoreCase(input.getName().trim());
            if (optionalCategory.isPresent() && !optionalCategory.get().getId().equals(id)) {
                throw new IllegalArgumentException("Category '" + input.getName() + "' already exists");
            }
            category.setName(input.getName().trim());
        }
        if (input.getDescription() != null) {
            category.setDescription(input.getDescription());
        }
        Category saved = categoryRepo.save(category);

        // Search documents carry the category name, so re-index the products in this category.
        for (Product product : productRepo.findAllByCategory_Id(id)) {
            productSearchIndexer.index(product);
        }
        return saved;
    }

    @Override
    public Page<Product> getProductsInCategory(Long categoryId, Integer pageNumber, Integer pageSize) {
        getCategoryById(categoryId);
        return productRepo.findAllByCategory_Id(categoryId, PageRequest.of(pageNumber, pageSize, Sort.by("name")));
    }
}
