package com.popclub.apiTests.tuition;

import com.popclub.android.cloud.CloudConfig;
import com.popclub.android.webview.RazorpayCheckoutOtpPage;
import com.popclub.api.dto.tuition.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.impl.C2CService;
import com.popclub.api.impl.OmsService;
import com.popclub.core.TestContext;
import com.popclub.testdata.TuitionTestData;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import com.popclub.web.constants.TestCaseId;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.popclub.api.impl.BaseService.assertStatus;
import static org.testng.Assert.*;

public class TuitionPayerJourneyTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionPayerJourneyTest.class);

    private C2CService csvc;
    private OmsService oms;

    private String payoutReferenceId;
    private String paymentIntentId;
    private String reserveId;
    private String orderNumber;
    private String payeeDisplayId;
    private String payerId;

    private CompletableFuture<List<String>> sseStreamFuture;

    private PaymentSummaryResponse.Fee paymentFee;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        String token = TestContext.getUserToken();
        assertNotNull(token, "Student access_token not found in TestContext — run ts_tuition_student_login.yaml first");

        csvc = new C2CService();
        oms = new OmsService();

        csvc.attachToken(token);
        oms.attachToken(token);

        log.info("Test tutor: mobile={}, pan={}",
                TuitionTestData.get().getTutor().getMobile(), TuitionTestData.get().getTutor().getPan());
    }

    // precondition: Payer is logged in with a valid student access_token.
    // steps: Call GET /payee-list?search=98&search_field=mobile; assert HTTP 200.
    // expected: Payee history is returned with a non-null data object (verified/unverified lists).
    @Test(groups = "tuition-payer")
    @TestCaseId("PO-17259")
    public void step1_getPayeeHistory() {
        Response r = csvc.getPayeeList("98");

        assertStatus(r, 200, "GET", Routes.CSVC_PAYEE_LIST.getPath());
        PayeeListResponse payeeListResponse = r.as(PayeeListResponse.class);
        assertNotNull(payeeListResponse.getData(), "Payee list data should not be null");

        log.info("Payee list fetched successfully");
    }

    // precondition: Payer is logged in; tutor's VPA is available from test data.
    // steps: Call POST /payee/basic-info with the tutor's VPA; assert HTTP 200.
    // expected: Response returns vpa_info (name, is_valid) and a payout_reference_id for the next step.
    @Test(groups = "tuition-payer", dependsOnMethods = "step1_getPayeeHistory")
    @TestCaseId("PO-17260")
    public void step2_getPayeeBasicInfo() {
        Response r = csvc.getPayeeBasicInfo(
                PayeeBasicInfoRequest.forVpaSubmission(TuitionTestData.get().getTutor().getVpa())
        );

        assertStatus(r, 200, "POST", Routes.CSVC_PAYEE_BASIC_INFO.getPath());

        PayeeBasicInfoResponse basicInfoResponse = r.as(PayeeBasicInfoResponse.class);
        payoutReferenceId = basicInfoResponse.getData().getPayeeInfo().getPayoutReferenceId();
        assertNotNull(payoutReferenceId, "payoutReferenceId should be returned");
        log.info("PayeeBasicInfo OK, payoutReferenceId={}", payoutReferenceId);
    }

    // precondition: payout_reference_id from step2 (getPayeeBasicInfo) is available.
    // steps: Call POST /payee/verification with tutor PAN, mobile and payout_reference_id; assert HTTP 200.
    // expected: is_success=true and a payee_display_id + payee_id are returned for use in later steps.
    @Test(groups = "tuition-payer", dependsOnMethods = "step2_getPayeeBasicInfo")
    @TestCaseId("PO-17261")
    public void step3_verifyPayeeInfo() {
        Response resp = csvc.verifyPayee(
                VerifyPayeeRequestDto.from(
                        TuitionTestData.get().getTutor().getPan(),
                        TuitionTestData.get().getTutor().getMobile(),
                        payoutReferenceId)
        );

        assertStatus(resp, 200, "POST", Routes.CSVC_PAYEE_VERIFICATION.getPath());

        VerifyPayeeResponse verifyPayeeResponse = resp.as(VerifyPayeeResponse.class);
        assertTrue(verifyPayeeResponse.isSuccess(), "Payee verification successful");

        payeeDisplayId = verifyPayeeResponse.getData().getPayee().getPayeeDisplayId();

        assertNotNull(payeeDisplayId, "Payee Display Id should not be null");
        assertNotNull(verifyPayeeResponse.getData().getPayee().getPayeeId(), "Payee Id cannot be null");

        log.info("PayeeDisplayId fetched: {}", payeeDisplayId);
    }

    private static double round2(double value) {
        return new BigDecimal(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // precondition: payee_display_id from step3 (verifyPayeeInfo) is available.
    // steps: Call GET /payment/summary?payee_display_id&amount; assert HTTP 200; recompute convenience fee (2.5%) and GST (18%) and compare to the response.
    // expected: fee breakdown (total_amount, convenience_fee, gst_convenience_fee, total_payable) matches the independently computed values.
    @Test(groups = "tuition-payer", dependsOnMethods = "step3_verifyPayeeInfo")
    @TestCaseId("PO-17262")
    public void step4_getPaymentSummary() {
        double amountInPaisa = Double.parseDouble(TuitionTestData.get().getPayment().getAmount());
        Response r = csvc.getPaymentSummary(payeeDisplayId, amountInPaisa);

        assertStatus(r, 200, "GET", Routes.CSVC_PAYMENT_SUMMARY.getPath());

        PaymentSummaryResponse summaryResponse = r.as(PaymentSummaryResponse.class);
        paymentFee = summaryResponse.getData().getFee();

        assertEquals(amountInPaisa, paymentFee.getTotalAmount(), "Amount given is right");
        assertNotNull(paymentFee.getTotalCtaAmount(), "Total CTA cannot be null");

        double calculatedConvenienceFee = round2(amountInPaisa * 0.025);
        double calculatedGstConvenienceFee = round2(calculatedConvenienceFee * 0.18);
        double calculatedTotalPayable = round2(
                amountInPaisa + calculatedConvenienceFee + calculatedGstConvenienceFee
        );

        assertEquals(paymentFee.getConvenienceFee(), calculatedConvenienceFee, "Convenience fee calculated is right");
        assertEquals(paymentFee.getGstConvenienceFee(), calculatedGstConvenienceFee, "Gst calculated is right");
        assertEquals(paymentFee.getTotalPayable(), calculatedTotalPayable, "Total payable calculated is right");

        log.info("Payment summary: {}", paymentFee);
    }

    // precondition: payee_display_id and fee breakdown from step4 (getPaymentSummary) are available.
    // steps: Call POST /payee/payment-intent with payee_display_id, fee breakdown and a fresh idempotency_key; assert HTTP 200.
    // expected: is_success=true, message="success", and payment_intent_id, payee_id, payee_display_id, payer_id, total_payable are all returned.
    @Test(groups = "tuition-payer", dependsOnMethods = "step4_getPaymentSummary")
    @TestCaseId("PO-17263")
    public void step5_generatePaymentIntent() {
        Response r = csvc.generatePaymentIntent(
                GeneratePaymentIntentRequest.from(payeeDisplayId, paymentFee, UUID.randomUUID().toString())
        );

        assertStatus(r, 200, "POST", Routes.CSVC_PAYMENT_INTENT.getPath());

        PaymentIntentResponse intentResponse = r.as(PaymentIntentResponse.class);
        assertTrue(intentResponse.isSuccess(), "Payment intent should be successful");
        assertEquals(intentResponse.getMessage(), "success", "Message should be success");

        paymentIntentId = intentResponse.getData().getPaymentIntentId();

        assertNotNull(paymentIntentId, "payment_intent_id should be returned");
        assertNotNull(intentResponse.getData().getPayeeId(), "payee_id should be returned");
        assertNotNull(intentResponse.getData().getPayeeDisplayId(), "payee_display_id should be returned");

        payerId = intentResponse.getData().getPayerId();

        assertNotNull(payerId, "payer_id should be returned");
        assertNotNull(intentResponse.getData().getTotalPayable(), "total_payable should be returned");

        log.info("Payment intent generated: {}", paymentIntentId);
    }

    // Limit state machine (student + tutor each have a ₹1L/month aggregate limit, reset on the
    // 1st of each month): RESERVE at checkout immediately deducts from both sides; the amount
    // stays reserved during AWAITING_ACCEPTANCE; on tutor REJECT it is automatically RELEASED
    // back to both; on tutor ACCEPT (after payout + a temporal update) it is finally CONSUMED.
    //   Available = Configured − Reserved − Consumed + Released + Restored

    // precondition: order_number from step7 (createTuitionOrder) is available.
    // steps: Call POST /limit/reserve with payment_intent_id and order_number (Stage 1: RESERVE — immediately deducts from both student & tutor limits).
    // expected: HTTP 200 and a reserve_id is returned for use in the consume/release steps.
    @Test(groups = "tuition-payer", dependsOnMethods = "step7_createTuitionOrder")
    @TestCaseId("PO-17281")
    public void step6_reserveLimit() {
        Response r = csvc.reserveLimit(ReserveLimitRequest.from(paymentIntentId, orderNumber));

        assertStatus(r, 200, "POST", Routes.CSVC_LIMIT_RESERVE.getPath());

        ReserveLimitResponse reserveResponse = r.as(ReserveLimitResponse.class);
        reserveId = reserveResponse.getData().getReserveId();

        assertNotNull(reserveId, "reserve_id should be returned");

        log.info("Limit reserved: reserve_id={}", reserveId);
    }

    // precondition: payment_intent_id and fee breakdown from step5 (generatePaymentIntent) are available.
    // steps: Build a CARD order payload (test card details + amount breakdown) and call POST /marketplace/orders; assert HTTP 200; start listening to the order's SSE stream for a payment_link event.
    // expected: Order is created successfully and an order_number is returned.
    @Test(groups = "tuition-payer", dependsOnMethods = "step5_generatePaymentIntent")
    @TestCaseId("PO-17264")
    public void step7_createTuitionOrder() {
        TuitionTestData.Payment paymentData = TuitionTestData.get().getPayment();

        CreateTuitionOrderRequest.Payment cardPayment = CreateTuitionOrderRequest.Payment.cardPayment(
                CreateTuitionOrderRequest.CardDetails.of(
                        paymentData.getCardNumber(), paymentData.getExpiryMonth(), paymentData.getExpiryYear(),
                        paymentData.getCvv(), paymentData.getCardHolderName(), paymentData.getCardType())
        );

        Response r = oms.createTuitionOrder(
                CreateTuitionOrderRequest.cardOrder(
                        "TUITION", paymentIntentId, cardPayment, 0,
                        CreateTuitionOrderRequest.AmountBreakdown.listFrom(paymentFee))
        );

        assertStatus(r, 200, "POST", Routes.OMS_ORDERS.getPath());

        CreateTuitionOrderResponse orderResponse = r.as(CreateTuitionOrderResponse.class);
        orderNumber = orderResponse.getData().getOrderNumber();

        assertNotNull(orderNumber, "order_number should be returned");

        TestContext.setScalarData("c2c_order_number", orderNumber);

        log.info("Tuition order created: orderNumber={}", orderNumber);

        sseStreamFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return oms.listenToOrderStream(orderNumber, "payment_link", Duration.ofSeconds(30));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // precondition: order_number from step7 (createTuitionOrder) is available, with its SSE stream already being listened to; limit reserved in step6 and confirmed AWAITING_PAYMENT in step8_pollOrderStatus_awaitingPayment.
    // steps: Call POST /payment-intent/{id}/verify; assert payment_allowed=true and capture the tutor onboarding link (pay_msg_link); extract the Razorpay payment_link from the SSE stream; drive the Razorpay checkout page with a random OTP; await a PAYOUT_ON_HOLD SSE event.
    // expected: Card payment succeeds and the order reaches PAYOUT_ON_HOLD (payout held pending the tutor's onboarding acceptance — Stage 2: AWAITING_ACCEPTANCE, reserved amount still held).
    @Test(groups = "tuition-payer", dependsOnMethods = "step8_pollOrderStatus_awaitingPayment")
    @TestCaseId("PO-17265")
    public void step8_completePaymentAndAwaitLink() throws Exception {
        Response verifyResp = csvc.verifyPaymentIntent(
                paymentIntentId,
                PaymentIntentVerifyRequest.from("TUITION", orderNumber, payerId, paymentFee)
        );

        assertStatus(verifyResp, 200, "POST", Routes.CSVC_PAYMENT_INTENT_VERIFY.getPath());

        PaymentIntentVerifyResponse verifyDto = verifyResp.as(PaymentIntentVerifyResponse.class);
        assertTrue(verifyDto.isSuccess(),
                "Payment intent verify should succeed, body=" + verifyResp.asString());
        assertTrue(verifyDto.getData().isPaymentAllowed(),
                "payment_allowed should be true for a fresh, in-limit payment intent, body="
                        + verifyResp.asString());

        log.info("Payment intent verified — payment_allowed=true");

        String tutorOnboardingLink = verifyDto.getData().getPayee() != null
                ? verifyDto.getData().getPayee().getPayMsgLink() : null;
        if (tutorOnboardingLink != null && !tutorOnboardingLink.isEmpty()) {
            TestContext.setScalarData("c2c_tutor_onboarding_link", tutorOnboardingLink);
            log.info("Tutor onboarding link captured: {}", tutorOnboardingLink);
        } else {
            log.warn("No pay_msg_link found in payment-intent/verify response");
        }

        List<String> events = sseStreamFuture.get(30, TimeUnit.SECONDS);

        assertFalse(events.isEmpty(), "Should receive at least one SSE event for order " + orderNumber);
        log.info("SSE events received ({} total)", events.size());

        String paymentLink = OmsService.extractPaymentLink(events);
        assertNotNull(paymentLink, "❌ Payment link not found in SSE events");
        log.info("Payment link found: {}", paymentLink);

        log.info("Opening payment link for OTP entry...");
        String randomOtp = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        log.info("Generated random OTP: {}", randomOtp);
        try (RazorpayCheckoutOtpPage checkout = new RazorpayCheckoutOtpPage(CloudConfig.getDeviceSerial())) {
            checkout.open(paymentLink)
                    .enterOtp(randomOtp)
                    .submit();
        }
        log.info("Payment flow automation completed!");

        log.info("Awaiting PAYOUT_ON_HOLD event after authentication...");
        List<String> linkEvents = oms.listenToOrderStream(orderNumber, "PAYOUT_ON_HOLD", Duration.ofSeconds(65));

        // After a successful card payment, the payout is held until the tutor accepts
        // their onboarding invite — that's a separate web flow, not part of this journey.
        assertTrue(
                linkEvents.stream().anyMatch(e -> e.contains("PAYOUT_ON_HOLD")),
                "Expected PAYOUT_ON_HOLD event in SSE stream after authentication, got: " + linkEvents
        );

        log.info("Step 8 Complete: PAYOUT_ON_HOLD event received");
    }

    // precondition: order_number from step7 is available and the limit was reserved in step6.
    // steps: Poll GET /order-status/{orderNumber} until status=AWAITING_PAYMENT or timeout (5 attempts); assert HTTP 200.
    // expected: Order status is returned and is non-null — order sits in AWAITING_PAYMENT while the reserved amount is held.
    @Test(groups = "tuition-payer", dependsOnMethods = "step6_reserveLimit")
    @TestCaseId("PO-17266")
    public void step8_pollOrderStatus_awaitingPayment() {
        Response r = oms.pollOrderStatus(orderNumber, "AWAITING_PAYMENT", 5);

        assertStatus(r, 200, "GET", Routes.OMS_ORDER_STATUS.kongUrl(orderNumber));

        OrderStatusResponse statusResponse = r.as(OrderStatusResponse.class);
        assertEquals(statusResponse.getData().getStatus(), "AWAITING_PAYMENT",
                "Order should be AWAITING_PAYMENT right after reserve, before payment is completed");

        log.info("Order status after creation: {}", statusResponse.getData().getStatus());
    }

    // precondition: payment_intent_id and reserve_id from step6; payout reached PAYOUT_ON_HOLD in step8 (tutor acceptance + payout + temporal update already happened server-side).
    // steps: Call POST /limits/consume with payment_intent_id and reserve_id (Stage 3B final step: moves the reserved amount to CONSUMED).
    // expected: HTTP 200 — reserved limit is finally consumed and no longer held as "reserved".
    @Test(groups = "tuition-payer", dependsOnMethods = "step8_completePaymentAndAwaitLink")
    @TestCaseId("PO-17267")
    public void step9_consumeReservedLimit_happyPath() {
        Response r = csvc.consumeLimit(LimitActionRequest.from(paymentIntentId, reserveId));

        assertStatus(r, 200, "POST", Routes.CSVC_LIMITS_CONSUME.getPath());

        log.info("Limit consumed successfully");
    }

    // precondition: payee_display_id and fee breakdown already available from step3/step4 (reuses the same payee, builds its own fresh payment_intent + order to avoid colliding with the happy-path reservation).
    // steps: Generate a fresh payment intent, create its order, reserve the limit for it, then call POST /limits/release with the same payment_intent_id and reserve_id (Stage 3A: REJECT — simulates the automatic release that happens when the tutor rejects the payment).
    // expected: Reserve returns HTTP 200 with a reserve_id; release then returns HTTP 200 — the reserved amount is restored to both student and tutor limits.
    @Test(groups = "tuition-payer", dependsOnMethods = "step4_getPaymentSummary")
    @TestCaseId("PO-17282")
    public void step10_rejectFlow_reserveThenReleaseLimit() {
        Response intentResp = csvc.generatePaymentIntent(
                GeneratePaymentIntentRequest.from(payeeDisplayId, paymentFee, UUID.randomUUID().toString())
        );
        assertStatus(intentResp, 200, "POST", Routes.CSVC_PAYMENT_INTENT.getPath());
        String rejectPaymentIntentId = intentResp.as(PaymentIntentResponse.class).getData().getPaymentIntentId();
        assertNotNull(rejectPaymentIntentId, "payment_intent_id should be returned for the reject-flow intent");

        TuitionTestData.Payment paymentData = TuitionTestData.get().getPayment();
        CreateTuitionOrderRequest.Payment cardPayment = CreateTuitionOrderRequest.Payment.cardPayment(
                CreateTuitionOrderRequest.CardDetails.of(
                        paymentData.getCardNumber(), paymentData.getExpiryMonth(), paymentData.getExpiryYear(),
                        paymentData.getCvv(), paymentData.getCardHolderName(), paymentData.getCardType())
        );
        Response orderResp = oms.createTuitionOrder(
                CreateTuitionOrderRequest.cardOrder(
                        "TUITION", rejectPaymentIntentId, cardPayment, 0,
                        CreateTuitionOrderRequest.AmountBreakdown.listFrom(paymentFee))
        );
        assertStatus(orderResp, 200, "POST", Routes.OMS_ORDERS.getPath());
        String rejectOrderNumber = orderResp.as(CreateTuitionOrderResponse.class).getData().getOrderNumber();
        assertNotNull(rejectOrderNumber, "order_number should be returned for the reject-flow order");

        Response reserveResp = csvc.reserveLimit(ReserveLimitRequest.from(rejectPaymentIntentId, rejectOrderNumber));
        assertStatus(reserveResp, 200, "POST", Routes.CSVC_LIMIT_RESERVE.getPath());
        String rejectReserveId = reserveResp.as(ReserveLimitResponse.class).getData().getReserveId();
        assertNotNull(rejectReserveId, "reserve_id should be returned for the reject-flow reservation");
        log.info("Reject-flow limit reserved: reserve_id={}", rejectReserveId);

        Response releaseResp = csvc.releaseLimit(LimitActionRequest.from(rejectPaymentIntentId, rejectReserveId));
        assertStatus(releaseResp, 200, "POST", Routes.CSVC_LIMITS_RELEASE.getPath());
        log.info("Reject-flow limit released: reserve_id={}", rejectReserveId);
    }

    private static TuitionTestData.InvalidCases invalidCases() {
        return TuitionTestData.get().getInvalidCases();
    }

    private static CreateTuitionOrderRequest.Payment dummyCardPayment() {
        TuitionTestData.DummyCard d = invalidCases().getDummyCard();
        return CreateTuitionOrderRequest.Payment.cardPayment(
                CreateTuitionOrderRequest.CardDetails.of(
                        d.getCardNumber(), d.getExpiryMonth(), d.getExpiryYear(),
                        d.getCvv(), d.getCardHolderName(), d.getCardType())
        );
    }

    private static List<CreateTuitionOrderRequest.AmountBreakdown> dummyAmountBreakdown() {
        TuitionTestData.DummyAmountBreakdown d = invalidCases().getDummyAmountBreakdown();
        return CreateTuitionOrderRequest.AmountBreakdown.listFrom(
                d.getTotalAmount(), d.getConvenienceFee(), d.getGstConvenienceFee(), d.getTotalPayable());
    }

    // precondition: Payer is logged in.
    // steps: Call POST /payee/basic-info with a bogus payment_option value.
    // expected: HTTP 400 with error code err-payee-basic-info.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17268")
    public void error_payeeBasicInfo_invalidPaymentOption_badRequest() {
        Response r = csvc.getPayeeBasicInfo(
                PayeeBasicInfoRequest.forVpa(invalidCases().getInvalidPaymentOption(), invalidCases().getWellFormedVpa())
        );

        assertEquals(r.statusCode(), 400,
                "Invalid payment_option should return 400, got: " + r.statusCode()
                        + " body=" + r.asString());

        log.info("Error case: payee/basic-info with bogus payment_option → 400");
    }

    // precondition: Payer is logged in.
    // steps: Call POST /payee/basic-info with a malformed VPA string.
    // expected: HTTP 400 with error code err-payee-basic-info.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17269")
    public void error_payeeBasicInfo_invalidVpa_checkFailure() {
        Response r = csvc.getPayeeBasicInfo(
                PayeeBasicInfoRequest.forVpa("VPA", invalidCases().getInvalidVpa())
        );

        assertEquals(r.statusCode(), 400,
                "Invalid VPA should return 400, got: " + r.statusCode()
                        + " body=" + r.asString());

        log.info("Error case: payee/basic-info with invalid VPA → 400");
    }

    // precondition: payout_reference_id from step2 must already be set (skips with a warning otherwise).
    // steps: Call POST /payee/verification with a malformed PAN (wrong format/length).
    // expected: HTTP 400.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17270")
    public void error_payeeVerification_malformedPan_badRequest() {
        if (payoutReferenceId == null) {
            log.warn("Skipping — no payoutReferenceId (run step2_getPayeeBasicInfo first)");
            return;
        }

        Response r = csvc.verifyPayee(
                VerifyPayeeRequestDto.from(
                        invalidCases().getMalformedPan(), invalidCases().getGenericMobile(), payoutReferenceId)
        );

        assertEquals(r.statusCode(), 400,
                "Malformed PAN should return 400, got: " + r.statusCode()
                        + " body=" + r.asString());

        log.info("Error case: payee/verification with malformed PAN → 400");
    }

    // precondition: payout_reference_id from step2 must already be set (skips with a warning otherwise).
    // steps: Call POST /payee/verification with a well-formed but non-existent/unresolvable PAN.
    // expected: HTTP 200 (soft failure) with is_success=false and is_pan_valid=false.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17271")
    public void error_payeeVerification_unresolvablePan_checkFailure() {
        if (payoutReferenceId == null) {
            log.warn("Skipping — no payoutReferenceId (run step2_getPayeeBasicInfo first)");
            return;
        }

        Response r = csvc.verifyPayee(
                VerifyPayeeRequestDto.from(
                        invalidCases().getUnresolvablePan(), invalidCases().getGenericMobile(), payoutReferenceId)
        );

        assertStatus(r, 200, "POST", Routes.CSVC_PAYEE_VERIFICATION.getPath());

        VerifyPayeeResponse response = r.as(VerifyPayeeResponse.class);
        assertFalse(response.isSuccess(), "Unresolvable PAN should be a soft failure (is_success:false)");
        assertFalse(response.getData().isPanValid(), "is_pan_valid should be false for an unresolvable PAN");

        log.info("Error case: payee/verification with unresolvable PAN → soft failure, is_pan_valid=false");
    }

    // precondition: payout_reference_id from step2 must already be set (skips with a warning otherwise).
    // steps: Call POST /payee/verification up to 3 times in rapid succession using a known rate-limit-triggering PAN/mobile pair.
    // expected: At least one of the rapid calls returns HTTP 429.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17272")
    public void error_payeeVerification_rateLimited() {
        if (payoutReferenceId == null) {
            log.warn("Skipping — no payoutReferenceId (run step2_getPayeeBasicInfo first)");
            return;
        }

        int attempts = 3;
        Response last = null;
        boolean sawRateLimit = false;
        for (int i = 0; i < attempts; i++) {
            last = csvc.verifyPayee(
                    VerifyPayeeRequestDto.from(
                            invalidCases().getRateLimitSamplePan(),
                            invalidCases().getRateLimitSampleMobile(),
                            payoutReferenceId)
            );
            if (last.statusCode() == 429) {
                sawRateLimit = true;
                break;
            }
        }

        assertTrue(sawRateLimit,
                "Expected a 429 within " + attempts + " rapid calls, last status was: "
                        + (last != null ? last.statusCode() : "null"));

        log.info("Error case: payee/verification rate limit after {} rapid calls → 429", attempts);
    }

    // precondition: payee_display_id from step3 must already be set (skips with a warning otherwise).
    // steps: Call GET /payment/summary with amount="abc" (non-numeric).
    // expected: HTTP 400.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17273")
    public void error_paymentSummary_nonNumericAmount_badRequest() {
        if (payeeDisplayId == null) {
            log.warn("Skipping — no payeeDisplayId (run step3_verifyPayeeInfo first)");
            return;
        }

        Response r = csvc.getPaymentSummaryRaw(
                Map.of("payee_display_id", payeeDisplayId, "amount", "abc")
        );

        assertEquals(r.statusCode(), 400,
                "Non-numeric amount should return 400, got: " + r.statusCode()
                        + " body=" + r.asString());

        log.info("Error case: payment summary with non-numeric amount → 400");
    }

    // precondition: Payer is logged in.
    // steps: Call GET /payment/summary with amount but no payee_display_id.
    // expected: HTTP 400 with error code err-payment-summary.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17274")
    public void error_paymentSummary_missingPayeeId_badRequest() {
        Response r = csvc.getPaymentSummaryRaw(
                Map.of("amount", TuitionTestData.get().getPayment().getAmount()));

        assertEquals(r.statusCode(), 400,
                "Missing payee_display_id should return 400, got: " + r.statusCode()
                        + " body=" + r.asString());

        log.info("Error case: payment summary with no payee_display_id → 400");
    }

    // precondition: Payer is logged in. (No documented error case for this endpoint — 400 inferred from sibling C2C endpoints.)
    // steps: Call POST /payee/payment-intent with only an idempotency_key (no payee/amount fields).
    // expected: HTTP 400 with error code err-payee-create-payment-intent.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17275")
    public void error_generatePaymentIntent_missingRequiredFields() {
        // No documented error case for this endpoint in the Postman contract — 400 is inferred from sibling C2C endpoints.
        Response r = csvc.generatePaymentIntent(
                GeneratePaymentIntentRequest.withIdempotencyKeyOnly(UUID.randomUUID().toString())
        );

        assertEquals(r.statusCode(), 400,
                "Missing total_payable/total_amount/payee_display_id should return 400, got: "
                        + r.statusCode() + " body=" + r.asString());

        log.info("Error case: payment-intent with no payee/amount fields → 400 (inferred, not documented anywhere)");
    }

    // precondition: Payer is logged in.
    // steps: Call POST /marketplace/orders with a completely empty body.
    // expected: HTTP 400 with error_code=INVALID_PARAMETERS.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17276")
    public void error_createOrder_invalidParameters_emptyBody() {
        Response r = oms.createTuitionOrder(CreateTuitionOrderRequest.empty());

        assertEquals(r.statusCode(), 400,
                "Empty body should return 400, got: " + r.statusCode());
        assertEquals(r.jsonPath().getString("error_code"), "INVALID_PARAMETERS");

        log.info("Error case: create order with no fields → 400 INVALID_PARAMETERS");
    }

    // precondition: Payer is logged in.
    // steps: Call POST /marketplace/orders with order_type="RENTAL" and dummy card/amount data.
    // expected: HTTP 400 with error_code=ORDER_TYPE_UNSUPPORTED.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17277")
    public void error_createOrder_orderTypeUnsupported_rental() {
        Response r = oms.createTuitionOrder(
                CreateTuitionOrderRequest.cardOrder(
                        "RENTAL", "pi_dummy_for_order_type_check", dummyCardPayment(), 0, dummyAmountBreakdown())
        );

        assertEquals(r.statusCode(), 400,
                "order_type=RENTAL should return 400, got: " + r.statusCode());
        assertEquals(r.jsonPath().getString("error_code"), "ORDER_TYPE_UNSUPPORTED");

        log.info("Error case: create order with order_type=RENTAL → 400 ORDER_TYPE_UNSUPPORTED");
    }

    // precondition: Payer is logged in.
    // steps: Call POST /marketplace/orders with a payment_intent_id that was never issued.
    // expected: HTTP 400 with error_code=PAYMENT_INTENT_INVALID.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17278")
    public void error_createOrder_paymentIntentInvalid() {
        Response r = oms.createTuitionOrder(
                CreateTuitionOrderRequest.cardOrder(
                        "TUITION", invalidCases().getUnknownPaymentIntentId(), dummyCardPayment(), 0, dummyAmountBreakdown())
        );

        assertEquals(r.statusCode(), 400,
                "Unknown payment_intent_id should return 400, got: " + r.statusCode());
        assertEquals(r.jsonPath().getString("error_code"), "PAYMENT_INTENT_INVALID");

        log.info("Error case: create order with unknown payment_intent_id → 400 PAYMENT_INTENT_INVALID");
    }

    // precondition: None — deliberately uses a fresh OmsService with no auth token attached.
    // steps: Call POST /marketplace/orders without an Authorization header.
    // expected: HTTP 401.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17279")
    public void error_createOrder_unauthorized_noToken() {
        OmsService unauthOms = new OmsService();

        Response r = unauthOms.createTuitionOrder(
                CreateTuitionOrderRequest.cardOrder(
                        "TUITION", "pi_dummy", dummyCardPayment(), 0, dummyAmountBreakdown())
        );

        assertEquals(r.statusCode(), 401,
                "No auth token should return 401, got: " + r.statusCode());

        log.info("Error case: create order with no auth token → 401 UNAUTHORIZED");
    }

    // precondition: paymentIntentId from step5 must already be set (skips with a warning otherwise).
    // steps: Call POST /marketplace/orders with an amount_breakdown whose total_payable (1) doesn't match the real fee from the payment intent.
    // expected: HTTP 400 with error_code=AMOUNT_MISMATCH.
    @Test(groups = "tuition-payer-error")
    @TestCaseId("PO-17280")
    public void error_createOrder_amountMismatch() {
        if (paymentIntentId == null) {
            log.warn("Skipping — no paymentIntentId (run step5_generatePaymentIntent first)");
            return;
        }

        List<CreateTuitionOrderRequest.AmountBreakdown> mismatchedBreakdown =
                CreateTuitionOrderRequest.AmountBreakdown.listFrom(
                        String.valueOf(paymentFee.getTotalAmount()),
                        String.valueOf(paymentFee.getConvenienceFee()),
                        String.valueOf(paymentFee.getGstConvenienceFee()),
                        "1");

        Response r = oms.createTuitionOrder(
                CreateTuitionOrderRequest.cardOrder(
                        "TUITION", paymentIntentId, dummyCardPayment(), 0, mismatchedBreakdown)
        );

        assertEquals(r.statusCode(), 400,
                "Mismatched total_payable should return 400, got: " + r.statusCode());
        assertEquals(r.jsonPath().getString("error_code"), "AMOUNT_MISMATCH");

        log.info("Error case: create order with mismatched amount_breakdown → 400 AMOUNT_MISMATCH");
    }
}
