package org.example.productcatalogservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.productcatalogservice.clients.AuthClient;
import org.example.productcatalogservice.dtos.ProductDto;
import org.example.productcatalogservice.exceptions.ProductNotFoundException;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.services.IProductService;
import org.example.productcatalogservice.utils.mappers.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Products", description = "Product listings and details")
@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private IProductService productService;

    @Autowired
    private AuthClient authClient;

    @Operation(summary = "List all products")
    @GetMapping
    public List<ProductDto> getAllProducts() {
        List<ProductDto> response = new ArrayList<>();
      List<Product> products = productService.getAllProducts();
      for(Product product : products) {
          response.add(ProductMapper.from(product));
      }
      return response;
    }

    @Operation(summary = "Get product details", description = "Served from the Redis cache when available")
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable Long id) {
        if (id == 0) {
                 throw new IllegalArgumentException("Please pass id > 0");
        } else if(id < 0) {
                throw new IllegalArgumentException("Invalid Id");
        }

        Product product = productService.getProductById(id);
        if (product != null) {
            ProductDto productDto = ProductMapper.from(product);
            return new ResponseEntity<>(productDto,HttpStatus.OK);
        } else {
            throw new ProductNotFoundException("Product with requested id not found");
        }
    }

    @Operation(summary = "Create a product (admin only)")
    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                                    @Valid @RequestBody ProductDto productDto) {
        authClient.requireAdmin(authorizationHeader);
        Product product = ProductMapper.from(productDto);
        Product response = productService.createProduct(product);
        return new ResponseEntity<>(ProductMapper.from(response), HttpStatus.CREATED);
    }


    @Operation(summary = "Replace a product (admin only)")
    @PutMapping("/{id}")
    public ProductDto replaceProduct(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                     @PathVariable Long id, @Valid @RequestBody ProductDto productDto) {
        authClient.requireAdmin(authorizationHeader);
        Product product = productService.replaceProduct(id, ProductMapper.from(productDto));
        if(product != null) {
            return ProductMapper.from(product);
        } else {
            throw new ProductNotFoundException("Product with requested id not found");
        }
    }

    @Operation(summary = "Delete a product (admin only)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                              @PathVariable Long id) {
        authClient.requireAdmin(authorizationHeader);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
