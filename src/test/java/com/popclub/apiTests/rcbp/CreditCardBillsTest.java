package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.dto.ConfirmPaymentRequestDto;
import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.dto.InitiatePaymentRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.BillsService;
import com.popclub.api.rcbp.impl.CatalogueService;
import com.popclub.api.rcbp.impl.PaymentService;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.*;

public class CreditCardBillsTest {

    private static final Logger log = LoggerFactory.getLogger(CreditCardBillsTest.class);

    private static final String INPUT_FIELDS_BILLER_ID = "YESB00000NAT8U";
    private static final String CC_MOBILE              = "9936122907";
    private static final String CC_LAST4               = "4562";
    private static final String CC_CONFIRM_MOBILE      = "7760362814";

    private String extractedBillerId;
    private long   extractedReferenceId;
    private long   extractedBillId;
    private double extractedBillAmount;
    private int    extractedCoinsUsed;
    private double extractedDiscountedAmount;
    private String extractedOrderNumber;
    private String extractedGatewayOrderId;

    private CatalogueService catalogueService;
    private BillsService     billsService;
    private PaymentService   paymentService;

    @BeforeClass
    public void setup() {
        catalogueService = new CatalogueService();
        billsService     = new BillsService();
        paymentService   = new PaymentService();
    }

    @Test(description = "GET /catalogue/billers?category=creditcard returns 200",
          groups = "rcbp-credit-card")
    public void getCreditCardBillers_returns200() {
        log.info("Running: getCreditCardBillers_returns200");

        Response response = catalogueService.getCreditCardBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=creditcard");
    }

    @Test(description = "GET /catalogue/billers?category=creditcard — is_success=true, groupedBillers not empty, no error; extracts biller_id",
          groups = "rcbp-credit-card",
          dependsOnMethods = "getCreditCardBillers_returns200")
    public void getCreditCardBillers_validResponse() {
        log.info("Running: getCreditCardBillers_validResponse");

        Response response = catalogueService.getCreditCardBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=creditcard");

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        List<?> groupedBillers = response.jsonPath().getList("data.groupedBillers");
        assertNotNull(groupedBillers, "data.groupedBillers must not be null");
        assertFalse(groupedBillers.isEmpty(), "data.groupedBillers must not be empty");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        List<String> billerIds = response.jsonPath().getList("data.groupedBillers.billers.billerId.flatten()");
        extractedBillerId = (billerIds != null && billerIds.contains(INPUT_FIELDS_BILLER_ID))
                ? INPUT_FIELDS_BILLER_ID
                : (billerIds != null && !billerIds.isEmpty() ? billerIds.get(0) : INPUT_FIELDS_BILLER_ID);

        log.info("Extracted biller_id: {}", extractedBillerId);
    }

    @Test(description = "GET /catalogue/billers/YESB00000NAT8U?category=creditcard returns 200",
          groups = "rcbp-credit-card",
          dependsOnMethods = "getCreditCardBillers_validResponse")
    public void getCreditCardBillerInputFields_returns200() {
        log.info("Running: getCreditCardBillerInputFields_returns200");

        Response response = catalogueService.getCreditCardBillerInputFields(INPUT_FIELDS_BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INPUT_FIELDS_BILLER_ID + "?category=creditcard");
    }

    @Test(description = "GET /catalogue/billers/YESB00000NAT8U — is_success=true, inputFields not empty, no error",
          groups = "rcbp-credit-card",
          dependsOnMethods = "getCreditCardBillerInputFields_returns200")
    public void getCreditCardBillerInputFields_validResponse() {
        log.info("Running: getCreditCardBillerInputFields_validResponse");

        Response response = catalogueService.getCreditCardBillerInputFields(INPUT_FIELDS_BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INPUT_FIELDS_BILLER_ID + "?category=creditcard");

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        List<?> inputFields = response.jsonPath().getList("data.inputFields");
        assertNotNull(inputFields, "data.inputFields must not be null");
        assertFalse(inputFields.isEmpty(), "data.inputFields must not be empty");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        log.info("inputFields count: {}", inputFields.size());
    }

    @Test(description = "POST /bills/fetch creditcard — with customerParams returns 200",
          groups = "rcbp-credit-card",
          dependsOnMethods = "getCreditCardBillerInputFields_validResponse")
    public void fetchCreditCardBill_returns200() {
        log.info("Running: fetchCreditCardBill_returns200 | billerId={}", extractedBillerId);

        if (extractedBillerId == null) {
            throw new SkipException("Skipping — extractedBillerId not set (list billers step failed)");
        }

        FetchBillRequestDto request = FetchBillRequestDto.forCreditCard(
                extractedBillerId,
                Arrays.asList(
                        new FetchBillRequestDto.CustomerParam("Registered Mobile Number", CC_MOBILE),
                        new FetchBillRequestDto.CustomerParam("Last 4 digits of Primary Credit Card Number", CC_LAST4)
                )
        );

        Response response = billsService.fetchCreditCardBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());
    }

    @Test(description = "POST /bills/fetch creditcard — is_success=true, numeric ids, amount>0, CC-specific fields, extracts ids",
          groups = "rcbp-credit-card",
          dependsOnMethods = "fetchCreditCardBill_returns200")
    public void fetchCreditCardBill_validResponse() {
        log.info("Running: fetchCreditCardBill_validResponse");

        if (extractedBillerId == null) {
            throw new SkipException("Skipping — extractedBillerId not set (list billers step failed)");
        }

        FetchBillRequestDto request = FetchBillRequestDto.forCreditCard(
                extractedBillerId,
                Arrays.asList(
                        new FetchBillRequestDto.CustomerParam("Registered Mobile Number", CC_MOBILE),
                        new FetchBillRequestDto.CustomerParam("Last 4 digits of Primary Credit Card Number", CC_LAST4)
                )
        );

        Response response = billsService.fetchCreditCardBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        assertNotNull(response.jsonPath().get("data.referenceId"), "data.referenceId must be present");
        assertNotNull(response.jsonPath().get("data.billID"),      "data.billID must be present");

        assertNotNull(response.jsonPath().get("data.totalDetails.totalAmount"),
                "data.totalDetails.totalAmount must be present");

        double totalAmount = Double.parseDouble(
                response.jsonPath().getString("data.totalDetails.totalAmount"));
        assertTrue(totalAmount > 0, "totalAmount must be greater than 0");

        assertEquals(response.jsonPath().getString("data.exactness"), "Any",
                "data.exactness must be \"Any\"");

        assertNotNull(response.jsonPath().get("data.isMinDueAvailable"),
                "data.isMinDueAvailable must be present");
        assertNotNull(response.jsonPath().get("data.payableAmounts"),
                "data.payableAmounts must be present");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        extractedReferenceId      = ((Number) response.jsonPath().get("data.referenceId")).longValue();
        extractedBillId           = ((Number) response.jsonPath().get("data.billID")).longValue();
        extractedBillAmount       = totalAmount;
        Number coinsUsedVal       = response.jsonPath().get("data.totalDetailsRS.coinsUsed");
        extractedCoinsUsed        = coinsUsedVal != null ? coinsUsedVal.intValue() : 0;
        String discAmountStr      = response.jsonPath().getString("data.totalDetailsRS.DiscountedAmount");
        extractedDiscountedAmount = (discAmountStr != null)
                ? Double.parseDouble(discAmountStr)
                : extractedBillAmount;

        log.info("Extracted referenceId={}, billId={}, billAmount={}, coinsUsed={}, discountedAmount={}",
                extractedReferenceId, extractedBillId, extractedBillAmount,
                extractedCoinsUsed, extractedDiscountedAmount);
    }

    @Test(description = "POST /payment/initiate creditcard returns 200",
          groups = "rcbp-credit-card",
          dependsOnMethods = "fetchCreditCardBill_validResponse")
    public void initiateCreditCardPayment_returns200() {
        log.info("Running: initiateCreditCardPayment_returns200 | referenceId={} billId={} amount={}",
                extractedReferenceId, extractedBillId, extractedBillAmount);

        if (extractedBillerId == null) {
            throw new SkipException("Skipping — bill fetch step did not complete");
        }

        InitiatePaymentRequestDto request = InitiatePaymentRequestDto.forCreditCard(
                extractedReferenceId, extractedBillId, extractedBillAmount, extractedCoinsUsed);

        Response response = paymentService.initiateCreditCardPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    @Test(description = "POST /payment/initiate creditcard — is_success=true, message correct, orderNumber /^BP/, extracts order",
          groups = "rcbp-credit-card",
          dependsOnMethods = "initiateCreditCardPayment_returns200")
    public void initiateCreditCardPayment_validResponse() {
        log.info("Running: initiateCreditCardPayment_validResponse");

        if (extractedBillerId == null) {
            throw new SkipException("Skipping — bill fetch step did not complete");
        }

        InitiatePaymentRequestDto request = InitiatePaymentRequestDto.forCreditCard(
                extractedReferenceId, extractedBillId, extractedBillAmount, extractedCoinsUsed);

        Response response = paymentService.initiateCreditCardPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_INITIATE.getPath());

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        assertEquals(response.jsonPath().getString("message"), "payment initiated successfully!",
                "message must be \"payment initiated successfully!\"");

        String orderNumber = response.jsonPath().getString("data.orderNumber");
        assertNotNull(orderNumber, "data.orderNumber must be present");
        assertTrue(orderNumber.startsWith("BP"),
                "data.orderNumber must start with 'BP', got: " + orderNumber);

        String gatewayOrderId = response.jsonPath().getString("data.gatewayOrderId");
        assertNotNull(gatewayOrderId, "data.gatewayOrderId must be present");
        assertFalse(orderNumber.isEmpty(),    "data.orderNumber must not be empty");
        assertFalse(gatewayOrderId.isEmpty(), "data.gatewayOrderId must not be empty");

        assertNull(response.jsonPath().get("error"), "response must not have an error property");

        extractedOrderNumber    = orderNumber;
        extractedGatewayOrderId = gatewayOrderId;

        log.info("Extracted order_number: {}, gateway_order_id: {}",
                extractedOrderNumber, extractedGatewayOrderId);
    }

    @Test(description = "POST /payment/confirmation creditcard returns 200",
          groups = "rcbp-credit-card",
          dependsOnMethods = "initiateCreditCardPayment_validResponse")
    public void confirmCreditCardPayment_returns200() {
        log.info("Running: confirmCreditCardPayment_returns200 | orderNumber={}", extractedOrderNumber);

        if (extractedOrderNumber == null) {
            throw new SkipException("Skipping — extractedOrderNumber not set (initiate step failed)");
        }

        String paymentReferenceId = "pay_" + System.currentTimeMillis();

        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.forCreditCard(
                extractedOrderNumber,
                paymentReferenceId,
                extractedBillAmount,
                extractedDiscountedAmount,
                extractedCoinsUsed,
                CC_CONFIRM_MOBILE
        );

        Response response = paymentService.confirmCreditCardPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_CONFIRMATION.getPath());
    }
}
