package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.catalog.infrastructure.repository.postgres.CatalogProductJpaEntity;
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
              and (:name = '' or lower(product.name) like concat('%', :name, '%'))
              and (:sku = '' or lower(variant.sku) = :sku)
              and (:sizeLabel = '' or lower(variant.sizeLabel) = :sizeLabel)
              and (:color = '' or lower(variant.color) = :color)
            order by product.name
            """)
    List<CatalogProductJpaEntity> search(
            @Param("name") String name,
            @Param("sku") String sku,
            @Param("sizeLabel") String sizeLabel,
            @Param("color") String color);
}
