package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.dto.ConfirmPaymentRequestDto;
import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.dto.InitiatePaymentRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.BillsService;
import com.popclub.api.rcbp.impl.PaymentService;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import com.popclub.api.rcbp.impl.RcbpConfigManager;
import com.popclub.api.rcbp.impl.RechargeService;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * Integration tests for the mobile prepaid recharge and payment flow.
 *
 * Flow (from updated prepaid Postman collection):
 *   1. GET  /v2/recharge/fetch/plans?phone_number={{phone}}&name={{operator_name}}
 *      Auth: X-Userid: 019cfef7 (hardcoded)
 *      → is_success=true, data.plans non-empty array
 *      → plans[0].planData[0] has planId (number) and amount (number > 0)
 *      → no error property
 *      → extracts plan_id, plan_amount
 *
 *   2. GET  /v2/recharge/fetch/operator-circle?phone_number={{phone}}
 *      Auth: X-Userid: 019cfef7 (hardcoded)
 *      → is_success=true, current_operator non-empty string, current_location non-empty string
 *      → data has phone_number property
 *      → no error property
 *      (independent step — does not chain into later steps)
 *
 *   3. POST /v2/bills/fetch  (rcbp.api.base.url)
 *      Auth: X-Userid: 019cfef7 (hardcoded)
 *      Body: category=mobileprepaid, planId={{plan_id}}
 *      → is_success=true, message="fetched bill successfully"
 *      → data.billSummary exists, data.totalDetails.totalAmount exists, amount>0
 *      → extracts bill_amount
 *
 *   4. POST /v2/payment/initiate
 *      Auth: X-Userid: {{user_id}}
 *      Body: referenceId={{plan_id}}, category=mobileprepaid, planId={{plan_id}},
 *            amount={{plan_amount}}, coinsUsed=0
 *      → is_success=true, message="payment initiated successfully!"
 *      → data.orderNumber matches /^RC/, orderNumber and gatewayOrderId non-empty strings
 *      → no error property
 *      → extracts order_number
 *
 *   5. UPI payment mock — Postman mock server, SKIPPED (not a real API)
 *
 *   6. POST /v3/payment/confirm  (rcbp.api.base.url)
 *      Auth: X-Userid: 019cfef7 (hardcoded)
 *      → is_success=true, data has status/transactionId/refId/paymentDetails
 *      → paymentDetails has amount/mode/paymentRefId/timestamp
 *      → status != "Failed", status = "Processing"
 *      → transactionId is non-empty string
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ NOT AVAILABLE IN COLLECTION — tests deliberately omitted:               │
 * │  • UPI payment mock (Postman mock server — not a real API)              │
 * │  • fetchOperatorsAndStates (removed from new collection)                │
 * │  • Error / negative scenarios                                           │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
public class RechargeTest {

    private static final Logger log = LoggerFactory.getLogger(RechargeTest.class);

    private static final String PHONE_NUMBER   = RcbpConfigManager.getPhone();
    private static final String OPERATOR_NAME  = RcbpConfigManager.getOperatorName();

    // Extracted from fetchPlans — used in billFetch + initiatePayment
    private long   extractedPlanId;
    private double extractedPlanAmount;

    // Extracted from billFetch — used in confirmPayment
    private double extractedBillAmount;

    // Extracted from initiatePayment — used in confirmPayment
    private String extractedOrderNumber;

    private RechargeService rechargeService;
    private BillsService    billsService;
    private PaymentService  paymentService;

    @BeforeClass
    public void setup() {
        rechargeService = new RechargeService();
        billsService    = new BillsService();
        paymentService  = new PaymentService();
    }

    // ── 1. GET fetch/plans ────────────────────────────────────────────────────

    @Test(description = "GET /recharge/fetch/plans returns 200",
          groups = "rcbp-recharge")
    public void fetchPlans_returns200() {
        log.info("Running: fetchPlans_returns200");

        Response response = rechargeService.fetchPlans(PHONE_NUMBER, OPERATOR_NAME);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());
    }

    @Test(description = "GET /recharge/fetch/plans — is_success=true, plans non-empty, planId/amount valid; extracts plan_id and plan_amount",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchPlans_returns200")
    public void fetchPlans_validResponse() {
        log.info("Running: fetchPlans_validResponse");

        Response response = rechargeService.fetchPlans(PHONE_NUMBER, OPERATOR_NAME);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());

        // Postman: pm.expect(jsonData.is_success).to.be.true
        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        // Postman: pm.expect(jsonData.data.plans).to.be.an("array").that.is.not.empty
        List<?> plans = response.jsonPath().getList("data.plans");
        assertNotNull(plans, "data.plans must not be null");
        assertFalse(plans.isEmpty(), "data.plans must not be empty");

        // Postman: firstPlan has planId (number) and amount (number > 0)
        Object planIdVal   = response.jsonPath().get("data.plans[0].planData[0].planId");
        Object amountVal   = response.jsonPath().get("data.plans[0].planData[0].amount");
        assertNotNull(planIdVal, "data.plans[0].planData[0].planId must be present");
        assertNotNull(amountVal, "data.plans[0].planData[0].amount must be present");

        double amount = ((Number) amountVal).doubleValue();
        assertTrue(amount > 0, "plan amount must be greater than 0");

        // Postman: no error property
        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        // Postman: extract first plan_id and plan_amount across all groups
        extractedPlanId     = ((Number) planIdVal).longValue();
        extractedPlanAmount = amount;

        log.info("Extracted plan_id={}, plan_amount={}", extractedPlanId, extractedPlanAmount);
    }

    // ── 2. GET fetch/operator-circle ──────────────────────────────────────────
    //
    // Independent step in the collection — does not chain into the payment flow.

    @Test(description = "GET /recharge/fetch/operator-circle returns 200",
          groups = "rcbp-recharge")
    public void fetchOperatorCircle_returns200() {
        log.info("Running: fetchOperatorCircle_returns200");

        Response response = rechargeService.fetchOperatorCircle(PHONE_NUMBER);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_OPERATOR_CIRCLE.getPath() + "?phone_number=" + PHONE_NUMBER);
    }

    @Test(description = "GET /recharge/fetch/operator-circle — is_success=true, operator/location non-empty, phone_number present",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchOperatorCircle_returns200")
    public void fetchOperatorCircle_validResponse() {
        log.info("Running: fetchOperatorCircle_validResponse");

        Response response = rechargeService.fetchOperatorCircle(PHONE_NUMBER);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.RECHARGE_FETCH_OPERATOR_CIRCLE.getPath() + "?phone_number=" + PHONE_NUMBER);

        // Postman: pm.expect(jsonData.is_success).to.be.true
        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        // Postman: current_operator and current_location are non-empty strings
        String currentOperator = response.jsonPath().getString("data.current_operator");
        String currentLocation = response.jsonPath().getString("data.current_location");
        assertNotNull(currentOperator, "data.current_operator must be present");
        assertNotNull(currentLocation, "data.current_location must be present");
        assertFalse(currentOperator.isEmpty(), "data.current_operator must not be empty");
        assertFalse(currentLocation.isEmpty(), "data.current_location must not be empty");

        // Postman: data has phone_number property
        assertNotNull(response.jsonPath().get("data.phone_number"),
                "data.phone_number must be present");

        // Postman: no error property
        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        log.info("Detected operator: {}, circle: {}", currentOperator, currentLocation);
    }

    // ── 3. POST bills/fetch — mobile prepaid ─────────────────────────────────

    @Test(description = "POST /bills/fetch mobileprepaid — returns 200",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchPlans_validResponse")
    public void fetchPrepaidBill_returns200() {
        log.info("Running: fetchPrepaidBill_returns200 | planId={}", extractedPlanId);

        FetchBillRequestDto request = FetchBillRequestDto.forMobilePrepaid(extractedPlanId);

        Response response = billsService.fetchPrepaidBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());
    }

    @Test(description = "POST /bills/fetch mobileprepaid — is_success=true, message correct, billSummary/totalAmount present",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchPrepaidBill_returns200")
    public void fetchPrepaidBill_validResponse() {
        log.info("Running: fetchPrepaidBill_validResponse");

        FetchBillRequestDto request = FetchBillRequestDto.forMobilePrepaid(extractedPlanId);

        Response response = billsService.fetchPrepaidBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());

        // Postman: pm.expect(jsonData.is_success).to.be.true
        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        // Postman: pm.expect(jsonData.message).to.equal("fetched bill successfully")
        assertEquals(response.jsonPath().getString("message"), "fetched bill successfully",
                "message must be \"fetched bill successfully\"");

        // Postman: data.billSummary exists, data.totalDetails.totalAmount exists
        assertNotNull(response.jsonPath().get("data.billSummary"),
                "data.billSummary must be present");
        assertNotNull(response.jsonPath().get("data.totalDetails.totalAmount"),
                "data.totalDetails.totalAmount must be present");

        // Postman: amount > 0
        double totalAmount = Double.parseDouble(
                response.jsonPath().getString("data.totalDetails.totalAmount"));
        assertTrue(totalAmount > 0, "totalAmount must be greater than 0");

        extractedBillAmount = totalAmount;
        log.info("Extracted bill_amount={}", extractedBillAmount);
    }

    // ── 4. POST payment/initiate — mobile prepaid ─────────────────────────────

    @Test(description = "POST /payment/initiate mobileprepaid — returns 200",
          groups = "rcbp-recharge",
          dependsOnMethods = "fetchPrepaidBill_validResponse")
    public void initiatePrepaidPayment_returns200() {
        log.info("Running: initiatePrepaidPayment_returns200 | planId={} amount={}",
                extractedPlanId, extractedPlanAmount);

        InitiatePaymentRequestDto request = InitiatePaymentRequestDto.forMobilePrepaid(
                extractedPlanId, extractedPlanAmount);

        Response response = paymentService.initiatePrepaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    @Test(description = "POST /payment/initiate mobileprepaid — is_success=true, message correct, orderNumber /^RC/; extracts order_number",
          groups = "rcbp-recharge",
          dependsOnMethods = "initiatePrepaidPayment_returns200")
    public void initiatePrepaidPayment_validResponse() {
        log.info("Running: initiatePrepaidPayment_validResponse");

        InitiatePaymentRequestDto request = InitiatePaymentRequestDto.forMobilePrepaid(
                extractedPlanId, extractedPlanAmount);

        Response response = paymentService.initiatePrepaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_INITIATE.getPath());

        // Postman: pm.expect(jsonData.is_success).to.be.true
        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        // Postman: pm.expect(jsonData.message).to.equal("payment initiated successfully!")
        assertEquals(response.jsonPath().getString("message"), "payment initiated successfully!",
                "message must be \"payment initiated successfully!\"");

        // Postman: pm.expect(jsonData.data.orderNumber).to.match(/^RC/)
        String orderNumber = response.jsonPath().getString("data.orderNumber");
        assertNotNull(orderNumber, "data.orderNumber must be present");
        assertTrue(orderNumber.startsWith("RC"),
                "data.orderNumber must start with 'RC', got: " + orderNumber);

        // Postman: orderNumber and gatewayOrderId are non-empty strings
        String gatewayOrderId = response.jsonPath().getString("data.gatewayOrderId");
        assertNotNull(gatewayOrderId, "data.gatewayOrderId must be present");
        assertFalse(orderNumber.isEmpty(),    "data.orderNumber must not be empty");
        assertFalse(gatewayOrderId.isEmpty(), "data.gatewayOrderId must not be empty");

        // Postman: no error property
        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        extractedOrderNumber = orderNumber;
        log.info("Extracted order_number: {}", extractedOrderNumber);
    }

    // ── 5. UPI payment mock — SKIPPED ─────────────────────────────────────────
    //
    // The "UPI payment mock" step hits a Postman mock server (not a real API).
    // paymentReferenceId is generated here as "pay_" + timestamp.

    // ── 6. POST payment/confirm — mobile prepaid ──────────────────────────────

    @Test(description = "POST /payment/confirm mobileprepaid — returns 200",
          groups = "rcbp-recharge",
          dependsOnMethods = "initiatePrepaidPayment_validResponse")
    public void confirmPrepaidPayment_returns200() {
        log.info("Running: confirmPrepaidPayment_returns200 | orderNumber={}", extractedOrderNumber);

        if (extractedOrderNumber == null) {
            throw new SkipException("Skipping — extractedOrderNumber not set (initiate step failed)");
        }

        String paymentReferenceId = "pay_" + System.currentTimeMillis();

        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.forMobilePrepaid(
                extractedOrderNumber, paymentReferenceId, extractedBillAmount, PHONE_NUMBER);

        Response response = paymentService.confirmPrepaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_CONFIRM.getPath());
    }

    @Test(description = "POST /payment/confirm mobileprepaid — is_success=true, status=Processing, required fields present",
          groups = "rcbp-recharge",
          dependsOnMethods = "confirmPrepaidPayment_returns200")
    public void confirmPrepaidPayment_validResponse() {
        log.info("Running: confirmPrepaidPayment_validResponse");

        if (extractedOrderNumber == null) {
            throw new SkipException("Skipping — extractedOrderNumber not set (initiate step failed)");
        }

        String paymentReferenceId = "pay_" + System.currentTimeMillis();

        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.forMobilePrepaid(
                extractedOrderNumber, paymentReferenceId, extractedBillAmount, PHONE_NUMBER);

        Response response = paymentService.confirmPrepaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_CONFIRM.getPath());

        // Postman: pm.expect(jsonData.is_success).to.be.true
        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        // Postman: data has status, transactionId, refId, paymentDetails
        assertNotNull(response.jsonPath().get("data.status"),         "data.status must be present");
        assertNotNull(response.jsonPath().get("data.transactionId"),  "data.transactionId must be present");
        assertNotNull(response.jsonPath().get("data.refId"),          "data.refId must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails"), "data.paymentDetails must be present");

        // Postman: paymentDetails has amount, mode, paymentRefId, timestamp
        assertNotNull(response.jsonPath().get("data.paymentDetails.amount"),      "paymentDetails.amount must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails.mode"),        "paymentDetails.mode must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails.paymentRefId"),"paymentDetails.paymentRefId must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails.timestamp"),   "paymentDetails.timestamp must be present");

        // Postman: status != "Failed"
        String status = response.jsonPath().getString("data.status");
        assertNotEquals(status, "Failed", "data.status must not be 'Failed'");

        // Postman: status = "Processing"
        assertEquals(status, "Processing", "data.status must be \"Processing\"");

        // Postman: transactionId is a non-empty string
        String transactionId = response.jsonPath().getString("data.transactionId");
        assertNotNull(transactionId, "data.transactionId must not be null");
        assertFalse(transactionId.isEmpty(), "data.transactionId must not be empty");

        // Postman: no error property
        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        log.info("Confirm status: {}, transactionId: {}", status, transactionId);
    }

    /*
     * ── What is NOT tested (not available in collection) ──────────────────────
     *
     * 1. fetchOperatorsAndStates: removed from the new prepaid collection.
     *
     * 2. UPI payment mock step: Postman mock server — not a real API.
     *    paymentReferenceId is generated via "pay_" + timestamp.
     *
     * 3. Error scenarios (4xx/5xx): no negative examples in the collection.
     */
}
