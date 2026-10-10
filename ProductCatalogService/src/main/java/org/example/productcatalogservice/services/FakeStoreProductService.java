package org.example.productcatalogservice.services;

import org.example.productcatalogservice.dtos.FakeStoreProductDto;
import org.example.productcatalogservice.models.Category;
import org.example.productcatalogservice.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

// Inject with @Qualifier("fkps"); StorageProductService (MySQL) is the default.
@Service("fkps")
public class FakeStoreProductService implements IProductService {

    private static final String FAKE_STORE_URL = "https://fakestoreapi.com/products";

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @Override
    public Product getProductById(Long id) {
        FakeStoreProductDto fakeStoreProductDto = null;
        try {
            fakeStoreProductDto = (FakeStoreProductDto) redisTemplate.opsForHash().get("FAKESTORE_PRODUCTS", String.valueOf(id));
        } catch (Exception exception) {
            // cache is optional
        }

       if(fakeStoreProductDto == null) {
         fakeStoreProductDto =
                   requestForEntity(FAKE_STORE_URL + "/{id}", null, HttpMethod.GET,
                           FakeStoreProductDto.class, id).getBody();

           if (fakeStoreProductDto != null) {
               try {
                   redisTemplate.opsForHash().put("FAKESTORE_PRODUCTS", String.valueOf(id), fakeStoreProductDto);
               } catch (Exception exception) {
                   // cache is optional
               }
               return from(fakeStoreProductDto);
           }
       } else {
           return from(fakeStoreProductDto);
       }

       return null;
    }

    @Override
    public Product replaceProduct(Long id, Product input) {
        FakeStoreProductDto fakeStoreProductDto = from(input);
       ResponseEntity<FakeStoreProductDto> fakeStoreProductDtoResponseEntity = requestForEntity(
               FAKE_STORE_URL + "/{id}", fakeStoreProductDto, HttpMethod.PUT,
               FakeStoreProductDto.class, id);

       return from(fakeStoreProductDtoResponseEntity.getBody());
    }

    @Override
    public void deleteProduct(Long id) {
        requestForEntity(FAKE_STORE_URL + "/{id}", null, HttpMethod.DELETE, FakeStoreProductDto.class, id);
    }

    private <T> ResponseEntity<T> requestForEntity(String url, @Nullable Object request, HttpMethod httpMethod,
                                                   Class<T> responseType, Object... uriVariables) throws RestClientException {
        RestTemplate restTemplate1 = restTemplateBuilder.build();
        RequestCallback requestCallback = restTemplate1.httpEntityCallback(request, responseType);
        ResponseExtractor<ResponseEntity<T>> responseExtractor = restTemplate1.responseEntityExtractor(responseType);
        return restTemplate1.execute(url, httpMethod, requestCallback, responseExtractor, uriVariables);
    }

    @Override
    public List<Product> getAllProducts() {
        FakeStoreProductDto[] fakeStoreProductDtos = restTemplateBuilder.build()
                .getForEntity(FAKE_STORE_URL, FakeStoreProductDto[].class).getBody();
        List<Product> products = new ArrayList<>();
        if (fakeStoreProductDtos != null) {
            for (FakeStoreProductDto fakeStoreProductDto : fakeStoreProductDtos) {
                products.add(from(fakeStoreProductDto));
            }
        }
        return products;
    }

    @Override
    public Product createProduct(Product product) {
        FakeStoreProductDto fakeStoreProductDto = requestForEntity(FAKE_STORE_URL, from(product), HttpMethod.POST,
                FakeStoreProductDto.class).getBody();
        if (fakeStoreProductDto != null) {
            return from(fakeStoreProductDto);
        }
        return null;
    }

    private Product from(FakeStoreProductDto fakeStoreProductDto) {
        Product product = new Product();
        product.setId(fakeStoreProductDto.getId());
        product.setName(fakeStoreProductDto.getTitle());
        product.setDescription(fakeStoreProductDto.getDescription());
        product.setPrice(fakeStoreProductDto.getPrice());
        product.setImageUrl(fakeStoreProductDto.getImage());
        Category category = new Category();
        category.setName(fakeStoreProductDto.getCategory());
        product.setCategory(category);
        return product;
    }

    private FakeStoreProductDto from(Product product) {
        FakeStoreProductDto fakeStoreProductDto = new FakeStoreProductDto();
        fakeStoreProductDto.setId(product.getId());
        fakeStoreProductDto.setTitle(product.getName());
        fakeStoreProductDto.setPrice(product.getPrice());
        fakeStoreProductDto.setDescription(product.getDescription());
        fakeStoreProductDto.setImage(product.getImageUrl());
        if(product.getCategory() != null) {
            fakeStoreProductDto.setCategory(product.getCategory().getName());
        }
        return fakeStoreProductDto;
    }
}
