package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

public class BillsService extends RcbpBaseService {

    public Response fetchCreditCardBill(FetchBillRequestDto request) {
        return buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }

    public Response fetchMobilePostpaidBill(FetchBillRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }

    public Response fetchPrepaidBill(FetchBillRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }
}
