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

public class RechargeTest {

    private static final Logger log = LoggerFactory.getLogger(RechargeTest.class);

    private static final String PHONE_NUMBER  = RcbpConfigManager.getPhone();
    private static final String OPERATOR_NAME = RcbpConfigManager.getOperatorName();

    private long   extractedPlanId;
    private double extractedPlanAmount;
    private double extractedBillAmount;
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

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        List<?> plans = response.jsonPath().getList("data.plans");
        assertNotNull(plans, "data.plans must not be null");
        assertFalse(plans.isEmpty(), "data.plans must not be empty");

        Object planIdVal = response.jsonPath().get("data.plans[0].planData[0].planId");
        Object amountVal = response.jsonPath().get("data.plans[0].planData[0].amount");
        assertNotNull(planIdVal, "data.plans[0].planData[0].planId must be present");
        assertNotNull(amountVal, "data.plans[0].planData[0].amount must be present");

        double amount = ((Number) amountVal).doubleValue();
        assertTrue(amount > 0, "plan amount must be greater than 0");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        extractedPlanId     = ((Number) planIdVal).longValue();
        extractedPlanAmount = amount;

        log.info("Extracted plan_id={}, plan_amount={}", extractedPlanId, extractedPlanAmount);
    }

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

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        String currentOperator = response.jsonPath().getString("data.current_operator");
        String currentLocation = response.jsonPath().getString("data.current_location");
        assertNotNull(currentOperator, "data.current_operator must be present");
        assertNotNull(currentLocation, "data.current_location must be present");
        assertFalse(currentOperator.isEmpty(), "data.current_operator must not be empty");
        assertFalse(currentLocation.isEmpty(), "data.current_location must not be empty");

        assertNotNull(response.jsonPath().get("data.phone_number"),
                "data.phone_number must be present");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        log.info("Detected operator: {}, circle: {}", currentOperator, currentLocation);
    }

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

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        assertEquals(response.jsonPath().getString("message"), "fetched bill successfully",
                "message must be \"fetched bill successfully\"");

        assertNotNull(response.jsonPath().get("data.billSummary"),
                "data.billSummary must be present");
        assertNotNull(response.jsonPath().get("data.totalDetails.totalAmount"),
                "data.totalDetails.totalAmount must be present");

        double totalAmount = Double.parseDouble(
                response.jsonPath().getString("data.totalDetails.totalAmount"));
        assertTrue(totalAmount > 0, "totalAmount must be greater than 0");

        extractedBillAmount = totalAmount;
        log.info("Extracted bill_amount={}", extractedBillAmount);
    }

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

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        assertEquals(response.jsonPath().getString("message"), "payment initiated successfully!",
                "message must be \"payment initiated successfully!\"");

        String orderNumber = response.jsonPath().getString("data.orderNumber");
        assertNotNull(orderNumber, "data.orderNumber must be present");
        assertTrue(orderNumber.startsWith("RC"),
                "data.orderNumber must start with 'RC', got: " + orderNumber);

        String gatewayOrderId = response.jsonPath().getString("data.gatewayOrderId");
        assertNotNull(gatewayOrderId, "data.gatewayOrderId must be present");
        assertFalse(orderNumber.isEmpty(),    "data.orderNumber must not be empty");
        assertFalse(gatewayOrderId.isEmpty(), "data.gatewayOrderId must not be empty");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        extractedOrderNumber = orderNumber;
        log.info("Extracted order_number: {}", extractedOrderNumber);
    }

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

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        assertNotNull(response.jsonPath().get("data.status"),         "data.status must be present");
        assertNotNull(response.jsonPath().get("data.transactionId"),  "data.transactionId must be present");
        assertNotNull(response.jsonPath().get("data.refId"),          "data.refId must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails"), "data.paymentDetails must be present");

        assertNotNull(response.jsonPath().get("data.paymentDetails.amount"),       "paymentDetails.amount must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails.mode"),         "paymentDetails.mode must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails.paymentRefId"), "paymentDetails.paymentRefId must be present");
        assertNotNull(response.jsonPath().get("data.paymentDetails.timestamp"),    "paymentDetails.timestamp must be present");

        String status = response.jsonPath().getString("data.status");
        assertNotEquals(status, "Failed", "data.status must not be 'Failed'");
        assertEquals(status, "Processing", "data.status must be \"Processing\"");

        String transactionId = response.jsonPath().getString("data.transactionId");
        assertNotNull(transactionId, "data.transactionId must not be null");
        assertFalse(transactionId.isEmpty(), "data.transactionId must not be empty");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        log.info("Confirm status: {}, transactionId: {}", status, transactionId);
    }
}
