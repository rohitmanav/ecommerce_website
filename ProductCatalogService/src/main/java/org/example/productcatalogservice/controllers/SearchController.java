package org.example.productcatalogservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.productcatalogservice.clients.AuthClient;
import org.example.productcatalogservice.dtos.MessageResponseDto;
import org.example.productcatalogservice.dtos.ProductDto;
import org.example.productcatalogservice.dtos.SearchRequestDto;
import org.example.productcatalogservice.services.ISearchService;
import org.example.productcatalogservice.services.ProductSearchIndexer;
import org.example.productcatalogservice.utils.mappers.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Search", description = "Keyword search with typo tolerance")
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private ISearchService searchService;

    @Autowired
    private ProductSearchIndexer productSearchIndexer;

    @Autowired
    private AuthClient authClient;

    @Operation(summary = "Search products", description = "Sort by price or name; leave the query empty to list everything")
    @PostMapping
    public Page<ProductDto> searchProducts(@Valid @RequestBody SearchRequestDto searchRequestDto)
    {
       return searchService.searchProducts(searchRequestDto.getQuery(),
               searchRequestDto.getPageSize(),
               searchRequestDto.getPageNumber(),searchRequestDto.getSortParamList())
               .map(ProductMapper::from);
    }

    @Operation(summary = "Search products by keyword")
    @GetMapping
    public Page<ProductDto> searchProductsByKeyword(@RequestParam(name = "q", required = false) String query,
                                                    @RequestParam(defaultValue = "0") Integer pageNumber,
                                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNumber must be >= 0 and pageSize between 1 and 100");
        }
        return searchService.searchProducts(query, pageSize, pageNumber, List.of()).map(ProductMapper::from);
    }

    @Operation(summary = "Rebuild the Elasticsearch index from MySQL (admin only)")
    @PostMapping("/reindex")
    public MessageResponseDto reindex(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authClient.requireAdmin(authorizationHeader);
        int count = productSearchIndexer.reindexAll();
        return new MessageResponseDto("Indexed " + count + " products");
    }
}
