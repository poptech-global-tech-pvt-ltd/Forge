package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import com.popclub.api.rcbp.impl.RechargeService;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

/**
 * Integration tests for the prepaid recharge flow.
 *
 * Flow:
 *   1. GET fetch/operators/states  - load operators & states
 *   2. GET fetch/operator-circle   - detect operator + circle from phone number
 *   3. GET fetch/plans             - fetch plans using detected operator + circle
 *
 * operator_id and state_id are extracted from step 2 and passed into step 3.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ NOT AVAILABLE IN COLLECTION — tests deliberately omitted:               │
 * │  • name query param on fetch/plans (disabled in collection)             │
 * │  • Response body schema / field assertions (no saved responses)         │
 * │  • Error / negative scenarios                                           │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
public class RechargeTest {

    private static final Logger log = LoggerFactory.getLogger(RechargeTest.class);

    private static final String PHONE_NUMBER = "7975209916";

    private RechargeService rechargeService;

    // Extracted from fetchOperatorCircle response, used in fetchPlans
    private String detectedOperatorId;
    private String detectedStateId;

    @BeforeClass
    public void setup() {
        rechargeService = new RechargeService();
    }

    // ── 1. GET fetch/operators/states (stage, no auth) ────────────────────────

    @Test(description = "GET /recharge/fetch/operators/states returns 200",
          groups = "rcbp-recharge")
    public void fetchOperatorsAndStates_returns200() {
        log.info("Running: fetchOperatorsAndStates_returns200");

        Response response = rechargeService.fetchOperatorsAndStates();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_OPERATORS_STATES.getPath());
    }

    @Test(description = "GET /recharge/fetch/operators/states response body is non-empty",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchOperatorsAndStates_returns200")
    public void fetchOperatorsAndStates_bodyNotEmpty() {
        log.info("Running: fetchOperatorsAndStates_bodyNotEmpty");

        Response response = rechargeService.fetchOperatorsAndStates();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_OPERATORS_STATES.getPath());

        assertFalse(response.body().asString().isEmpty(),
                "Operators/states response body must not be empty");
    }

    // ── 2. GET fetch/operator-circle (stage, with auth) ───────────────────────

    @Test(description = "GET /recharge/fetch/operator-circle returns 200 and detects operator + circle",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchOperatorsAndStates_bodyNotEmpty")
    public void fetchOperatorCircle_detectsOperatorAndState() {
        log.info("Running: fetchOperatorCircle_detectsOperatorAndState");

        Response response = rechargeService.fetchOperatorCircle(PHONE_NUMBER);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_OPERATOR_CIRCLE.getPath() + "?phone_number=" + PHONE_NUMBER);

        detectedOperatorId = response.jsonPath().getString("data.current_operator");
        detectedStateId    = response.jsonPath().getString("data.current_location");

        assertNotNull(detectedOperatorId, "current_operator must be present in response");
        assertNotNull(detectedStateId,    "current_location must be present in response");

        log.info("Detected operator: {}, circle: {}", detectedOperatorId, detectedStateId);
    }

    // ── 3. GET fetch/plans using detected operator + circle (prod) ────────────

    @Test(description = "GET /recharge/fetch/plans returns 200 for detected operator and circle",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchOperatorCircle_detectsOperatorAndState")
    public void fetchPlans_forDetectedOperatorAndCircle_returns200() {
        log.info("Running: fetchPlans_forDetectedOperatorAndCircle_returns200 | operator={} circle={}",
                detectedOperatorId, detectedStateId);

        Response response = rechargeService.fetchPlans(PHONE_NUMBER, detectedOperatorId, detectedStateId);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());
    }

    @Test(description = "GET /recharge/fetch/plans response body is non-empty",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchPlans_forDetectedOperatorAndCircle_returns200")
    public void fetchPlans_responseBodyNotEmpty() {
        log.info("Running: fetchPlans_responseBodyNotEmpty");

        Response response = rechargeService.fetchPlans(PHONE_NUMBER, detectedOperatorId, detectedStateId);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());

        assertFalse(response.body().asString().isEmpty(),
                "Plans response body must not be empty");
    }

    /*
     * ── What is NOT tested (not available in collection) ──────────────────────
     *
     * 1. name query param on fetch/plans: disabled in the Postman collection.
     *
     * 2. Error scenarios (invalid phone, missing required params, etc.):
     *    No negative examples in the collection.
     *
     * 3. Response field-level assertions (operator name, plan details, etc.):
     *    No saved responses available.
     */
}
