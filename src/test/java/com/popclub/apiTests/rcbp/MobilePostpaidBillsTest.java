package com.popclub.apiTests.rcbp;

import com.popclub.api.rcbp.dto.FetchBillRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import com.popclub.api.rcbp.impl.BillsService;
import com.popclub.api.rcbp.impl.CatalogueService;
import com.popclub.api.rcbp.impl.RcbpBaseService;
import com.popclub.api.rcbp.impl.RcbpConfigManager;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Integration tests for Mobile Postpaid bill payment APIs.
 * Base URL: {{pop-rcbp-base}}
 *
 * The collection implements a chained flow — values extracted from each response
 * are passed into the next request:
 *
 *   1. GET /v2/catalogue/billers?category=mobilepostpaid
 *      Auth: X-Userid: {{userid}}
 *      → verify data is not empty
 *
 *   2. GET /v2/catalogue/billers/VODA00000NAT96?category=mobilePostpaid
 *      Auth: phone: {{phone}} header + X-Userid: {{userid}} (no Authorization)
 *      → verify data.inputFields is not empty
 *      → EXTRACT: data.billerId → used as billerId in step 3
 *      → EXTRACT: data.inputFields[0].paramName → used as customerParams[0].name in step 3
 *
 *   3. POST /v2/bills/fetch
 *      Auth: X-Userid: {{userid}}
 *      Body: billerId={{biller_id}}, category=mobilePostpaid,
 *            customerMobile={{phone}},
 *            customerParams=[{ name={{customer_param_name}}, value={{phone}} }]
 *      → verify data is not empty
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ NOT AVAILABLE IN COLLECTION — tests deliberately omitted:               │
 * │  • Error / negative scenarios                                           │
 * │  • Response field assertions beyond what Postman scripts assert         │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
public class MobilePostpaidBillsTest {

    private static final Logger log = LoggerFactory.getLogger(MobilePostpaidBillsTest.class);

    // Test data — from collection
    private static final String INPUT_FIELDS_BILLER_ID = "VODA00000NAT96";

    // Extracted from input fields response — used in fetch bill
    private String extractedBillerId;
    private String extractedParamName;

    private CatalogueService catalogueService;
    private BillsService     billsService;

    @BeforeClass
    public void setup() {
        catalogueService = new CatalogueService();
        billsService     = new BillsService();
    }

    // ── 1. GET billers ────────────────────────────────────────────────────────

    @Test(description = "GET /catalogue/billers?category=mobilepostpaid returns 200",
          groups = "rcbp-mobile-postpaid")
    public void getMobilePostpaidBillers_returns200() {
        log.info("Running: getMobilePostpaidBillers_returns200");

        Response response = catalogueService.getMobilePostpaidBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=mobilepostpaid");
    }

    @Test(description = "GET /catalogue/billers?category=mobilepostpaid — data is not empty",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidBillers_returns200")
    public void getMobilePostpaidBillers_dataNotEmpty() {
        log.info("Running: getMobilePostpaidBillers_dataNotEmpty");

        Response response = catalogueService.getMobilePostpaidBillers();

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "?category=mobilepostpaid");

        // Postman: pm.expect(jsonData.data).to.not.be.empty
        Object data = response.jsonPath().get("data");
        assertNotNull(data, "data must not be null in billers response");
    }

    // ── 2. GET biller input fields (phone header + X-Userid, no Authorization) ─

    @Test(description = "GET /catalogue/billers/VODA00000NAT96?category=mobilePostpaid returns 200 (phone + X-Userid auth)",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidBillers_dataNotEmpty")
    public void getMobilePostpaidInputFields_returns200() {
        log.info("Running: getMobilePostpaidInputFields_returns200");

        // Auth: phone header + X-Userid. No Authorization token.
        Response response = catalogueService.getMobilePostpaidBillerInputFields(INPUT_FIELDS_BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INPUT_FIELDS_BILLER_ID + "?category=mobilePostpaid");
    }

    @Test(description = "GET /catalogue/billers/VODA00000NAT96 — data.inputFields is not empty; extracts billerId and paramName",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidInputFields_returns200")
    public void getMobilePostpaidInputFields_extractsBillerIdAndParamName() {
        log.info("Running: getMobilePostpaidInputFields_extractsBillerIdAndParamName");

        Response response = catalogueService.getMobilePostpaidBillerInputFields(INPUT_FIELDS_BILLER_ID);

        RcbpBaseService.assertStatus(response, 200, "GET",
                RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + INPUT_FIELDS_BILLER_ID + "?category=mobilePostpaid");

        // Postman: pm.expect(jsonData.data.inputFields).to.not.be.empty
        List<?> inputFields = response.jsonPath().getList("data.inputFields");
        assertNotNull(inputFields, "data.inputFields must not be null");
        assertFalse(inputFields.isEmpty(), "data.inputFields must not be empty");

        // Postman extracts these and uses them in the fetch bill request
        extractedBillerId  = response.jsonPath().getString("data.billerId");
        extractedParamName = response.jsonPath().getString("data.inputFields[0].paramName");

        assertNotNull(extractedBillerId,  "data.billerId must be present in input fields response");
        assertNotNull(extractedParamName, "data.inputFields[0].paramName must be present");

        log.info("Extracted billerId: {}, paramName: {}", extractedBillerId, extractedParamName);
    }

    // ── 3. POST bills/fetch — mobile postpaid (chained) ──────────────────────

    @Test(description = "POST /bills/fetch mobilePostpaid — using billerId and paramName extracted from input fields; returns 200",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "getMobilePostpaidInputFields_extractsBillerIdAndParamName")
    public void fetchMobilePostpaidBill_withExtractedParams_returns200() {
        log.info("Running: fetchMobilePostpaidBill_withExtractedParams_returns200 | billerId={} paramName={}",
                extractedBillerId, extractedParamName);

        // Postman body: billerId={{biller_id}}, customerMobile={{phone}},
        //               customerParams=[{name={{customer_param_name}}, value={{phone}}}]
        String phone = RcbpConfigManager.getPhone();
        FetchBillRequestDto.CustomerParam param =
                new FetchBillRequestDto.CustomerParam(extractedParamName, phone);

        FetchBillRequestDto request = FetchBillRequestDto.forMobilePostpaid(
                extractedBillerId,
                phone,
                Collections.singletonList(param)
        );

        Response response = billsService.fetchMobilePostpaidBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());
    }

    @Test(description = "POST /bills/fetch mobilePostpaid — data is not empty",
          groups = "rcbp-mobile-postpaid",
          dependsOnMethods = "fetchMobilePostpaidBill_withExtractedParams_returns200")
    public void fetchMobilePostpaidBill_dataNotEmpty() {
        log.info("Running: fetchMobilePostpaidBill_dataNotEmpty");

        String phone = RcbpConfigManager.getPhone();
        FetchBillRequestDto.CustomerParam param =
                new FetchBillRequestDto.CustomerParam(extractedParamName, phone);

        FetchBillRequestDto request = FetchBillRequestDto.forMobilePostpaid(
                extractedBillerId,
                phone,
                Collections.singletonList(param)
        );

        Response response = billsService.fetchMobilePostpaidBill(request);

        RcbpBaseService.assertStatus(response, 200, "POST", RcbpRoutes.BILLS_FETCH.getPath());

        // Postman: pm.expect(jsonData.data).to.not.be.empty
        Object data = response.jsonPath().get("data");
        assertNotNull(data, "data must not be null in fetch bill response");
    }

    /*
     * ── What is NOT tested (not available in collection) ──────────────────────
     *
     * 1. Error scenarios (4xx/5xx): no negative examples in the collection.
     *
     * 2. Response field assertions beyond data non-null:
     *    Postman scripts only assert data is not empty.
     */
}
