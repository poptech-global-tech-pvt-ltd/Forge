package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

/**
 * Service wrapper for /v2/recharge/* endpoints.
 *
 * The prepaid collection defines two independent requests followed by a chained
 * bill-payment flow:
 *
 *   1. fetchPlans(phone, operatorName)   — fetch plans for a phone number using
 *                                          a pre-configured operator name;
 *                                          extracts plan_id and plan_amount
 *   2. fetchOperatorCircle(phone)        — detect operator + circle from phone;
 *                                          independent of the plans step
 *
 * Auth (from new prepaid collection):
 *   fetchPlans()         → X-Userid: 019cfef7 (hardcoded in collection)
 *   fetchOperatorCircle()→ X-Userid: 019cfef7 (hardcoded in collection)
 *
 * NOTE: fetchOperatorsAndStates() was present in the old collection only.
 * It is NOT in the new prepaid collection and has been removed.
 */
public class RechargeService extends RcbpBaseService {

    /**
     * GET /v2/recharge/fetch/plans?phone_number={phone}&name={operatorName}
     * X-Userid: 019cfef7 (hardcoded in collection).
     *
     * Postman test assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.plans is a non-empty array
     *   - plans[0].planData[0] has planId (number) and amount (number > 0)
     *   - no error property
     *   - Extracts plan_id and plan_amount (first plan across all groups)
     *
     * @param phoneNumber  {{phone}} in collection
     * @param operatorName {{operator_name}} in collection — pre-configured env variable
     */
    public Response fetchPlans(String phoneNumber, String operatorName) {
        return buildSpecWithOperatorId()
                .queryParam("phone_number", phoneNumber)
                .queryParam("name",         operatorName)
                .get(RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());
    }

    /**
     * GET /v2/recharge/fetch/operator-circle?phone_number={phone}
     * X-Userid: 019cfef7 (hardcoded in collection).
     *
     * Postman test assertions:
     *   - Status 200
     *   - is_success = true
     *   - data.current_operator is a non-empty string
     *   - data.current_location is a non-empty string
     *   - data has phone_number property
     *   - no error property
     *   - Extracts current_operator and current_location
     *
     * @param phoneNumber {{phone}} in collection
     */
    public Response fetchOperatorCircle(String phoneNumber) {
        return buildSpecWithOperatorId()
                .queryParam("phone_number", phoneNumber)
                .get(RcbpRoutes.RECHARGE_FETCH_OPERATOR_CIRCLE.getPath());
    }
}
