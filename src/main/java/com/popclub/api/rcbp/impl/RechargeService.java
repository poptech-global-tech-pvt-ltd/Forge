package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

/**
 * Service wrapper for /v2/recharge/* endpoints.
 *
 * The collection defines a sequential recharge flow:
 *   1. fetchOperatorsAndStates() — load operators and states list
 *   2. fetchOperatorCircle(phone) — detect operator + circle from phone number;
 *                                   response contains data.current_operator and
 *                                   data.current_location — passed into step 3
 *   3. fetchPlans(phone, operatorId, stateId) — fetch plans for detected operator/circle
 *
 * Auth per request (Authorization is disabled everywhere in the collection):
 *   fetchOperatorsAndStates()  → X-Userid: {{X-Userid}}, auth type = noauth
 *   fetchOperatorCircle()      → X-Userid: hardcoded UUID "019cfef7-10b7-7cdc-8077-b96cf69e39d6"
 *   fetchPlans()               → X-Userid: {{X-Userid}}
 */
public class RechargeService extends RcbpBaseService {

    /**
     * GET /v2/recharge/fetch/operators/states
     * Postman auth type: noauth. X-Userid: {{X-Userid}}.
     *
     * Postman test assertions:
     *   - Status 200
     *   - data is not empty
     */
    public Response fetchOperatorsAndStates() {
        return buildRechargeSpec(RcbpConfigManager.getXUserid())
                .get(RcbpRoutes.RECHARGE_FETCH_OPERATORS_STATES.getPath());
    }

    /**
     * GET /v2/recharge/fetch/operator-circle?phone_number={phone}
     * X-Userid: hardcoded UUID in collection ("019cfef7-10b7-7cdc-8077-b96cf69e39d6").
     * Authorization disabled.
     *
     * Postman test assertions:
     *   - Status 200
     *   - data.current_operator is not undefined
     *   - data.current_location is not undefined
     *   - Extracts current_operator and current_location for use in fetchPlans
     *
     * @param phoneNumber {{phone}} in collection
     */
    public Response fetchOperatorCircle(String phoneNumber) {
        return buildRechargeSpec(RcbpConfigManager.getOperatorUserid())
                .queryParam("phone_number", phoneNumber)
                .get(RcbpRoutes.RECHARGE_FETCH_OPERATOR_CIRCLE.getPath());
    }

    /**
     * GET /v2/recharge/fetch/plans?phone_number={phone}&operator_id={op}&state_id={state}
     * X-Userid: {{X-Userid}}. No Authorization.
     *
     * operatorId and stateId are extracted from fetchOperatorCircle response
     * (data.current_operator and data.current_location respectively).
     *
     * Postman test assertions:
     *   - Status 200
     *   - data is not empty
     *
     * NOTE: state_id and operator_id params are disabled (unchecked) in the
     * collection but the test scripts confirm the intent to use detected values.
     * The linter-updated version of this service already includes them.
     *
     * @param phoneNumber  {{phone}}
     * @param operatorId   data.current_operator from fetchOperatorCircle
     * @param stateId      data.current_location from fetchOperatorCircle
     */
    public Response fetchPlans(String phoneNumber, String operatorId, String stateId) {
        return buildRechargeSpec(RcbpConfigManager.getXUserid())
                .queryParam("phone_number", phoneNumber)
                .queryParam("operator_id",  operatorId)
                .queryParam("state_id",     stateId)
                .get(RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());
    }
}
