package com.popclub.apiTests.tuition;

import com.popclub.api.dto.tuition.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.impl.BaseService;
import com.popclub.api.impl.C2CService;
import com.popclub.api.impl.OmsService;
import com.popclub.core.TestContext;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static com.popclub.api.impl.BaseService.assertStatus;
import static java.lang.Math.round;
import static org.testng.Assert.*;

public class TuitionPayerJourneyTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionPayerJourneyTest.class);

    private C2CService csvc;
    private OmsService oms;

    // state shared across tests
    private String payoutReferenceId;
    private double amountInPaisa = 2.00; // 500.00 INR in paise
    private String paymentIntentId;
    private String reserveId;
    private String orderNumber;
    // private String inviteCode;
    private String pan = "BWHPC6702P";
    private String mobile = "7069283292";
    private String payeeDisplayId;
    private String payerId;
    private String inviteLink;

    private java.util.concurrent.CompletableFuture<java.util.List<String>> sseStreamFuture;

    private String totalCtaAmount;
    private double totalAmount;
    private double convenienceFee;
    private double gstConvenienceFee;
    private double totalPayable;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        String token = TestContext.getUserToken();
        assertNotNull(token, "Student access_token not found in TestContext — run ts_tuition_student_login.yaml first");

        csvc = new C2CService();
        oms = new OmsService();

        csvc.attachToken(token);
        oms.attachToken(token);

        System.out.println("[DEBUG] setup() instance=" + this.hashCode() + " csvc=" + csvc);
    }

    // ─── Happy Path ──────────────────────────────────────────────────────────

    @Test(groups = "tuition-payer")
    public void step1_getPayeeHistory() {
        System.out.println("[DEBUG] step1 instance=" + this.hashCode() + " csvc=" + csvc);

        Response r = csvc.getPayeeList("98");

        assertStatus(r, 200, "GET", Routes.CSVC_PAYEE_LIST.getPath());
        assertNotNull(r.jsonPath().get("data"), "Payee list data should not be null");

        log.info("Payee list fetched successfully");
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step1_getPayeeHistory")
    public void step2_getPayeeBasicInfo() {
        Response r = csvc.getPayeeBasicInfo(
                PayeeBasicInfoRequest.builder()
                        .paymentOption("VPA")
                        .vpa("7069283292@yespop")
                        .bankAccountNumber("")
                        .bankIfsc("")
                        .bankAccountName("")
                        .bankName("")
                        .build()
        );

        assertStatus(r, 200, "POST", Routes.CSVC_PAYEE_BASIC_INFO.getPath());

        payoutReferenceId = r.jsonPath().getString("data.payee_info.payout_reference_id");

        assertNotNull(payoutReferenceId, "payoutReferenceId should be returned");

        log.info("PayeeBasicInfo OK, payoutReferenceId={}", payoutReferenceId);
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step2_getPayeeBasicInfo")
    public void step3_verifyPayeeInfo() {
        Response resp = csvc.verifyPayee(
                VerifyPayeeRequestDto.builder()
                        .pan(pan)
                        .mobile(mobile)
                        .payoutReferenceId(payoutReferenceId)
                        .build()
        );

        assertStatus(resp, 200, "POST", Routes.CSVC_PAYEE_VERIFICATION.getPath());

        assertEquals(resp.jsonPath().getString("is_success"), "true", "Payee verification successful");

        payeeDisplayId = resp.jsonPath().getString("data.payee.payee_display_id");

        assertNotNull(payeeDisplayId, "Payee Display Id should not be null");
        assertNotNull(resp.jsonPath().getString("data.payee.payee_id"), "Payee Id cannot be null");

        log.info("PayeeDisplayId fetched: {}", payeeDisplayId);
    }

    private static double round2(double value) {
        return new BigDecimal(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step3_verifyPayeeInfo")
    public void step4_getPaymentSummary() {
        Response r = csvc.getPaymentSummary(payeeDisplayId, amountInPaisa);

        assertStatus(r, 200, "GET", Routes.CSVC_PAYMENT_SUMMARY.getPath());

        totalCtaAmount = r.jsonPath().getString("data.fee.total_cta_amount");
        totalAmount = r.jsonPath().getDouble("data.fee.total_amount");
        convenienceFee = r.jsonPath().getDouble("data.fee.convenience_fee");
        gstConvenienceFee = r.jsonPath().getDouble("data.fee.gst_convenience_fee");
        totalPayable = r.jsonPath().getDouble("data.fee.total_payable");

        assertEquals(amountInPaisa, totalAmount, "Amount given is right");

        assertNotNull(totalCtaAmount, "Total CTA cannot be null");
        assertNotNull(totalPayable, "Total Payable cannot be null");
        assertNotNull(totalAmount, "Total Amount cannot be null");
        assertNotNull(convenienceFee, "Convenience Fee cannot be null");
        assertNotNull(gstConvenienceFee, "Convenience Fee cannot be null");

        double calculatedConvenienceFee = round2(amountInPaisa * 0.025);
        double calculatedGstConvenienceFee = round2(calculatedConvenienceFee * 0.18);
        double calculatedTotalPayable = round2(
                amountInPaisa + calculatedConvenienceFee + calculatedGstConvenienceFee
        );

        assertEquals(convenienceFee, calculatedConvenienceFee, "Convenience fee calculated is right");
        assertEquals(gstConvenienceFee, calculatedGstConvenienceFee, "Gst calculated is right");
        assertEquals(totalPayable, calculatedTotalPayable, "Total payable calculated is right");

        log.info(
                "Payment summary: amount={}, convenienceFee={}, gst={}, totalPayable={}",
                totalAmount, convenienceFee, gstConvenienceFee, totalPayable
        );
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step4_getPaymentSummary")
    public void step5_generatePaymentIntent() {
        Response r = csvc.generatePaymentIntent(
                GeneratePaymentIntentRequest.builder()
                        .payeeDisplayId(payeeDisplayId)
                        .totalPayable(String.valueOf(totalPayable))
                        .gstConvenienceFee(String.valueOf(gstConvenienceFee))
                        .totalAmount(String.valueOf(totalAmount))
                        .convenienceFee(String.valueOf(convenienceFee))
                        .idempotencyKey(java.util.UUID.randomUUID().toString())
                        .build()
        );

        assertStatus(r, 200, "POST", Routes.CSVC_PAYMENT_INTENT.getPath());

        assertEquals(r.jsonPath().getString("is_success"), "true", "Payment intent should be successful");
        assertEquals(r.jsonPath().getString("message"), "success", "Message should be success");

        paymentIntentId = r.jsonPath().getString("data.payment_intent_id");

        assertNotNull(paymentIntentId, "payment_intent_id should be returned");
        assertNotNull(r.jsonPath().getString("data.payee_id"), "payee_id should be returned");
        assertNotNull(r.jsonPath().getString("data.payee_display_id"), "payee_display_id should be returned");

        payerId = r.jsonPath().getString("data.payer_id");

        assertNotNull(payerId, "payer_id should be returned");
        assertNotNull(r.jsonPath().getString("data.total_payable"), "total_payable should be returned");

        log.info("Payment intent generated: {}", paymentIntentId);
    }

    public void step6_reserveLimit() {
        Response r = csvc.reserveLimit(
                ReserveLimitRequest.builder()
                        .paymentIntentId(paymentIntentId)
                        .amount(amountInPaisa)
                        .build()
        );

        assertStatus(r, 200, "POST", Routes.CSVC_LIMIT_RESERVE.getPath());

        reserveId = r.jsonPath().getString("data.reserve_id");

        assertNotNull(reserveId, "reserve_id should be returned");

        log.info("Limit reserved: reserve_id={}", reserveId);
    }

    @Test(groups = "tuition-payer", dependsOnMethods = "step5_generatePaymentIntent")
    public void step7_createTuitionOrder() {
        Response r = oms.createTuitionOrder(
                CreateTuitionOrderRequest.builder()
                        .orderType("TUITION")
                        .paymentIntentId(paymentIntentId)
                        .payment(
                                CreateTuitionOrderRequest.Payment.builder()
                                        .method("CARD")
                                        .details(
                                                CreateTuitionOrderRequest.CardDetails.builder()
                                                        .cardNumber("4111111111111111")
                                                        .expiryMonth("12")
                                                        .expiryYear("2028")
                                                        .cvv("123")
                                                        .cardHolderName("Test User")
                                                        .cardType("DEBIT")
                                                        .flow("non-save")
                                                        .saveCard(false)
                                                        .build()
                                        )
                                        .build()
                        )
                        .coinsUsed(0)
                        .amountBreakdown(
                                java.util.List.of(
                                        CreateTuitionOrderRequest.AmountBreakdown.builder()
                                                .key("total_amount")
                                                .label("Tuition Fee")
                                                .value(String.valueOf(totalAmount))
                                                .displayValue("₹" + String.valueOf(totalAmount))
                                                .build(),

                                        CreateTuitionOrderRequest.AmountBreakdown.builder()
                                                .key("convenience_fee")
                                                .label("Convenience Fee")
                                                .value(String.valueOf(convenienceFee))
                                                .displayValue("₹" + String.valueOf(convenienceFee))
                                                .build(),

                                        CreateTuitionOrderRequest.AmountBreakdown.builder()
                                                .key("gst_convenience_fee")
                                                .label("GST")
                                                .value(String.valueOf(gstConvenienceFee))
                                                .displayValue("₹" + String.valueOf(gstConvenienceFee))
                                                .build(),

                                        CreateTuitionOrderRequest.AmountBreakdown.builder()
                                                .key("total_payable")
                                                .label("Total Payable")
                                                .value(String.valueOf(totalPayable))
                                                .displayValue("₹" + String.valueOf(totalPayable))
                                                .build()
                                )
                        )
                        .build()
        );

        assertStatus(r, 200, "POST", Routes.OMS_ORDERS.getPath());

        orderNumber = r.jsonPath().getString("data.order_number");

        // inviteCode = r.jsonPath().getString("data.invite_code");

        assertNotNull(orderNumber, "order_number should be returned");

        // assertNotNull(inviteCode, "invite_code should be returned");

        // Share state for the onboarding test
        TestContext.setScalarData("c2c_order_number", orderNumber);

        // TestContext.setScalarData("c2c_invite_code", inviteCode);

        // log.info(
        //         "Tuition order created: orderNumber={}, inviteCode={}",
        //         orderNumber,
        //         inviteCode
        // );

        log.info("Tuition order created: orderNumber={}", orderNumber);

        // Open SSE stream before payment verification.
        // This ensures we are already listening when the backend emits LINK_CREATED.
        sseStreamFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return oms.listenToOrderStream(
                        orderNumber,
                        "LINK_CREATED",
                        java.time.Duration.ofSeconds(65)
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /*
     * Old SSE-only step kept for reference.
     * Current happy flow uses step8_completePaymentAndAwaitLink().
     *
     * @Test(groups = "tuition-payer", dependsOnMethods = "step7_createTuitionOrder")
     * public void step8_awaitLinkCreatedEvent() throws Exception {
     *     java.util.List<String> events =
     *             sseStreamFuture.get(
     *                     65,
     *                     java.util.concurrent.TimeUnit.SECONDS
     *             );
     *
     *     assertFalse(
     *             events.isEmpty(),
     *             "Should receive at least one SSE event for order " + orderNumber
     *     );
     *
     *     assertTrue(
     *             events.stream().anyMatch(e -> e.contains("LINK_CREATED")),
     *             "Expected LINK_CREATED event in SSE stream, got: " + events
     *     );
     *
     *     log.info(
     *             "SSE events received for order {}: {}",
     *             orderNumber,
     *             events
     *     );
     * }
     */

    @Test(groups = "tuition-payer", dependsOnMethods = "step7_createTuitionOrder")
    public void step8_completePaymentAndAwaitLink() throws Exception {
        java.util.Map<String, Object> verifyBody = new java.util.LinkedHashMap<>();

        verifyBody.put("category", "TUITION");
        verifyBody.put("order_number", orderNumber);
        verifyBody.put("payer_id", payerId);

        verifyBody.put(
                "fee",
                java.util.Map.of(
                        "total_payable", String.valueOf(totalPayable),
                        "convenience_fee", String.valueOf(convenienceFee),
                        "gst_convenience_fee", String.valueOf(gstConvenienceFee),
                        "total_amount", String.valueOf(totalAmount)
                )
        );

        // Verify payment intent after SSE listener is already active.
        Response verifyResp = csvc.verifyPaymentIntent(paymentIntentId, verifyBody);

        assertStatus(
                verifyResp,
                200,
                "POST",
                Routes.CSVC_PAYMENT_INTENT_VERIFY.kongUrl(paymentIntentId)
        );

        assertEquals(
                verifyResp.jsonPath().getString("data.payment_allowed"),
                "true",
                "Payment should be allowed"
        );

        inviteLink = verifyResp.jsonPath().getString("data.payee.pay_msg_link");

        assertNotNull(inviteLink, "Tutor invite link should be returned");
        assertFalse(inviteLink.isBlank(), "Tutor invite link should not be blank");

        TestContext.setScalarData("c2c_invite_link", inviteLink);

        log.info("Tutor invite link received: {}", inviteLink);

        // Wait for the SSE event triggered by the verified payment.
        java.util.List<String> events = sseStreamFuture.get(
                65,
                java.util.concurrent.TimeUnit.SECONDS
        );

        assertFalse(
                events.isEmpty(),
                "Should receive at least one SSE event for order " + orderNumber
        );

        assertTrue(
                events.stream().anyMatch(e -> e.contains("LINK_CREATED")),
                "Expected LINK_CREATED event in SSE stream, got: " + events
        );

        log.info("Payment intent verified successfully");

        log.info(
                "SSE events received for order {}: {}",
                orderNumber,
                events
        );
    }

    /*
     * Old verify-payment step kept for reference.
     * Verify Payment Intent is now part of step8_completePaymentAndAwaitLink().
     *
     * // @Test(groups = "tuition-payer", dependsOnMethods = "step8_awaitLinkCreatedEvent")
     * // public void step9_verifyPaymentIntent() {
     * //     java.util.Map<String, Object> verifyBody =
     * //             new java.util.LinkedHashMap<>();
     * //
     * //     verifyBody.put("category", "TUITION");
     * //     verifyBody.put("order_number", orderNumber);
     * //     verifyBody.put("payer_id", payerId);
     * //
     * //     verifyBody.put(
     * //             "fee",
     * //             java.util.Map.of(
     * //                     "total_payable", String.valueOf(totalPayable),
     * //                     "convenience_fee", String.valueOf(convenienceFee),
     * //                     "gst_convenience_fee", String.valueOf(gstConvenienceFee),
     * //                     "total_amount", String.valueOf(totalAmount)
     * //             )
     * //     );
     * //
     * //     Response verifyResp =
     * //             csvc.verifyPaymentIntent(paymentIntentId, verifyBody);
     * //
     * //     assertStatus(
     * //             verifyResp,
     * //             200,
     * //             "POST",
     * //             Routes.CSVC_PAYMENT_INTENT_VERIFY.getPath()
     * //     );
     * //
     * //     log.info("Payment intent verified (payer acknowledgment)");
     * // }
     */

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer", dependsOnMethods = "step7_createTuitionOrder")
    public void step8_pollOrderStatus_awaitingPayment() {
        Response r = oms.pollOrderStatus(
                orderNumber,
                "AWAITING_PAYMENT",
                5
        );

        assertStatus(r, 200, "GET", Routes.OMS_ORDER_STATUS.kongUrl(orderNumber));

        String status = r.jsonPath().getString("data.status");

        assertNotNull(status, "Order status should not be null");

        log.info("Order status after creation: {}", status);
    }

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer", dependsOnMethods = "step7_createTuitionOrder")
    public void step9_consumeReservedLimit_happyPath() {
        Response r = csvc.consumeLimit(
                LimitActionRequest.builder()
                        .paymentIntentId(paymentIntentId)
                        .reserveId(reserveId)
                        .build()
        );

        assertStatus(r, 200, "POST", Routes.CSVC_LIMITS_CONSUME.getPath());

        log.info("Limit consumed successfully");
    }

    // ─── Error Cases ─────────────────────────────────────────────────────────

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer-error")
    public void error_payeeBasicInfo_invalidMobile() {
        Response r = csvc.getPayeeBasicInfo(
                PayeeBasicInfoRequest.builder()
                        .paymentOption("VPA")
                        .vpa("invalid@vpa")
                        .build()
        );

        assertEquals(r.statusCode(), 404, "Invalid mobile should return 404");

        log.info("Error case: invalid mobile → {}", r.statusCode());
    }

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer-error")
    public void error_payeeVerification_unknownPayee() {
        Response r = csvc.verifyPayee(
                VerifyPayeeRequestDto.builder()
                        .payoutReferenceId("invalid-payee-id-000")
                        .build()
        );

        assertTrue(
                r.statusCode() == 400 || r.statusCode() == 404,
                "Unknown payee should return 400 or 404, got: " + r.statusCode()
        );

        log.info("Error case: unknown payee → {}", r.statusCode());
    }

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer-error")
    public void error_paymentSummary_missingAmount() {
        Response r = csvc.getPaymentSummary(
                payeeDisplayId != null ? payeeDisplayId : "dummy",
                0L
        );

        assertTrue(
                r.statusCode() == 400 || r.statusCode() == 422,
                "Zero amount should return 400/422, got: " + r.statusCode()
        );

        log.info("Error case: zero amount → {}", r.statusCode());
    }

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer-error")
    public void error_generatePaymentIntent_missingPayee() {
        Response r = csvc.generatePaymentIntent(
                GeneratePaymentIntentRequest.builder()
                        .payeeDisplayId("nonexistent-payee")
                        .totalAmount(String.valueOf(amountInPaisa))
                        .idempotencyKey(java.util.UUID.randomUUID().toString())
                        .build()
        );

        assertTrue(
                r.statusCode() == 400 || r.statusCode() == 404,
                "Missing payee should return 400/404, got: " + r.statusCode()
        );

        log.info("Error case: missing payee in intent → {}", r.statusCode());
    }

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer-error")
    public void error_reserveLimit_invalidIntent() {
        Response r = csvc.reserveLimit(
                ReserveLimitRequest.builder()
                        .paymentIntentId("invalid-intent-id")
                        .amount(amountInPaisa)
                        .build()
        );

        assertTrue(
                r.statusCode() == 400 || r.statusCode() == 404,
                "Invalid intent should return 400/404, got: " + r.statusCode()
        );

        log.info("Error case: invalid payment intent for reserve → {}", r.statusCode());
    }

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer-error")
    public void error_orderStatus_unknownOrder() {
        Response r = oms.getOrderStatus("UNKNOWN-ORDER-000");

        assertEquals(r.statusCode(), 404, "Unknown order status should return 404");

        log.info("Error case: unknown order status → {}", r.statusCode());
    }

    // Not part of current happy flow.
    // @Test(groups = "tuition-payer-error")
    public void error_releaseLimit_happyPath() {
        // Reserve a new intent first (uses a dummy; validates the release path)
        // This test verifies the release endpoint is callable — actual E2E requires a valid reserve

        Response r = csvc.releaseLimit(
                LimitActionRequest.builder()
                        .paymentIntentId("test-intent-release")
                        .reserveId("test-reserve-id")
                        .build()
        );

        assertTrue(
                r.statusCode() == 200
                        || r.statusCode() == 400
                        || r.statusCode() == 404,
                "Release should return 200, 400, or 404, got: " + r.statusCode()
        );

        log.info("Error case: release limit with dummy ids → {}", r.statusCode());
    }
}
