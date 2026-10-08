package com.ecomtest.repository;

import com.ecomtest.dto.ProductFilter;
import com.ecomtest.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Product> search(String term, ProductFilter filter, Pageable pageable) {
        String scoreExpr = "("
                + "(CASE WHEN LOWER(name) = LOWER(:term) THEN 100 "
                + "WHEN LOWER(name) LIKE LOWER(:prefix) THEN 80 "
                + "WHEN LOWER(name) LIKE LOWER(:contains) THEN 60 ELSE 0 END) * 3"
                + " + "
                + "(CASE WHEN LOWER(sku) = LOWER(:term) THEN 100 "
                + "WHEN LOWER(sku) LIKE LOWER(:prefix) THEN 80 "
                + "WHEN LOWER(sku) LIKE LOWER(:contains) THEN 60 ELSE 0 END) * 3"
                + " + "
                + "(CASE WHEN LOWER(description) = LOWER(:term) THEN 100 "
                + "WHEN LOWER(description) LIKE LOWER(:prefix) THEN 80 "
                + "WHEN LOWER(description) LIKE LOWER(:contains) THEN 60 ELSE 0 END)"
                + ")";

        StringBuilder where = new StringBuilder();
        where.append(scoreExpr).append(" > 0");

        if (filter != null) {
            if (filter.getStatus() != null) {
                where.append(" AND status = :status");
            }
            if (filter.getMinPrice() != null) {
                where.append(" AND price >= :minPrice");
            }
            if (filter.getMaxPrice() != null) {
                where.append(" AND price <= :maxPrice");
            }
        }

        String baseSql = "SELECT * FROM products WHERE " + where;
        String countSql = "SELECT COUNT(*) FROM products WHERE " + where;
        String orderedSql = baseSql + " ORDER BY " + scoreExpr + " DESC";

        Query dataQuery = entityManager.createNativeQuery(orderedSql, Product.class);
        Query countQuery = entityManager.createNativeQuery(countSql);

        bindParameters(dataQuery, term, filter);
        bindParameters(countQuery, term, filter);

        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Product> results = dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }

    private void bindParameters(Query query, String term, ProductFilter filter) {
        query.setParameter("term", term);
        query.setParameter("prefix", term + "%");
        query.setParameter("contains", "%" + term + "%");
        if (filter != null) {
            if (filter.getStatus() != null) {
                query.setParameter("status", filter.getStatus().name());
            }
            if (filter.getMinPrice() != null) {
                query.setParameter("minPrice", filter.getMinPrice());
            }
            if (filter.getMaxPrice() != null) {
                query.setParameter("maxPrice", filter.getMaxPrice());
            }
        }
    }
}
