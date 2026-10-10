package org.example.productcatalogservice.utils;

import org.example.productcatalogservice.dtos.SortOrder;
import org.example.productcatalogservice.dtos.SortParam;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SortParamUtil {

    private static final Map<String, String> JPA_FIELDS = Map.of("price", "price", "name", "name");
    private static final Map<String, String> ELASTICSEARCH_FIELDS = Map.of("price", "price", "name", "name.keyword");

    public static Sort toJpaSort(List<SortParam> sortParamList) {
        List<Sort.Order> orders = new ArrayList<>();
        for (SortParam sortParam : safe(sortParamList)) {
            String field = resolve(JPA_FIELDS, sortParam);
            Sort.Order order;
            if (sortParam.getSortOrder() == SortOrder.DESC) {
                order = Sort.Order.desc(field);
            } else {
                order = Sort.Order.asc(field);
            }
            orders.add(order);
        }
        return Sort.by(orders);
    }

    // Each entry is an Elasticsearch field name and whether to sort ascending.
    public static List<Map.Entry<String, Boolean>> toElasticsearchSort(List<SortParam> sortParamList) {
        List<Map.Entry<String, Boolean>> fields = new ArrayList<>();
        for (SortParam sortParam : safe(sortParamList)) {
            String field = resolve(ELASTICSEARCH_FIELDS, sortParam);
            boolean ascending = sortParam.getSortOrder() != SortOrder.DESC;
            fields.add(Map.entry(field, ascending));
        }
        return fields;
    }

    private static String resolve(Map<String, String> fields, SortParam sortParam) {
        String paramName = sortParam.getParamName() == null ? "" : sortParam.getParamName().trim().toLowerCase();
        String field = fields.get(paramName);
        if (field == null) {
            throw new IllegalArgumentException("Cannot sort by '" + sortParam.getParamName() + "', use one of: price, name");
        }
        return field;
    }

    private static List<SortParam> safe(List<SortParam> sortParamList) {
        return sortParamList == null ? List.of() : sortParamList;
    }
}
