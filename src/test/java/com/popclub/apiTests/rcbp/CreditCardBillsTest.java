package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.BillsService;
import com.popclub.api.rcbp.impl.CatalogueService;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * Integration tests for Credit Card bill payment APIs.
 *
 * Base URL: {{pop-rcbp-base}}
 * Auth: X-Userid: {{user_id}} (Authorization header is disabled in the collection)
 *
 * Flow:
 *   1. GET /v2/catalogue/billers?category=creditCard
 *      → verify data.groupedBillers[0].billers is not empty
 *      → verify ICIC00000NATSI biller is present in the list
 *
 *   2. GET /v2/catalogue/billers/ICIC00000NATSI?category=creditCard
 *      → verify data.inputFields is not empty
 *
 *   3. POST /v2/bills/fetch
 *      → body: billerId=ICIC00000NATSI, category=creditCard, amount=1, billId=458001
 *      → verify data is not empty
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ NOT AVAILABLE IN COLLECTION — tests deliberately omitted:               │
 * │  • customerParams in fetch bill body (commented out in collection)      │
 * │  • Response field-level assertions beyond what Postman scripts assert   │
 * │  • Error / negative scenarios                                           │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
public class CreditCardBillsTest {

    private static final Logger log = LoggerFactory.getLogger(CreditCardBillsTest.class);

    // Test data — taken directly from the Postman collection
    private static final String BILLER_ID = "ICIC00000NATSI";
    private static final String CATEGORY  = "creditCard";
    private static final int    AMOUNT    = 1;
    private static final long   BILL_ID   = 458001L;

    private CatalogueService catalogueService;
    private BillsService     billsService;

    @BeforeClass
    public void setup() {
        catalogueService = new CatalogueService();
        billsService     = new BillsService();
    }

    // ── 1. GET billers ────────────────────────────────────────────────────────

    @Test(description = "GET /catalogue/billers?category=creditCard returns 200",
          groups = "rcbp-credit-card")
    public void getCreditCardBillers_returns200() {
        log.info("Running: getCreditCardBillers_returns200");

        Response response = catalogueService.getCreditCardBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=creditCard");
    }

    @Test(description = "GET /catalogue/billers?category=creditCard — data.groupedBillers[0].billers is not empty",
          groups = "rcbp-credit-card",
          dependsOnMethods = "getCreditCardBillers_returns200")
    public void getCreditCardBillers_groupedBillersNotEmpty() {
        log.info("Running: getCreditCardBillers_groupedBillersNotEmpty");

        Response response = catalogueService.getCreditCardBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=creditCard");

        // Postman: pm.expect(jsonData.data.groupedBillers[0].billers).to.not.be.empty
        List<?> billers = response.jsonPath().getList("data.groupedBillers[0].billers");
        assertNotNull(billers, "data.groupedBillers[0].billers must not be null");
        assertFalse(billers.isEmpty(), "data.groupedBillers[0].billers must not be empty");

        log.info("Total billers in first group: {}", billers.size());
    }

    @Test(description = "GET /catalogue/billers?category=creditCard — ICIC00000NATSI biller is present",
          groups = "rcbp-credit-card",
          dependsOnMethods = "getCreditCardBillers_groupedBillersNotEmpty")
    public void getCreditCardBillers_iciciBillerPresent() {
        log.info("Running: getCreditCardBillers_iciciBillerPresent");

        Response response = catalogueService.getCreditCardBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=creditCard");

        // Postman: billers.find(b => b.billerId === "ICIC00000NATSI") — verifies biller exists
        List<String> billerIds = response.jsonPath().getList("data.groupedBillers[0].billers.billerId");
        assertNotNull(billerIds, "billerId list must not be null");
        assertTrue(billerIds.contains(BILLER_ID),
                "Expected biller '" + BILLER_ID + "' to be present in groupedBillers[0].billers");

        log.info("ICIC biller found in list");
    }

    // ── 2. GET biller input fields ────────────────────────────────────────────

    @Test(description = "GET /catalogue/billers/ICIC00000NATSI?category=creditCard returns 200",
          groups = "rcbp-credit-card")
    public void getCreditCardBillerInputFields_returns200() {
        log.info("Running: getCreditCardBillerInputFields_returns200");

        Response response = catalogueService.getCreditCardBillerInputFields(BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + BILLER_ID + "?category=creditCard");
    }

    @Test(description = "GET /catalogue/billers/ICIC00000NATSI — data.inputFields is not empty",
          groups = "rcbp-credit-card",
          dependsOnMethods = "getCreditCardBillerInputFields_returns200")
    public void getCreditCardBillerInputFields_inputFieldsNotEmpty() {
        log.info("Running: getCreditCardBillerInputFields_inputFieldsNotEmpty");

        Response response = catalogueService.getCreditCardBillerInputFields(BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + BILLER_ID + "?category=creditCard");

        // Postman: pm.expect(jsonData.data.inputFields).to.not.be.empty
        List<?> inputFields = response.jsonPath().getList("data.inputFields");
        assertNotNull(inputFields, "data.inputFields must not be null");
        assertFalse(inputFields.isEmpty(), "data.inputFields must not be empty");

        // Postman also extracts data.billerId — verify it matches the requested biller
        String returnedBillerId = response.jsonPath().getString("data.billerId");
        assertEquals(returnedBillerId, BILLER_ID,
                "data.billerId in response must match requested biller");

        log.info("inputFields count: {}, billerId: {}", inputFields.size(), returnedBillerId);
    }

    // ── 3. POST bills/fetch — credit card ────────────────────────────────────

    @Test(description = "POST /bills/fetch with billerId=ICIC00000NATSI, category=creditCard, amount=1, billId=458001 returns 200",
          groups = "rcbp-credit-card")
    public void fetchCreditCardBill_returns200() {
        log.info("Running: fetchCreditCardBill_returns200");

        FetchBillRequestDto request = FetchBillRequestDto.forCreditCard(BILLER_ID, AMOUNT, BILL_ID);
        Response response = billsService.fetchCreditCardBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());
    }

    @Test(description = "POST /bills/fetch credit card — data is not empty",
          groups = "rcbp-credit-card",
          dependsOnMethods = "fetchCreditCardBill_returns200")
    public void fetchCreditCardBill_dataNotEmpty() {
        log.info("Running: fetchCreditCardBill_dataNotEmpty");

        FetchBillRequestDto request = FetchBillRequestDto.forCreditCard(BILLER_ID, AMOUNT, BILL_ID);
        Response response = billsService.fetchCreditCardBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());

        // Postman: pm.expect(jsonData.data).to.not.be.empty
        Object data = response.jsonPath().get("data");
        assertNotNull(data, "data field must be present in fetch bill response");
    }

    /*
     * ── What is NOT tested (not available in collection) ──────────────────────
     *
     * 1. customerParams in fetch bill body:
     *    The block with "Registered Mobile Number" and "Last 4 digits of Credit Card
     *    Number" is commented out in the Postman request. The contract for this
     *    field is not confirmed.
     *
     * 2. Error scenarios (4xx/5xx):
     *    No negative-case examples in the collection.
     *
     * 3. Response field assertions beyond data non-null:
     *    Postman scripts only assert data is not empty — no field names specified.
     */
}
