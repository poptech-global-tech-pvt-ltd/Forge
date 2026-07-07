package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

/**
 * Service wrapper for POST /v2/bills/fetch.
 *
 * Three usages across collections:
 *
 *   fetchCreditCardBill()     → rcbp.base.url     + X-Userid: 019a97c6 (hardcoded in CC collection)
 *   fetchMobilePostpaidBill() → rcbp.api.base.url + X-Userid: 019cfef7 (hardcoded in postpaid collection)
 *   fetchPrepaidBill()        → rcbp.api.base.url + X-Userid: 019cfef7 (hardcoded in prepaid collection)
 */
public class BillsService extends RcbpBaseService {

    /**
     * POST /v2/bills/fetch — credit card.
     * Uses rcbp.base.url + X-Userid: 019a97c6 (hardcoded in CC collection).
     *
     * Postman assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.referenceId is a number
     *   - data.billID is a number
     *   - data.totalDetails.totalAmount exists
     *   - amount > 0
     *   - data.exactness = "Any"
     *   - data.isMinDueAvailable exists
     *   - data.payableAmounts exists
     *   - no error property
     */
    public Response fetchCreditCardBill(FetchBillRequestDto request) {
        return buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }

    /**
     * POST /v2/bills/fetch — mobile postpaid.
     * Uses rcbp.api.base.url + X-Userid: 019cfef7 (hardcoded in postpaid collection).
     *
     * customerParams are built from inputFields extracted in the previous step.
     *
     * Postman assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.referenceId is a number
     *   - data.billID is a number
     *   - data.totalDetails.totalAmount exists
     *   - amount > 0
     */
    public Response fetchMobilePostpaidBill(FetchBillRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }

    /**
     * POST /v2/bills/fetch — mobile prepaid.
     * Uses rcbp.api.base.url + X-Userid: 019cfef7 (hardcoded in prepaid collection).
     *
     * Postman assertions:
     *   - Status 200
     *   - is_success = true
     *   - message = "fetched bill successfully"
     *   - data.billSummary exists
     *   - data.totalDetails.totalAmount exists
     *   - amount > 0
     *   - amount matches plan_amount
     */
    public Response fetchPrepaidBill(FetchBillRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());
    }
}
