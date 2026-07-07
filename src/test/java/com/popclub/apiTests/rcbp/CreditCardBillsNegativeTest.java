package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.dto.InitiatePaymentRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Negative tests for the Credit Card bill payment flow.
 *
 * All tests are independent (no dependsOnMethods).
 * Assertions follow Option A:
 *   - Invalid input / missing required fields → 4xx status, is_success=false,
 *     message or error present, data absent
 *   - Invalid auth                            → 401, data absent
 *
 * NOTE: Exact status codes (e.g. 400 vs 422) and error message text are not
 * asserted — the API's error contract is not documented in the collection.
 */
public class CreditCardBillsNegativeTest extends RcbpBaseService {

    private static final Logger log = LoggerFactory.getLogger(CreditCardBillsNegativeTest.class);

    // Valid biller used in the CC collection — needed for invalid-param tests
    private static final String VALID_BILLER_ID = "YESB00000NAT8U";

    // Placeholder for a biller that does not exist
    private static final String INVALID_BILLER_ID = "INVALID_BILLER_XYZ";

    // Customer params hardcoded in collection — used to construct partial/invalid bodies
    private static final String CC_MOBILE = "9936122907";

    @BeforeClass
    public void setup() {
        // no service instances needed — specs are built directly via base methods
    }

    // ── Invalid input ─────────────────────────────────────────────────────────

    /**
     * GET /catalogue/billers/{billerId}?category=creditcard
     * Invalid input: biller ID that does not exist in the system.
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] GET /catalogue/billers/{billerId} — invalid biller ID returns 4xx",
          groups = "rcbp-credit-card-negative")
    public void getCreditCardBillerInputFields_invalidBillerId_returns4xx() {
        log.info("Running: getCreditCardBillerInputFields_invalidBillerId_returns4xx");

        Response response = buildSpecWithCcInputId()
                .queryParam("category", "creditcard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INVALID_BILLER_ID);

        RcbpBaseService.assertClientError(response,
                "GET /catalogue/billers/" + INVALID_BILLER_ID + "?category=creditcard");
    }

    /**
     * POST /bills/fetch — invalid input in customerParams.
     * Last 4 digits value is non-numeric ("ABCD"), which should fail input validation.
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] POST /bills/fetch — non-numeric last 4 digits returns 4xx",
          groups = "rcbp-credit-card-negative")
    public void fetchCreditCardBill_invalidCustomerParam_returns4xx() {
        log.info("Running: fetchCreditCardBill_invalidCustomerParam_returns4xx");

        FetchBillRequestDto request = FetchBillRequestDto.forCreditCard(
                VALID_BILLER_ID,
                Arrays.asList(
                        new FetchBillRequestDto.CustomerParam("Registered Mobile Number", CC_MOBILE),
                        new FetchBillRequestDto.CustomerParam(
                                "Last 4 digits of Primary Credit Card Number", "ABCD")
                )
        );

        Response response = buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /bills/fetch [invalid last-4-digits=ABCD]");
    }

    // ── Missing required fields ───────────────────────────────────────────────

    /**
     * POST /bills/fetch — customerParams is an empty array (required field missing).
     * Expects: 4xx, is_success=false, validation error present, data absent.
     */
    @Test(description = "[Negative] POST /bills/fetch — empty customerParams returns 4xx",
          groups = "rcbp-credit-card-negative")
    public void fetchCreditCardBill_emptyCustomerParams_returns4xx() {
        log.info("Running: fetchCreditCardBill_emptyCustomerParams_returns4xx");

        FetchBillRequestDto request = FetchBillRequestDto.forCreditCard(
                VALID_BILLER_ID,
                Collections.emptyList()
        );

        Response response = buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /bills/fetch [empty customerParams]");
    }

    /**
     * POST /payment/initiate — referenceId and billId are -1 (required fields invalid/absent).
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] POST /payment/initiate — invalid referenceId/billId returns 4xx",
          groups = "rcbp-credit-card-negative")
    public void initiateCreditCardPayment_invalidIds_returns4xx() {
        log.info("Running: initiateCreditCardPayment_invalidIds_returns4xx");

        InitiatePaymentRequestDto request =
                InitiatePaymentRequestDto.forCreditCard(-1L, -1L, 0.0, 0);

        Response response = buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /payment/initiate [referenceId=-1, billId=-1]");
    }

    // ── Invalid authentication ────────────────────────────────────────────────

    /**
     * GET /catalogue/billers?category=creditcard with a bogus X-Userid header.
     * Expects: 401 Unauthorized, no business data in response.
     */
    @Test(description = "[Negative] GET /catalogue/billers — invalid X-Userid returns 401",
          groups = "rcbp-credit-card-negative")
    public void getCreditCardBillers_invalidAuth_returns401() {
        log.info("Running: getCreditCardBillers_invalidAuth_returns401");

        Response response = buildSpecWithInvalidAuth()
                .queryParam("category", "creditcard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());

        RcbpBaseService.assertUnauthorized(response,
                "GET /catalogue/billers?category=creditcard [invalid auth]");
    }
}
