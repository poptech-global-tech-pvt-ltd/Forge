package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

/**
 * Service wrapper for /v2/catalogue/* endpoints.
 *
 * Auth per request (from collection — Authorization is disabled everywhere):
 *
 *   getCreditCardBillers()            → X-Userid: {{user_id}}
 *   getCreditCardBillerInputFields()  → X-Userid: {{user_id}}
 *   getMobilePostpaidBillers()        → X-Userid: {{userid}}
 *   getMobilePostpaidBillerInputFields() → phone: {{phone}} + X-Userid: {{userid}}
 */
public class CatalogueService extends RcbpBaseService {

    // ── Credit card catalogue ─────────────────────────────────────────────────

    /**
     * GET /v2/catalogue/billers?category=creditCard
     * Postman: "credit card get billers" — X-Userid: {{user_id}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - data.groupedBillers[0].billers is not empty
     *   - Extracts billerId for biller "ICIC00000NATSI"
     */
    public Response getCreditCardBillers() {
        return buildCreditCardSpec()
                .queryParam("category", "creditCard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());
    }

    /**
     * GET /v2/catalogue/billers/{billerId}?category=creditCard
     * Postman: "credit card input fields" — X-Userid: {{user_id}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - data.inputFields is not empty
     *   - Extracts data.billerId and data.inputFields[0].paramName
     *
     * @param billerId e.g. "ICIC00000NATSI" (used in collection)
     */
    public Response getCreditCardBillerInputFields(String billerId) {
        return buildCreditCardSpec()
                .queryParam("category", "creditCard")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + billerId);
    }

    // ── Mobile postpaid catalogue ─────────────────────────────────────────────

    /**
     * GET /v2/catalogue/billers?category=mobilepostpaid
     * Postman: "mobile postpaid get billers" — X-Userid: {{userid}}
     *
     * Postman test assertions:
     *   - Status 200
     *   - data is not empty
     */
    public Response getMobilePostpaidBillers() {
        return buildMobilePostpaidSpec()
                .queryParam("category", "mobilepostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath());
    }

    /**
     * GET /v2/catalogue/billers/{billerId}?category=mobilePostpaid
     * Postman: "mobile postpaid input fields" — phone header + X-Userid: {{userid}}
     *
     * The collection sends a "phone" request header (not a query param).
     * Authorization is absent entirely on this request.
     *
     * Postman test assertions:
     *   - Status 200
     *   - data.inputFields is not empty
     *   - Extracts data.billerId and data.inputFields[0].paramName (used in fetch bill)
     *
     * @param billerId "VODA00000NAT96" (used in collection)
     */
    public Response getMobilePostpaidBillerInputFields(String billerId) {
        return buildMobilePostpaidInputFieldsSpec()
                .queryParam("category", "mobilePostpaid")
                .get(RcbpRoutes.CATALOGUE_BILLERS.getPath() + "/" + billerId);
    }
}
