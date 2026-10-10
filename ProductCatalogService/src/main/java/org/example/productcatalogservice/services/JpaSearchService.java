package org.example.productcatalogservice.services;

import org.example.productcatalogservice.dtos.SortParam;
import org.example.productcatalogservice.models.Product;
import org.example.productcatalogservice.repos.ProductRepo;
import org.example.productcatalogservice.utils.SortParamUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

// Used when Elasticsearch is unavailable.
@Service
public class JpaSearchService implements ISearchService {

    @Autowired
    private ProductRepo productRepo;

    @Override
    public Page<Product> searchProducts(String query,
                                        Integer pageSize,
                                        Integer pageNumber,
                                        List<SortParam> sortParamList) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize, SortParamUtil.toJpaSort(sortParamList));
        if (query == null || query.isBlank()) {
            return productRepo.findAll(pageable);
        }
        return productRepo.searchByKeyword(query.trim(), pageable);
    }
}
