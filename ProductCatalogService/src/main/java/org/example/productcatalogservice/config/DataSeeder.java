package org.example.productcatalogservice.config;

import lombok.extern.slf4j.Slf4j;
import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.repos.CategoryRepo;
import org.example.productcatalogservice.repos.ProductRepo;
import org.example.productcatalogservice.services.ProductSearchIndexer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class DataSeeder implements ApplicationRunner {

    @Autowired
    private CategoryRepo categoryRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private ProductSearchIndexer productSearchIndexer;

    @Value("${app.seed-data:true}")
    private Boolean seedData;

    @Override
    public void run(ApplicationArguments args) {
        if (Boolean.TRUE.equals(seedData) && productRepo.count() == 0) {
            seed();
        }

        try {
            int count = productSearchIndexer.reindexAll();
            log.info("Indexed {} products in Elasticsearch", count);
        } catch (Exception exception) {
            log.warn("Elasticsearch unavailable at startup, search will use MySQL until POST /search/reindex: {}",
                    exception.getMessage());
        }
    }

    private void seed() {
        Category electronics = category("Electronics", "Phones, laptops and accessories");
        Category books = category("Books", "Fiction, non-fiction and technical books");
        Category fashion = category("Fashion", "Clothing, footwear and accessories");
        Category home = category("Home & Kitchen", "Appliances, cookware and decor");

        productRepo.saveAll(List.of(
                product("Samsung Galaxy S24", "6.2 inch AMOLED smartphone with 8GB RAM and a 50MP camera", 74999.0, electronics,
                        Map.of("RAM", "8 GB", "Storage", "256 GB", "Display", "6.2 inch AMOLED", "Battery", "4000 mAh")),
                product("Apple MacBook Air M3", "13 inch laptop with the Apple M3 chip, 16GB memory and 512GB SSD", 134900.0, electronics,
                        Map.of("Processor", "Apple M3", "Memory", "16 GB", "Storage", "512 GB SSD", "Weight", "1.24 kg")),
                product("Sony WH-1000XM5 Headphones", "Wireless noise cancelling over-ear headphones with 30 hour battery", 29990.0, electronics,
                        Map.of("Type", "Over-ear", "Battery", "30 hours", "Connectivity", "Bluetooth 5.2")),
                product("Clean Code", "A handbook of agile software craftsmanship by Robert C. Martin", 699.0, books,
                        Map.of("Author", "Robert C. Martin", "Pages", "464", "Language", "English")),
                product("Designing Data-Intensive Applications", "The big ideas behind reliable, scalable and maintainable systems by Martin Kleppmann", 1299.0, books,
                        Map.of("Author", "Martin Kleppmann", "Pages", "616", "Language", "English")),
                product("Men's Running Shoes", "Lightweight breathable running shoes with cushioned sole", 2499.0, fashion,
                        Map.of("Material", "Mesh", "Sole", "EVA", "Sizes", "6-11")),
                product("Cotton Kurta", "Regular fit cotton kurta for everyday wear", 899.0, fashion,
                        Map.of("Fabric", "Cotton", "Fit", "Regular", "Sleeve", "Full")),
                product("Prestige Pressure Cooker 5L", "Stainless steel pressure cooker suitable for gas and induction", 2199.0, home,
                        Map.of("Capacity", "5 litres", "Material", "Stainless steel", "Induction", "Yes")),
                product("Philips Air Fryer", "4.1 litre air fryer that cooks with up to 90% less fat", 7999.0, home,
                        Map.of("Capacity", "4.1 litres", "Power", "1400 W", "Warranty", "2 years"))
        ));
        log.info("Seeded sample catalog");
    }

    private Category category(String name, String description) {
        Optional<Category> optionalCategory = categoryRepo.findByNameIgnoreCase(name);
        if (optionalCategory.isPresent()) {
            return optionalCategory.get();
        }

        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return categoryRepo.save(category);
    }

    private Product product(String name, String description, Double price, Category category, Map<String, String> specifications) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategory(category);
        String image = "https://placehold.co/600x600?text=" + name.replace(' ', '+');
        product.setImageUrl(image);
        product.getImages().add(image);
        product.getSpecifications().putAll(specifications);
        return product;
    }
}
