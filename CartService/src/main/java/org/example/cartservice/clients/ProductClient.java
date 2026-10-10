package org.example.cartservice.clients;

import org.example.cartservice.dtos.ProductDto;
import org.example.cartservice.exceptions.ProductNotFoundException;
import org.example.cartservice.exceptions.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ProductClient {

    private static final String PRODUCT_URL = "http://productservice/products/{id}";

    @Autowired
    private RestTemplate restTemplate;

    public ProductDto getProduct(Long productId) {
        try {
            ProductDto productDto = restTemplate.getForEntity(PRODUCT_URL, ProductDto.class, productId).getBody();
            if (productDto == null) {
                throw new ProductNotFoundException("Product " + productId + " not found");
            }
            return productDto;
        } catch (HttpClientErrorException exception) {
            throw new ProductNotFoundException("Product " + productId + " not found");
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("Product service is unavailable");
        }
    }
}
