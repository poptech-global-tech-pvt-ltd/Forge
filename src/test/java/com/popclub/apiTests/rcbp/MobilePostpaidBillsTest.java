package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.dto.ConfirmPaymentRequestDto;
import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.dto.InitiatePaymentRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.BillsService;
import com.popclub.api.rcbp.impl.CatalogueService;
import com.popclub.api.rcbp.impl.PaymentService;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import com.popclub.api.rcbp.impl.RcbpConfigManager;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;

public class MobilePostpaidBillsTest {

    private static final Logger log = LoggerFactory.getLogger(MobilePostpaidBillsTest.class);

    private static final String INPUT_FIELDS_BILLER_ID = "VODA00000NAT96";

    private String extractedBillerId;
    private String extractedInputFieldName;
    private long   extractedReferenceId;
    private long   extractedBillId;
    private double extractedBillAmount;
    private String extractedOrderNumber;

    private CatalogueService catalogueService;
    private BillsService     billsService;
    private PaymentService   paymentService;

    @BeforeClass
    public void setup() {
        catalogueService = new CatalogueService();
        billsService     = new BillsService();
        paymentService   = new PaymentService();
    }

    @Test(description = "GET /catalogue/billers?category=mobilepostpaid returns 200",
          groups = "rcbp-mobile-postpaid")
    public void getMobilePostpaidBillers_returns200() {
        log.info("Running: getMobilePostpaidBillers_returns200");

        Response response = catalogueService.getMobilePostpaidBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=mobilepostpaid");
    }

    @Test(description = "GET /catalogue/billers?category=mobilepostpaid — is_success=true, billers not empty; extracts biller_id",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidBillers_returns200")
    public void getMobilePostpaidBillers_validResponse() {
        log.info("Running: getMobilePostpaidBillers_validResponse");

        Response response = catalogueService.getMobilePostpaidBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=mobilepostpaid");

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        List<?> groupedBillers = response.jsonPath().getList("data.groupedBillers");
        assertNotNull(groupedBillers, "data.groupedBillers must not be null");
        assertFalse(groupedBillers.isEmpty(), "data.groupedBillers must not be empty");

        List<?> firstGroupBillers = response.jsonPath().getList("data.groupedBillers[0].billers");
        assertNotNull(firstGroupBillers, "data.groupedBillers[0].billers must not be null");
        assertFalse(firstGroupBillers.isEmpty(), "data.groupedBillers[0].billers must not be empty");

        List<String> billerIds = response.jsonPath().getList("data.groupedBillers[0].billers.billerId");
        extractedBillerId = (billerIds != null && billerIds.contains(INPUT_FIELDS_BILLER_ID))
                ? INPUT_FIELDS_BILLER_ID
                : (billerIds != null && !billerIds.isEmpty() ? billerIds.get(0) : INPUT_FIELDS_BILLER_ID);

        log.info("Extracted biller_id: {}", extractedBillerId);
    }

    @Test(description = "GET /catalogue/billers/VODA00000NAT96?category=mobilepostpaid returns 200",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidBillers_validResponse")
    public void getMobilePostpaidInputFields_returns200() {
        log.info("Running: getMobilePostpaidInputFields_returns200");

        Response response = catalogueService.getMobilePostpaidBillerInputFields(INPUT_FIELDS_BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INPUT_FIELDS_BILLER_ID + "?category=mobilepostpaid");
    }

    @Test(description = "GET /catalogue/billers/VODA00000NAT96 — is_success=true, inputFields not empty; extracts input_field_name",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidInputFields_returns200")
    public void getMobilePostpaidInputFields_validResponse() {
        log.info("Running: getMobilePostpaidInputFields_validResponse");

        Response response = catalogueService.getMobilePostpaidBillerInputFields(INPUT_FIELDS_BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INPUT_FIELDS_BILLER_ID + "?category=mobilepostpaid");

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        List<?> inputFields = response.jsonPath().getList("data.inputFields");
        assertNotNull(inputFields, "data.inputFields must not be null");
        assertFalse(inputFields.isEmpty(), "data.inputFields must not be empty");

        extractedInputFieldName = response.jsonPath().getString("data.inputFields[0].paramName");
        assertNotNull(extractedInputFieldName, "data.inputFields[0].paramName must be present");

        log.info("Extracted input_field_name: {}", extractedInputFieldName);
    }

    @Test(description = "POST /bills/fetch mobilepostpaid — returns 200",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidInputFields_validResponse")
    public void fetchMobilePostpaidBill_returns200() {
        log.info("Running: fetchMobilePostpaidBill_returns200 | billerId={} paramName={}",
                extractedBillerId, extractedInputFieldName);

        if (extractedBillerId == null || extractedInputFieldName == null) {
            throw new SkipException("Skipping — extractedBillerId/inputFieldName not set (input fields step failed)");
        }

        String phone = RcbpConfigManager.getPhone();
        FetchBillRequestDto request = FetchBillRequestDto.forMobilePostpaid(
                extractedBillerId,
                Collections.singletonList(
                        new FetchBillRequestDto.CustomerParam(extractedInputFieldName, phone))
        );

        Response response = billsService.fetchMobilePostpaidBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());
    }

    @Test(description = "POST /bills/fetch mobilepostpaid — is_success=true, numeric ids, amount>0; extracts bill details",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "fetchMobilePostpaidBill_returns200")
    public void fetchMobilePostpaidBill_validResponse() {
        log.info("Running: fetchMobilePostpaidBill_validResponse");

        if (extractedBillerId == null || extractedInputFieldName == null) {
            throw new SkipException("Skipping — extractedBillerId/inputFieldName not set (input fields step failed)");
        }

        String phone = RcbpConfigManager.getPhone();
        FetchBillRequestDto request = FetchBillRequestDto.forMobilePostpaid(
                extractedBillerId,
                Collections.singletonList(
                        new FetchBillRequestDto.CustomerParam(extractedInputFieldName, phone))
        );

        Response response = billsService.fetchMobilePostpaidBill(request);

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

        extractedReferenceId = ((Number) response.jsonPath().get("data.referenceId")).longValue();
        extractedBillId      = ((Number) response.jsonPath().get("data.billID")).longValue();
        extractedBillAmount  = totalAmount;

        log.info("Extracted referenceId={}, billId={}, billAmount={}",
                extractedReferenceId, extractedBillId, extractedBillAmount);
    }

    @Test(description = "POST /payment/initiate mobilepostpaid — returns 200",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "fetchMobilePostpaidBill_validResponse")
    public void initiatePostpaidPayment_returns200() {
        log.info("Running: initiatePostpaidPayment_returns200 | referenceId={} billId={} amount={}",
                extractedReferenceId, extractedBillId, extractedBillAmount);

        if (extractedBillerId == null) {
            throw new SkipException("Skipping — bill fetch step did not complete");
        }

        InitiatePaymentRequestDto request = InitiatePaymentRequestDto.forMobilePostpaid(
                extractedReferenceId, extractedBillId, extractedBillAmount);

        Response response = paymentService.initiatePostpaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    @Test(description = "POST /payment/initiate mobilepostpaid — is_success=true, orderNumber /^BP/, extracts order_number",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "initiatePostpaidPayment_returns200")
    public void initiatePostpaidPayment_validResponse() {
        log.info("Running: initiatePostpaidPayment_validResponse");

        if (extractedBillerId == null) {
            throw new SkipException("Skipping — bill fetch step did not complete");
        }

        InitiatePaymentRequestDto request = InitiatePaymentRequestDto.forMobilePostpaid(
                extractedReferenceId, extractedBillId, extractedBillAmount);

        Response response = paymentService.initiatePostpaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_INITIATE.getPath());

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        assertNotNull(response.jsonPath().get("data.orderNumber"),    "data.orderNumber must be present");
        assertNotNull(response.jsonPath().get("data.gatewayOrderId"), "data.gatewayOrderId must be present");

        String orderNumber = response.jsonPath().getString("data.orderNumber");
        assertTrue(orderNumber.startsWith("BP"),
                "data.orderNumber must start with 'BP', got: " + orderNumber);

        extractedOrderNumber = orderNumber;
        log.info("Extracted order_number: {}", extractedOrderNumber);
    }

    @Test(description = "POST /payment/confirm mobilepostpaid — returns 200",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "initiatePostpaidPayment_validResponse")
    public void confirmPostpaidPayment_returns200() {
        log.info("Running: confirmPostpaidPayment_returns200 | orderNumber={}", extractedOrderNumber);

        if (extractedOrderNumber == null) {
            throw new SkipException("Skipping — extractedOrderNumber not set (initiate step failed)");
        }

        String paymentReferenceId = "pay_" + System.currentTimeMillis();
        String phone = RcbpConfigManager.getPhone();

        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.forMobilePostpaid(
                extractedOrderNumber, paymentReferenceId, extractedBillAmount, phone);

        Response response = paymentService.confirmPostpaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_CONFIRM.getPath());
    }

    @Test(description = "POST /payment/confirm mobilepostpaid — is_success=true, status=Processing",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "confirmPostpaidPayment_returns200")
    public void confirmPostpaidPayment_validResponse() {
        log.info("Running: confirmPostpaidPayment_validResponse");

        if (extractedOrderNumber == null) {
            throw new SkipException("Skipping — extractedOrderNumber not set (initiate step failed)");
        }

        String paymentReferenceId = "pay_" + System.currentTimeMillis();
        String phone = RcbpConfigManager.getPhone();

        ConfirmPaymentRequestDto request = ConfirmPaymentRequestDto.forMobilePostpaid(
                extractedOrderNumber, paymentReferenceId, extractedBillAmount, phone);

        Response response = paymentService.confirmPostpaidPayment(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.PAYMENT_CONFIRM.getPath());

        assertTrue(Boolean.TRUE.equals(response.jsonPath().get("is_success")),
                "is_success must be true");

        assertNotNull(response.jsonPath().get("data.status"),        "data.status must be present");
        assertNotNull(response.jsonPath().get("data.transactionId"), "data.transactionId must be present");

        assertEquals(response.jsonPath().getString("data.status"), "Processing",
                "data.status must be \"Processing\"");

        log.info("Confirm status: {}", response.jsonPath().getString("data.status"));
    }
}
