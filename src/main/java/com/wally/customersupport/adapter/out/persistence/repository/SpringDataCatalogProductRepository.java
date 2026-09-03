package com.wally.customersupport.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.adapter.out.persistence.entity.CatalogProductJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataCatalogProductRepository extends JpaRepository<CatalogProductJpaEntity, UUID> {

    @EntityGraph(attributePaths = "variants")
    @Query("""
            select distinct product
            from CatalogProductJpaEntity product
            join product.variants variant
            where product.active = true
              and variant.active = true
              and (:name is null or lower(product.name) like lower(concat('%', :name, '%')))
              and (:sku is null or lower(variant.sku) = lower(:sku))
              and (:sizeLabel is null or lower(variant.sizeLabel) = lower(:sizeLabel))
              and (:color is null or lower(variant.color) = lower(:color))
            order by product.name
            """)
    List<CatalogProductJpaEntity> search(
            @Param("name") String name,
            @Param("sku") String sku,
            @Param("sizeLabel") String sizeLabel,
            @Param("color") String color);
}
