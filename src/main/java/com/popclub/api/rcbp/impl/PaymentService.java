package com.popclub.api.rcbp.impl;

import com.popclub.api.rcbp.dto.ConfirmPaymentRequestDto;
import com.popclub.api.rcbp.dto.InitiatePaymentRequestDto;
import com.popclub.api.rcbp.enums.RcbpRoutes;
import io.restassured.response.Response;

public class PaymentService extends RcbpBaseService {

    public Response initiatePostpaidPayment(InitiatePaymentRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    public Response initiatePrepaidPayment(InitiatePaymentRequestDto request) {
        return buildSpecWithUserId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    public Response initiateCreditCardPayment(InitiatePaymentRequestDto request) {
        return buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_INITIATE.getPath());
    }

    public Response confirmPostpaidPayment(ConfirmPaymentRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_CONFIRM.getPath());
    }

    public Response confirmPrepaidPayment(ConfirmPaymentRequestDto request) {
        return buildApiSpecWithOperatorId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_CONFIRM.getPath());
    }

    public Response confirmCreditCardPayment(ConfirmPaymentRequestDto request) {
        return buildSpecWithCcInputId()
                .body(request)
                .post(RcbpRoutes.PAYMENT_CONFIRMATION.getPath());
    }
}
