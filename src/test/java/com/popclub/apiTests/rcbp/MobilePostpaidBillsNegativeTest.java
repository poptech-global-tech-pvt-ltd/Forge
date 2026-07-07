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

import java.util.Collections;

/**
 * Negative tests for the Mobile Postpaid bill payment flow.
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
public class MobilePostpaidBillsNegativeTest extends RcbpBaseService {

    private static final Logger log = LoggerFactory.getLogger(MobilePostpaidBillsNegativeTest.class);

    // Valid biller used in the postpaid collection
    private static final String VALID_BILLER_ID   = "VODA00000NAT96";
    private static final String INVALID_BILLER_ID = "INVALID_BILLER_XYZ";

    @BeforeClass
    public void setup() {
        // no service instances needed — specs are built directly via base methods
    }

    // ── Invalid input ─────────────────────────────────────────────────────────

    /**
     * GET /catalogue/billers/{billerId}?category=mobilepostpaid
     * Invalid input: biller ID that does not exist.
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] GET /catalogue/billers/{billerId} — invalid biller ID returns 4xx",
          groups = "rcbp-mobile-postpaid-negative")
    public void getMobilePostpaidInputFields_invalidBillerId_returns4xx() {
        log.info("Running: getMobilePostpaidInputFields_invalidBillerId_returns4xx");

        Response response = buildSpecWithUserId()
                .queryParam("category", "mobilepostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INVALID_BILLER_ID);

        RcbpBaseService.assertClientError(response,
                "GET /catalogue/billers/" + INVALID_BILLER_ID + "?category=mobilepostpaid");
    }

    /**
     * POST /bills/fetch — billerId is a non-existent value.
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] POST /bills/fetch — invalid billerId returns 4xx",
          groups = "rcbp-mobile-postpaid-negative")
    public void fetchMobilePostpaidBill_invalidBillerId_returns4xx() {
        log.info("Running: fetchMobilePostpaidBill_invalidBillerId_returns4xx");

        String phone = RcbpConfigManager.getPhone();
        FetchBillRequestDto request = FetchBillRequestDto.forMobilePostpaid(
                INVALID_BILLER_ID,
                Collections.singletonList(
                        new FetchBillRequestDto.CustomerParam("Mobile Number", phone))
        );

        Response response = buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /bills/fetch [invalid billerId=" + INVALID_BILLER_ID + "]");
    }

    // ── Missing required fields ───────────────────────────────────────────────

    /**
     * POST /bills/fetch — customerParams is an empty array (required field missing).
     * Expects: 4xx, is_success=false, validation error present, data absent.
     */
    @Test(description = "[Negative] POST /bills/fetch — empty customerParams returns 4xx",
          groups = "rcbp-mobile-postpaid-negative")
    public void fetchMobilePostpaidBill_emptyCustomerParams_returns4xx() {
        log.info("Running: fetchMobilePostpaidBill_emptyCustomerParams_returns4xx");

        FetchBillRequestDto request = FetchBillRequestDto.forMobilePostpaid(
                VALID_BILLER_ID,
                Collections.emptyList()
        );

        Response response = buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.BILLS_FETCH.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /bills/fetch [empty customerParams]");
    }

    /**
     * POST /payment/initiate — referenceId and billId are -1 (invalid/missing required values).
     * Expects: 4xx, is_success=false, error/message present, data absent.
     */
    @Test(description = "[Negative] POST /payment/initiate — invalid referenceId/billId returns 4xx",
          groups = "rcbp-mobile-postpaid-negative")
    public void initiatePostpaidPayment_invalidIds_returns4xx() {
        log.info("Running: initiatePostpaidPayment_invalidIds_returns4xx");

        InitiatePaymentRequestDto request =
                InitiatePaymentRequestDto.forMobilePostpaid(-1L, -1L, 0.0);

        Response response = buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());

        RcbpBaseService.assertClientError(response,
                "POST /payment/initiate [referenceId=-1, billId=-1]");
    }

    // ── Invalid authentication ────────────────────────────────────────────────

    /**
     * GET /catalogue/billers?category=mobilepostpaid with a bogus X-Userid header.
     * Expects: 401 Unauthorized, no business data in response.
     */
    @Test(description = "[Negative] GET /catalogue/billers — invalid X-Userid returns 401",
          groups = "rcbp-mobile-postpaid-negative")
    public void getMobilePostpaidBillers_invalidAuth_returns401() {
        log.info("Running: getMobilePostpaidBillers_invalidAuth_returns401");

        Response response = buildSpecWithInvalidAuth()
                .queryParam("category", "mobilepostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());

        RcbpBaseService.assertUnauthorized(response,
                "GET /catalogue/billers?category=mobilepostpaid [invalid auth]");
    }
}
