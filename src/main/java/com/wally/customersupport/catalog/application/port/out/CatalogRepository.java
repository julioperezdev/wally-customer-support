package com.wally.customersupport.catalog.application.port.out;

import java.util.List;

import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;

public interface CatalogRepository {

    List<CatalogProduct> search(CatalogQuery query);
}
