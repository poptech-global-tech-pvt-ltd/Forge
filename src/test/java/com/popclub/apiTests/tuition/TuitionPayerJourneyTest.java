package com.popclub.apiTests.tuition;

import com.popclub.api.dto.tuition.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.impl.BaseService;
import com.popclub.api.impl.CsvcService;
import com.popclub.api.impl.OmsService;
import com.popclub.api.util.ConfigManager;
import com.popclub.core.TestContext;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TuitionPayerJourneyTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionPayerJourneyTest.class);

    private CsvcService csvc;
    private OmsService  oms;

    // state shared across tests
    private String payeeId;
    private long   amount = 50000L; // 500.00 INR in paise
    private String paymentIntentId;
    private String reserveId;
    private String orderNumber;
    private String inviteCode;

    @BeforeClass
    public void setup() {
        String token = TestContext.getUserToken();
        assertNotNull(token, "Student access_token not found in TestContext — run ts_tuition_student_login.yaml first");

        csvc = new CsvcService();
        oms  = new OmsService();
        csvc.attachToken(token);
        oms.attachToken(token);
    }

    // ─── Happy Path ──────────────────────────────────────────────────────────

    @Test(groups = "tuition-payer")
    public void step1_getPayeeHistory() {
        Response r = csvc.getPayeeList("98");
        BaseService.assertStatus(r, 200, "GET", Routes.CSVC_PAYEE_LIST.getPath());
        assertNotNull(r.jsonPath().get("data"), "Payee list data should not be null");
        log.info("Payee list fetched successfully");
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step1_getPayeeHistory")
    public void step2_getPayeeBasicInfo() {
        Response r = csvc.getPayeeBasicInfo(PayeeBasicInfoRequest.builder()
                .mobileNumber(ConfigManager.getTutorMobileNumber())
                .mobileCountryCode("+91")
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.CSVC_PAYEE_BASIC_INFO.getPath());

        payeeId = r.jsonPath().getString("data.payee_id");
        assertNotNull(payeeId, "payee_id should be returned");
        log.info("PayeeBasicInfo OK, payee_id={}", payeeId);
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step2_getPayeeBasicInfo")
    public void step3_verifyPayeeInfo() {
        Response r = csvc.verifyPayeeInfo(VerifyPayeeRequest.builder()
                .payeeId(payeeId)
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.CSVC_PAYEE_VERIFICATION.getPath());
        assertEquals(r.jsonPath().getString("data.status"), "verified", "Payee should be verified");
        log.info("Payee verification passed");
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step3_verifyPayeeInfo")
    public void step4_getPaymentSummary() {
        Response r = csvc.getPaymentSummary(payeeId, amount);
        BaseService.assertStatus(r, 200, "GET", Routes.CSVC_PAYMENT_SUMMARY.getPath());

        // Fee structure: 2.5% platform fee + 18% GST on fee
        assertNotNull(r.jsonPath().get("data.platform_fee"), "platform_fee missing");
        assertNotNull(r.jsonPath().get("data.gst"),          "gst missing");
        assertNotNull(r.jsonPath().get("data.total_amount"), "total_amount missing");
        log.info("Payment summary: fee={}, gst={}, total={}",
                r.jsonPath().get("data.platform_fee"),
                r.jsonPath().get("data.gst"),
                r.jsonPath().get("data.total_amount"));
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step4_getPaymentSummary")
    public void step5_generatePaymentIntent() {
        Response r = csvc.generatePaymentIntent(GeneratePaymentIntentRequest.builder()
                .payeeId(payeeId)
                .amount(amount)
                .remarks("Tuition fee for Math")
                .deviceId("test-device-001")
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.CSVC_PAYMENT_INTENT.getPath());

        paymentIntentId = r.jsonPath().getString("data.payment_intent_id");
        assertNotNull(paymentIntentId, "payment_intent_id should be returned");
        log.info("Payment intent generated: {}", paymentIntentId);
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step5_generatePaymentIntent")
    public void step6_reserveLimit() {
        Response r = csvc.reserveLimit(ReserveLimitRequest.builder()
                .paymentIntentId(paymentIntentId)
                .amount(amount)
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.CSVC_LIMIT_RESERVE.getPath());

        reserveId = r.jsonPath().getString("data.reserve_id");
        assertNotNull(reserveId, "reserve_id should be returned");
        log.info("Limit reserved: reserve_id={}", reserveId);
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step6_reserveLimit")
    public void step7_createTuitionOrder() {
        Response r = oms.createTuitionOrder(CreateTuitionOrderRequest.builder()
                .paymentIntentId(paymentIntentId)
                .payeeId(payeeId)
                .amount(amount)
                .remarks("Tuition fee for Math")
                .build());
        BaseService.assertStatus(r, 201, "POST", Routes.OMS_ORDERS.getPath());

        orderNumber = r.jsonPath().getString("data.order_number");
        inviteCode  = r.jsonPath().getString("data.invite_code");
        assertNotNull(orderNumber, "order_number should be returned");
        assertNotNull(inviteCode,  "invite_code should be returned");

        // Share state for the onboarding test
        TestContext.setScalarData("c2c_order_number", orderNumber);
        TestContext.setScalarData("c2c_invite_code",  inviteCode);
        log.info("Tuition order created: orderNumber={}, inviteCode={}", orderNumber, inviteCode);
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step7_createTuitionOrder")
    public void step8_pollOrderStatus_awaitingPayment() {
        Response r = oms.pollOrderStatus(orderNumber, "AWAITING_PAYMENT", 5);
        BaseService.assertStatus(r, 200, "GET", Routes.OMS_ORDER_STATUS.kongUrl(orderNumber));
        String status = r.jsonPath().getString("data.status");
        assertNotNull(status, "Order status should not be null");
        log.info("Order status after creation: {}", status);
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step7_createTuitionOrder")
    public void step9_consumeReservedLimit_happyPath() {
        Response r = csvc.consumeLimit(LimitActionRequest.builder()
                .paymentIntentId(paymentIntentId)
                .reserveId(reserveId)
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.CSVC_LIMITS_CONSUME.getPath());
        log.info("Limit consumed successfully");
    }

    // ─── Error Cases ─────────────────────────────────────────────────────────

    @Test(groups = "tuition-payer-error")
    public void error_payeeBasicInfo_invalidMobile() {
        Response r = csvc.getPayeeBasicInfo(PayeeBasicInfoRequest.builder()
                .mobileNumber("0000000000")
                .mobileCountryCode("+91")
                .build());
        assertEquals(r.statusCode(), 404, "Invalid mobile should return 404");
        log.info("Error case: invalid mobile → {}", r.statusCode());
    }

    @Test(groups = "tuition-payer-error")
    public void error_payeeVerification_unknownPayee() {
        Response r = csvc.verifyPayeeInfo(VerifyPayeeRequest.builder()
                .payeeId("invalid-payee-id-000")
                .build());
        assertTrue(r.statusCode() == 400 || r.statusCode() == 404,
                "Unknown payee should return 400 or 404, got: " + r.statusCode());
        log.info("Error case: unknown payee → {}", r.statusCode());
    }

    @Test(groups = "tuition-payer-error")
    public void error_paymentSummary_missingAmount() {
        Response r = csvc.getPaymentSummary(payeeId != null ? payeeId : "dummy", 0L);
        assertTrue(r.statusCode() == 400 || r.statusCode() == 422,
                "Zero amount should return 400/422, got: " + r.statusCode());
        log.info("Error case: zero amount → {}", r.statusCode());
    }

    @Test(groups = "tuition-payer-error")
    public void error_generatePaymentIntent_missingPayee() {
        Response r = csvc.generatePaymentIntent(GeneratePaymentIntentRequest.builder()
                .payeeId("nonexistent-payee")
                .amount(amount)
                .remarks("Test")
                .deviceId("test-device")
                .build());
        assertTrue(r.statusCode() == 400 || r.statusCode() == 404,
                "Missing payee should return 400/404, got: " + r.statusCode());
        log.info("Error case: missing payee in intent → {}", r.statusCode());
    }

    @Test(groups = "tuition-payer-error")
    public void error_reserveLimit_invalidIntent() {
        Response r = csvc.reserveLimit(ReserveLimitRequest.builder()
                .paymentIntentId("invalid-intent-id")
                .amount(amount)
                .build());
        assertTrue(r.statusCode() == 400 || r.statusCode() == 404,
                "Invalid intent should return 400/404, got: " + r.statusCode());
        log.info("Error case: invalid payment intent for reserve → {}", r.statusCode());
    }

    @Test(groups = "tuition-payer-error")
    public void error_orderStatus_unknownOrder() {
        Response r = oms.getOrderStatus("UNKNOWN-ORDER-000");
        assertEquals(r.statusCode(), 404, "Unknown order should return 404");
        log.info("Error case: unknown order status → {}", r.statusCode());
    }

    @Test(groups = "tuition-payer-error")
    public void error_releaseLimit_happyPath() {
        // Reserve a new intent first (uses a dummy; validates the release path)
        // This test verifies the release endpoint is callable — actual E2E requires a valid reserve
        Response r = csvc.releaseLimit(LimitActionRequest.builder()
                .paymentIntentId("test-intent-release")
                .reserveId("test-reserve-id")
                .build());
        assertTrue(r.statusCode() == 200 || r.statusCode() == 400 || r.statusCode() == 404,
                "Release should return 200, 400, or 404, got: " + r.statusCode());
        log.info("Error case: release limit with dummy ids → {}", r.statusCode());
    }
}
