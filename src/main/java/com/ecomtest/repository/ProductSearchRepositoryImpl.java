package com.ecomtest.repository;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ProductSearchRepositoryImpl implements ProductSearchRepository {

    private final EntityManager entityManager;

    public ProductSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<ProductSearchRow> search(List<String> tokens, ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        List<String> filters = new ArrayList<>();
        if (status != null) {
            filters.add("p.status = :status");
        }
        if (minPrice != null) {
            filters.add("p.price >= :minPrice");
        }
        if (maxPrice != null) {
            filters.add("p.price <= :maxPrice");
        }

        List<String> nameConditions = new ArrayList<>();
        List<String> skuConditions = new ArrayList<>();
        List<String> tokenConditions = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String nameCondition = "LOWER(p.name) LIKE CONCAT(LOWER(:token" + i + "), '%')";
            String skuCondition = "LOWER(p.sku) LIKE CONCAT(LOWER(:token" + i + "), '%')";
            nameConditions.add(nameCondition);
            skuConditions.add(skuCondition);
            tokenConditions.add("(" + nameCondition + " OR " + skuCondition + ")");
        }

        List<String> whereParts = new ArrayList<>(filters);
        whereParts.addAll(tokenConditions);
        String whereClause = whereParts.isEmpty() ? "" : " WHERE " + String.join(" AND ", whereParts);

        String nameMatchExpression;
        String skuMatchExpression;
        String orderBy;
        if (tokens.isEmpty()) {
            nameMatchExpression = "0";
            skuMatchExpression = "0";
            orderBy = "p.id ASC";
        } else {
            nameMatchExpression = "CASE WHEN (" + String.join(" OR ", nameConditions) + ") THEN 1 ELSE 0 END";
            skuMatchExpression = "CASE WHEN (" + String.join(" OR ", skuConditions) + ") THEN 1 ELSE 0 END";
            orderBy = "(CASE WHEN (" + String.join(" OR ", nameConditions) + ") THEN 2 ELSE 0 END"
                    + " + CASE WHEN (" + String.join(" OR ", skuConditions) + ") THEN 1 ELSE 0 END) DESC, p.id ASC";
        }

        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(p) FROM Product p" + whereClause, Long.class);
        applyParameters(countQuery, tokens, status, minPrice, maxPrice);
        long total = countQuery.getSingleResult();

        TypedQuery<Object[]> dataQuery = entityManager.createQuery(
                "SELECT p, " + nameMatchExpression + ", " + skuMatchExpression
                        + " FROM Product p" + whereClause + " ORDER BY " + orderBy,
                Object[].class);
        applyParameters(dataQuery, tokens, status, minPrice, maxPrice);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<ProductSearchRow> content = new ArrayList<>();
        for (Object[] row : dataQuery.getResultList()) {
            Product product = (Product) row[0];
            boolean nameMatched = ((Number) row[1]).intValue() == 1;
            boolean skuMatched = ((Number) row[2]).intValue() == 1;
            content.add(new ProductSearchRow(product, nameMatched, skuMatched));
        }

        return new PageImpl<>(content, pageable, total);
    }

    private void applyParameters(Query query, List<String> tokens, ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice) {
        if (status != null) {
            query.setParameter("status", status);
        }
        if (minPrice != null) {
            query.setParameter("minPrice", minPrice);
        }
        if (maxPrice != null) {
            query.setParameter("maxPrice", maxPrice);
        }
        for (int i = 0; i < tokens.size(); i++) {
            query.setParameter("token" + i, tokens.get(i));
        }
    }
}
