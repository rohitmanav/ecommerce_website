package org.example.productcatalogservice.services;

import co.elastic.clients.elasticsearch._types.SortOrder;
import lombok.extern.slf4j.Slf4j;
import org.example.productcatalogservice.dtos.SortParam;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.models.ProductDocument;
import org.example.productcatalogservice.repos.ProductRepo;
import org.example.productcatalogservice.utils.SortParamUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Primary
public class ElasticsearchSearchService implements ISearchService {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private JpaSearchService jpaSearchService;

    @Override
    public Page<Product> searchProducts(String query, Integer pageSize, Integer pageNumber, List<SortParam> sortParamList) {
        List<Map.Entry<String, Boolean>> sortFields = SortParamUtil.toElasticsearchSort(sortParamList);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        try {
            NativeQueryBuilder queryBuilder = NativeQuery.builder().withPageable(pageable);
            if (query == null || query.isBlank()) {
                queryBuilder.withQuery(q -> q.matchAll(m -> m));
            } else {
                queryBuilder.withQuery(q -> q.multiMatch(m -> m
                        .query(query.trim())
                        .fields("name^3", "categoryName^2", "description")
                        .fuzziness("AUTO")));
            }
            for (Map.Entry<String, Boolean> sortField : sortFields) {
                SortOrder sortOrder;
                if (sortField.getValue()) {
                    sortOrder = SortOrder.Asc;
                } else {
                    sortOrder = SortOrder.Desc;
                }
                queryBuilder.withSort(s -> s.field(f -> f
                        .field(sortField.getKey())
                        .order(sortOrder)));
            }

            SearchHits<ProductDocument> hits = elasticsearchOperations.search(queryBuilder.build(), ProductDocument.class);

            List<Long> ids = new ArrayList<>();
            for (SearchHit<ProductDocument> hit : hits.getSearchHits()) {
                ids.add(Long.valueOf(hit.getContent().getId()));
            }

            Map<Long, Product> productsById = new HashMap<>();
            for (Product product : productRepo.findAllById(ids)) {
                productsById.put(product.getId(), product);
            }

            List<Product> orderedProducts = new ArrayList<>();
            for (Long id : ids) {
                Product product = productsById.get(id);
                if (product != null) {
                    orderedProducts.add(product);
                }
            }
            return new PageImpl<>(orderedProducts, pageable, hits.getTotalHits());
        } catch (Exception exception) {
            log.warn("Elasticsearch search failed, falling back to SQL search: {}", exception.getMessage());
            return jpaSearchService.searchProducts(query, pageSize, pageNumber, sortParamList);
        }
    }
}
