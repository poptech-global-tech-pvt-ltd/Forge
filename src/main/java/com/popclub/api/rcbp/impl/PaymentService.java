package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.dto.ConfirmPaymentRequestDto;
import com.popclub.api.rcbp.dto.InitiatePaymentRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

/**
 * Service wrapper for payment initiate and confirm endpoints.
 *
 * Initiate — POST /v2/payment/initiate:
 *   • Mobile postpaid  → rcbp.api.base.url + X-Userid: 019cfef7 (hardcoded in collection)
 *   • Mobile prepaid   → rcbp.base.url     + X-Userid: {{user_id}}
 *   • Credit card      → rcbp.base.url     + X-Userid: 019a97c6 (hardcoded in collection)
 *
 * Confirm — POST /v3/payment/confirm (postpaid, prepaid):
 *   • Mobile postpaid  → rcbp.api.base.url + X-Userid: 019cfef7
 *   • Mobile prepaid   → rcbp.api.base.url + X-Userid: 019cfef7
 *
 * Confirm — POST /v3/payment/confirmation (credit card):
 *   • Credit card      → rcbp.base.url     + X-Userid: 019a97c6
 *
 * NOTE: The UPI/CC payment mock step (Postman mock server) is NOT executed —
 * it is a Postman-controlled mock, not a real API. The paymentReferenceId
 * is generated in the test using "pay_" + System.currentTimeMillis().
 */
public class PaymentService extends RcbpBaseService {

    // ── Initiate ──────────────────────────────────────────────────────────────

    /**
     * POST /v2/payment/initiate — mobile postpaid.
     * Uses rcbp.api.base.url + X-Userid: 019cfef7 (hardcoded in collection).
     *
     * Postman assertions:
     *   - is_success = true
     *   - data.orderNumber exists
     *   - data.gatewayOrderId exists
     *   - data.orderNumber matches /^BP/
     */
    public Response initiatePostpaidPayment(InitiatePaymentRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    /**
     * POST /v2/payment/initiate — mobile prepaid.
     * Uses rcbp.base.url + X-Userid: {{user_id}}.
     *
     * Postman assertions:
     *   - is_success = true
     *   - message = "payment initiated successfully!"
     *   - data.orderNumber matches /^RC/
     *   - data.orderNumber and data.gatewayOrderId are non-empty strings
     */
    public Response initiatePrepaidPayment(InitiatePaymentRequestDto request) {
        return buildSpecWithUserId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    /**
     * POST /v2/payment/initiate — credit card.
     * Uses rcbp.base.url + X-Userid: 019a97c6 (hardcoded in collection).
     *
     * Postman assertions:
     *   - is_success = true
     *   - message = "payment initiated successfully!"
     *   - data.orderNumber matches /^BP/
     *   - data.orderNumber and data.gatewayOrderId are non-empty strings
     *   - no error property
     */
    public Response initiateCreditCardPayment(InitiatePaymentRequestDto request) {
        return buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    /**
     * POST /v3/payment/confirm — mobile postpaid.
     * Uses rcbp.api.base.url + X-Userid: 019cfef7.
     *
     * Postman assertions:
     *   - is_success = true
     *   - data.status exists
     *   - data.transactionId exists
     *   - data.status = "Processing"
     */
    public Response confirmPostpaidPayment(ConfirmPaymentRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_CONFIRM.getPath());
    }

    /**
     * POST /v3/payment/confirm — mobile prepaid.
     * Uses rcbp.api.base.url + X-Userid: 019cfef7.
     *
     * Postman assertions:
     *   - is_success = true
     *   - data has: status, transactionId, refId, paymentDetails
     *   - paymentDetails has: amount, mode, paymentRefId, timestamp
     *   - data.status != "Failed"
     *   - data.status = "Processing"
     *   - transactionId is non-empty string
     *   - paymentDetails.amount = bill_amount
     *   - paymentDetails.timestamp is valid date string
     */
    public Response confirmPrepaidPayment(ConfirmPaymentRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_CONFIRM.getPath());
    }

    /**
     * POST /v3/payment/confirmation — credit card.
     * Uses rcbp.base.url + X-Userid: 019a97c6.
     *
     * NOTE: The CC confirm request in the collection has no test script.
     * Only status 200 is asserted.
     *
     * The collection uses hardcoded orderNumber and paymentReferenceId (developer
     * test artifacts). In automated tests these come from the initiate step.
     */
    public Response confirmCreditCardPayment(ConfirmPaymentRequestDto request) {
        return buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_CONFIRMATION.getPath());
    }
}
