package com.wally.customersupport.application.port.out;

import java.util.List;

import com.wally.customersupport.domain.model.CatalogProduct;
import com.wally.customersupport.domain.model.CatalogQuery;

public interface CatalogRepository {

    List<CatalogProduct> search(CatalogQuery query);
}
