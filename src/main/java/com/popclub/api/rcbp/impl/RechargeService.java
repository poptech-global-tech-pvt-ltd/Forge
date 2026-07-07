package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

public class RechargeService extends RcbpBaseService {

    public Response fetchPlans(String phoneNumber, String operatorName) {
        return buildSpecWithOperatorId()
                .queryParam("phone_number", phoneNumber)
                .queryParam("name",         operatorName)
                .get(RcbpRoutes.RECHARGE_FETCH_PLANS.getPath());
    }

    public Response fetchOperatorCircle(String phoneNumber) {
        return buildSpecWithOperatorId()
                .queryParam("phone_number", phoneNumber)
                .get(RcbpRoutes.RECHARGE_FETCH_OPERATOR_CIRCLE.getPath());
    }
}
