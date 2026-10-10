package org.example.productcatalogservice.services;

import lombok.extern.slf4j.Slf4j;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.models.ProductDocument;
import org.example.productcatalogservice.repos.ProductRepo;
import org.example.productcatalogservice.utils.mappers.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Indexing failures never fail the product write; search falls back to MySQL until a reindex.
@Slf4j
@Component
public class ProductSearchIndexer {

    private static final long RETRY_AFTER_MILLIS = 30_000;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ProductRepo productRepo;

    private volatile boolean indexReady = false;

    private volatile long unavailableUntil = 0;

    public void index(Product product) {
        if (isSkipped()) {
            return;
        }
        try {
            ensureIndex();
            elasticsearchOperations.save(ProductMapper.toDocument(product));
        } catch (Exception exception) {
            markUnavailable(exception);
        }
    }

    public void remove(Long productId) {
        if (isSkipped()) {
            return;
        }
        try {
            ensureIndex();
            elasticsearchOperations.delete(String.valueOf(productId), ProductDocument.class);
        } catch (Exception exception) {
            markUnavailable(exception);
        }
    }

    public int reindexAll() {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(ProductDocument.class);
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
        indexOperations.createWithMapping();
        indexReady = true;
        unavailableUntil = 0;

        List<Product> products = productRepo.findAll();
        List<ProductDocument> documents = new ArrayList<>();
        for (Product product : products) {
            documents.add(ProductMapper.toDocument(product));
        }
        if (!documents.isEmpty()) {
            elasticsearchOperations.save(documents);
        }
        indexOperations.refresh();
        return documents.size();
    }

    private void ensureIndex() {
        if (indexReady) {
            return;
        }
        IndexOperations indexOperations = elasticsearchOperations.indexOps(ProductDocument.class);
        if (!indexOperations.exists()) {
            indexOperations.createWithMapping();
        }
        indexReady = true;
    }

    private boolean isSkipped() {
        return System.currentTimeMillis() < unavailableUntil;
    }

    private void markUnavailable(Exception exception) {
        unavailableUntil = System.currentTimeMillis() + RETRY_AFTER_MILLIS;
        log.warn("Elasticsearch unavailable, product indexing skipped for {}s: {}", RETRY_AFTER_MILLIS / 1000, exception.getMessage());
    }
}
