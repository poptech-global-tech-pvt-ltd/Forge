package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

/**
 * Service wrapper for /v2/catalogue/* endpoints.
 *
 * Auth per request (from new collections):
 *
 *   getCreditCardBillers()               → X-Userid: {{user_id}}
 *   getCreditCardBillerInputFields()     → X-Userid: 019a97c6 (hardcoded in CC collection)
 *   getMobilePostpaidBillers()           → X-Userid: {{user_id}}
 *   getMobilePostpaidBillerInputFields() → X-Userid: {{user_id}}
 */
public class CatalogueService extends RcbpBaseService {

    // ── Credit card catalogue ─────────────────────────────────────────────────

    /**
     * GET /v2/catalogue/billers?category=creditcard
     * Postman: "list cc billers" — X-Userid: {{user_id}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.groupedBillers is not empty
     *   - no error property
     *   - Extracts biller_id (prefers YESB00000NAT8U, falls back to first)
     */
    public Response getCreditCardBillers() {
        return buildSpecWithUserId()
                .queryParam("category", "creditcard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());
    }

    /**
     * GET /v2/catalogue/billers/{billerId}?category=creditcard
     * Postman: "fetch input fields for billers" — X-Userid: 019a97c6 (hardcoded)
     *
     * Postman test assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.inputFields is not empty
     *   - no error property
     *
     * @param billerId "YESB00000NAT8U" (used in collection)
     */
    public Response getCreditCardBillerInputFields(String billerId) {
        return buildSpecWithCcInputId()
                .queryParam("category", "creditcard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + billerId);
    }

    // ── Mobile postpaid catalogue ─────────────────────────────────────────────

    /**
     * GET /v2/catalogue/billers?category=mobilepostpaid
     * Postman: "list postpaid billers" — X-Userid: {{user_id}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.groupedBillers is not empty
     *   - data.groupedBillers[0].billers is not empty
     *   - Extracts biller_id (prefers VODA00000NAT96, falls back to first)
     */
    public Response getMobilePostpaidBillers() {
        return buildSpecWithUserId()
                .queryParam("category", "mobilepostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());
    }

    /**
     * GET /v2/catalogue/billers/{billerId}?category=mobilepostpaid
     * Postman: "customer input fields for biller" — X-Userid: {{user_id}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.inputFields is not empty
     *   - Extracts input_fields (JSON array) and input_field_name
     *
     * @param billerId "VODA00000NAT96" (used in collection)
     */
    public Response getMobilePostpaidBillerInputFields(String billerId) {
        return buildSpecWithUserId()
                .queryParam("category", "mobilepostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + billerId);
    }
}
