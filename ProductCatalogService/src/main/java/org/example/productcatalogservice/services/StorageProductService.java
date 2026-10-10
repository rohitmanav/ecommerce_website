package org.example.productcatalogservice.services;

import org.example.productcatalogservice.clients.ProductCacheClient;
import org.example.productcatalogservice.dtos.ProductDto;
import org.example.productcatalogservice.exceptions.CategoryNotFoundException;
import org.example.productcatalogservice.exceptions.ProductNotFoundException;
import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.repos.CategoryRepo;
import org.example.productcatalogservice.repos.ProductRepo;
import org.example.productcatalogservice.utils.mappers.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service("sps")
@Primary
public class StorageProductService implements IProductService {

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private CategoryRepo categoryRepo;

    @Autowired
    private ProductCacheClient productCacheClient;

    @Autowired
    private ProductSearchIndexer productSearchIndexer;

    @Override
    public Product getProductById(Long id) {
        ProductDto productDto = productCacheClient.get(id);
        if (productDto != null) {
            return ProductMapper.from(productDto);
        }

        Optional<Product> optionalProduct = productRepo.findById(id);
        if(optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            productCacheClient.put(ProductMapper.from(product));
            return product;
        }

       return null;
    }

    @Override
    public List<Product> getAllProducts() {
        return productRepo.findAll();
    }

    @Override
    public Product createProduct(Product product) {
        product.setId(null);
        product.setCategory(resolveCategory(product.getCategory()));
        Product saved = productRepo.save(product);
        productSearchIndexer.index(saved);
        return saved;
    }

    @Override
    public Product replaceProduct(Long id, Product input) {
        Optional<Product> optionalProduct = productRepo.findById(id);
        if (optionalProduct.isPresent()) {
            input.setId(id);
            input.setCreatedAt(optionalProduct.get().getCreatedAt());
            input.setCategory(resolveCategory(input.getCategory()));
            Product saved = productRepo.save(input);
            productCacheClient.evict(id);
            productSearchIndexer.index(saved);
            return saved;
        }

        return null;
    }

    @Override
    public void deleteProduct(Long id) {
        if (!productRepo.existsById(id)) {
            throw new ProductNotFoundException("Product with requested id not found");
        }
        productRepo.deleteById(id);
        productCacheClient.evict(id);
        productSearchIndexer.remove(id);
    }

    // An existing category is looked up by id or name; a new name creates the category.
    private Category resolveCategory(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("Product category is required");
        }
        if (category.getId() != null) {
            Optional<Category> optionalCategory = categoryRepo.findById(category.getId());
            if (optionalCategory.isEmpty()) {
                throw new CategoryNotFoundException("Category with id " + category.getId() + " not found");
            }
            return optionalCategory.get();
        }
        if (category.getName() == null || category.getName().isBlank()) {
            throw new IllegalArgumentException("Pass a category id or name");
        }

        Optional<Category> optionalCategory = categoryRepo.findByNameIgnoreCase(category.getName().trim());
        if (optionalCategory.isPresent()) {
            return optionalCategory.get();
        }

        Category newCategory = new Category();
        newCategory.setName(category.getName().trim());
        newCategory.setDescription(category.getDescription());
        return categoryRepo.save(newCategory);
    }
}
