package org.example.productcatalogservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.productcatalogservice.clients.AuthClient;
import org.example.productcatalogservice.dtos.CategoryDto;
import org.example.productcatalogservice.dtos.ProductDto;
import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.services.ICategoryService;
import org.example.productcatalogservice.utils.mappers.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Categories", description = "Browse products by category")
@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private AuthClient authClient;

    @Operation(summary = "List all categories")
    @GetMapping
    public List<CategoryDto> getAllCategories() {
        List<CategoryDto> response = new ArrayList<>();
        List<Category> categories = categoryService.getAllCategories();
        for (Category category : categories) {
            response.add(ProductMapper.from(category));
        }
        return response;
    }

    @Operation(summary = "Get a category")
    @GetMapping("/{id}")
    public CategoryDto getCategory(@PathVariable Long id) {
        return ProductMapper.from(categoryService.getCategoryById(id));
    }

    @Operation(summary = "Browse the products in a category, page by page")
    @GetMapping("/{id}/products")
    public Page<ProductDto> getProductsInCategory(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "0") Integer pageNumber,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNumber must be >= 0 and pageSize between 1 and 100");
        }
        return categoryService.getProductsInCategory(id, pageNumber, pageSize).map(ProductMapper::from);
    }

    @Operation(summary = "Create a category (admin only)")
    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                                      @RequestBody CategoryDto categoryDto) {
        authClient.requireAdmin(authorizationHeader);
        Category category = categoryService.createCategory(ProductMapper.from(categoryDto));
        return new ResponseEntity<>(ProductMapper.from(category), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a category (admin only)")
    @PutMapping("/{id}")
    public CategoryDto updateCategory(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                      @PathVariable Long id, @RequestBody CategoryDto categoryDto) {
        authClient.requireAdmin(authorizationHeader);
        return ProductMapper.from(categoryService.updateCategory(id, ProductMapper.from(categoryDto)));
    }
}
