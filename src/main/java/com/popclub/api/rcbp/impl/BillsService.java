package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

/**
 * Service wrapper for POST /v2/bills/fetch.
 *
 * The collection contains two fetch-bill requests:
 *   1. Credit card bill fetch — X-Userid: {{user_id}}
 *   2. Mobile postpaid bill fetch — X-Userid: {{userid}}
 *
 * NOTE: The recharge "select plan" POST (previously in the old collection) is
 * NOT present in the current Postman collection. It has been removed.
 */
public class BillsService extends RcbpBaseService {

    /**
     * POST /v2/bills/fetch — credit card
     * Postman: "credit card fetch bill" — X-Userid: {{user_id}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - data is not empty
     */
    public Response fetchCreditCardBill(FetchBillRequestDto request) {
        return buildCreditCardSpec()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }

    /**
     * POST /v2/bills/fetch — mobile postpaid
     * Postman: "mobile postpaid fetch bill" — X-Userid: {{userid}}
     *
     * The request body uses values extracted from the input fields response:
     *   billerId           → from data.billerId
     *   customerParams[0]  → name from data.inputFields[0].paramName, value = {{phone}}
     *   customerMobile     → {{phone}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - data is not empty
     */
    public Response fetchMobilePostpaidBill(FetchBillRequestDto request) {
        return buildMobilePostpaidSpec()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }
}
