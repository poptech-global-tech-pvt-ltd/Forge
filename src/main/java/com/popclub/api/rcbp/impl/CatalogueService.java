package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

public class CatalogueService extends RcbpBaseService {

    public Response getCreditCardBillers() {
        return buildSpecWithUserId()
                .queryParam("category", "creditcard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());
    }

    public Response getCreditCardBillerInputFields(String billerId) {
        return buildSpecWithCcInputId()
                .queryParam("category", "creditcard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + billerId);
    }

    public Response getMobilePostpaidBillers() {
        return buildSpecWithUserId()
                .queryParam("category", "mobilepostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());
    }

    public Response getMobilePostpaidBillerInputFields(String billerId) {
        return buildSpecWithUserId()
                .queryParam("category", "mobilepostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + billerId);
    }
}
