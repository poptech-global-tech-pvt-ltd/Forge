package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.dto.InitiatePaymentRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import com.popclub.api.rcbp.impl.RcbpConfigManager;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Negative tests for the Mobile Prepaid recharge and payment flow.
 *
 * All tests are independent (no dependsOnMethods).
 * Assertions follow Option A:
 *   - Invalid input / missing required fields → 4xx status, is_success=false,
 *     message or error present, data absent
 *   - Invalid auth                            → 401, data absent
 *
 * NOTE: Exact status codes and error message text are not asserted —
 * the API's error contract is not documented in the collection.
 */
public class PrepaidRechargeNegativeTest extends RcbpBaseService {

    private static final Logger log = LoggerFactory.getLogger(PrepaidRechargeNegativeTest.class);

    // Clearly invalid values
    private static final String INVALID_PHONE         = "0000000000";
    private static final String INVALID_OPERATOR_NAME = "INVALID_OPERATOR_XYZ";
    private static final long   INVALID_PLAN_ID       = -1L;

    @BeforeClass
    public void setup() {
        // no service instances needed — specs are built directly via base methods
    }

    // ── Invalid input ─────────────────────────────────────────────────────────

    /**
     * GET /recharge/fetch/plans — phone number is all zeros (invalid).
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] GET /recharge/fetch/plans — invalid phone number returns 4xx",
          groups = "rcbp-recharge-negative")
    public void fetchPlans_invalidPhone_returns4xx() {
        log.info("Running: fetchPlans_invalidPhone_returns4xx");

        Response response = buildSpecWithOperatorId()
                .queryParam("phone_number", INVALID_PHONE)
                .queryParam("name",         RcbpConfigManager.getOperatorName())
                .get(RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());

        RcbpBaseService.assertClientError(response,
                "GET /recharge/fetch/plans [phone_number=" + INVALID_PHONE + "]");
    }

    /**
     * GET /recharge/fetch/operator-circle — phone number is all zeros (invalid).
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] GET /recharge/fetch/operator-circle — invalid phone number returns 4xx",
          groups = "rcbp-recharge-negative")
    public void fetchOperatorCircle_invalidPhone_returns4xx() {
        log.info("Running: fetchOperatorCircle_invalidPhone_returns4xx");

        Response response = buildSpecWithOperatorId()
                .queryParam("phone_number", INVALID_PHONE)
                .get(RcbpRoutes.RECHARGE_FETCH_OPERATOR_CIRCLE.getPath());

        RcbpBaseService.assertClientError(response,
                "GET /recharge/fetch/operator-circle [phone_number=" + INVALID_PHONE + "]");
    }

    // ── Missing required fields ───────────────────────────────────────────────

    /**
     * POST /bills/fetch — planId is -1 (invalid/non-existent plan).
     * Expects: 4xx, is_success=false, validation error present, data absent.
     */
    @Test(description = "[Negative] POST /bills/fetch mobileprepaid — invalid planId returns 4xx",
          groups = "rcbp-recharge-negative")
    public void fetchPrepaidBill_invalidPlanId_returns4xx() {
        log.info("Running: fetchPrepaidBill_invalidPlanId_returns4xx");

        FetchBillRequestDto request = FetchBillRequestDto.forMobilePrepaid(INVALID_PLAN_ID);

        Response response = buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /bills/fetch [planId=" + INVALID_PLAN_ID + "]");
    }

    /**
     * POST /payment/initiate — planId and referenceId are -1 (invalid/non-existent).
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] POST /payment/initiate mobileprepaid — invalid planId returns 4xx",
          groups = "rcbp-recharge-negative")
    public void initiatePrepaidPayment_invalidPlanId_returns4xx() {
        log.info("Running: initiatePrepaidPayment_invalidPlanId_returns4xx");

        InitiatePaymentRequestDto request =
                InitiatePaymentRequestDto.forMobilePrepaid(INVALID_PLAN_ID, 0.0);

        Response response = buildSpecWithUserId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /payment/initiate [planId=" + INVALID_PLAN_ID + "]");
    }

    // ── Invalid authentication ────────────────────────────────────────────────

    /**
     * GET /recharge/fetch/plans with a bogus X-Userid header.
     * Expects: 401 Unauthorized, no business data in response.
     */
    @Test(description = "[Negative] GET /recharge/fetch/plans — invalid X-Userid returns 401",
          groups = "rcbp-recharge-negative")
    public void fetchPlans_invalidAuth_returns401() {
        log.info("Running: fetchPlans_invalidAuth_returns401");

        Response response = buildSpecWithInvalidAuth()
                .queryParam("phone_number", RcbpConfigManager.getPhone())
                .queryParam("name",         RcbpConfigManager.getOperatorName())
                .get(RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());

        RcbpBaseService.assertUnauthorized(response,
                "GET /recharge/fetch/plans [invalid auth]");
    }
}
