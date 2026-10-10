package org.example.productcatalogservice.services;

import org.example.productcatalogservice.clients.ProductCacheClient;
import org.example.productcatalogservice.dtos.CategoryDto;
import org.example.productcatalogservice.dtos.ProductDto;
import org.example.productcatalogservice.exceptions.CategoryNotFoundException;
import org.example.productcatalogservice.exceptions.ProductNotFoundException;
import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.repos.CategoryRepo;
import org.example.productcatalogservice.repos.ProductRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageProductServiceTest {

    @Mock
    private ProductRepo productRepo;

    @Mock
    private CategoryRepo categoryRepo;

    @Mock
    private ProductCacheClient productCacheClient;

    @Mock
    private ProductSearchIndexer productSearchIndexer;

    @InjectMocks
    private StorageProductService productService;

    @Test
    void getProductByIdReturnsCachedProductWithoutHittingDatabase() {
        ProductDto productDto = new ProductDto();
        productDto.setId(1L);
        productDto.setName("Cached phone");
        productDto.setPrice(10.0);
        CategoryDto categoryDto = new CategoryDto();
        categoryDto.setId(3L);
        categoryDto.setName("Electronics");
        productDto.setCategory(categoryDto);
        when(productCacheClient.get(1L)).thenReturn(productDto);

        Product product = productService.getProductById(1L);

        assertEquals("Cached phone", product.getName());
        assertEquals("Electronics", product.getCategory().getName());
        verifyNoInteractions(productRepo);
    }

    @Test
    void getProductByIdLoadsFromDatabaseAndFillsCacheOnMiss() {
        Product stored = product(2L, "Laptop");
        when(productCacheClient.get(2L)).thenReturn(null);
        when(productRepo.findById(2L)).thenReturn(Optional.of(stored));

        Product product = productService.getProductById(2L);

        assertSame(stored, product);
        verify(productCacheClient).put(any(ProductDto.class));
    }

    @Test
    void createProductCreatesMissingCategoryByNameAndIndexesProduct() {
        Category input = new Category();
        input.setName("Toys");
        Product product = new Product();
        product.setName("Puzzle");
        product.setPrice(299.0);
        product.setCategory(input);
        when(categoryRepo.findByNameIgnoreCase("Toys")).thenReturn(Optional.empty());
        when(categoryRepo.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        when(productRepo.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product saved = productService.createProduct(product);

        assertEquals(9L, saved.getCategory().getId());
        verify(productSearchIndexer).index(saved);
    }

    @Test
    void createProductRejectsUnknownCategoryId() {
        Category input = new Category();
        input.setId(404L);
        Product product = new Product();
        product.setCategory(input);
        when(categoryRepo.findById(404L)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> productService.createProduct(product));
        verify(productRepo, never()).save(any());
    }

    @Test
    void replaceProductEvictsCacheAndReindexes() {
        Product existing = product(5L, "Old name");
        Product input = product(null, "New name");
        when(productRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepo.findById(existing.getCategory().getId())).thenReturn(Optional.of(existing.getCategory()));
        when(productRepo.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product saved = productService.replaceProduct(5L, input);

        assertEquals(5L, saved.getId());
        assertEquals("New name", saved.getName());
        verify(productCacheClient).evict(5L);
        verify(productSearchIndexer).index(saved);
    }

    @Test
    void deleteUnknownProductThrows() {
        when(productRepo.existsById(77L)).thenReturn(false);

        assertThrows(ProductNotFoundException.class, () -> productService.deleteProduct(77L));
        verify(productRepo, never()).deleteById(any());
    }

    private Product product(Long id, String name) {
        Category category = new Category();
        category.setId(3L);
        category.setName("Electronics");
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(100.0);
        product.setCategory(category);
        return product;
    }
}
