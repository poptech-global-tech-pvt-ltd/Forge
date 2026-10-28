package com.popclub.cardapi.impl;

import com.popclub.cardapi.dto.BasicDetailsRequestDto;
import com.popclub.cardapi.dto.OtpSendRequestDto;
import com.popclub.cardapi.dto.OtpVerifyRequestDto;
import com.popclub.cardapi.dto.SubmitConsentsRequestDto;
import com.popclub.cardapi.dto.UserDetailsRequestDto;
import com.popclub.cardapi.dto.PersonalDetailsRequestDto;
import com.popclub.cardapi.dto.ProfessionalDetailsRequestDto;
import com.popclub.cardapi.dto.YblAddressRequestDto;
import com.popclub.cardapi.dto.YblConsentsRequestDto;
import com.popclub.cardapi.enums.Routes;
import io.restassured.response.Response;

public class CardService extends BaseService {

    public Response sendOtp(OtpSendRequestDto request) {
        return post(Routes.OTP_SEND, request);
    }

    public Response sendOtpRaw(String rawBody) {
        return postRaw(Routes.OTP_SEND, rawBody);
    }

    public Response verifyOtp(OtpVerifyRequestDto request) {
        return post(Routes.OTP_VERIFY, request);
    }

    // Level 1 — Step 1.1: GET consents
    public Response getPopConsents(String mobileNumber) {
        return buildSpec()
                .queryParam("mobile_number", mobileNumber)
                .when()
                .get(Routes.POP_CONSENTS)
                .then()
                .log().ifError()
                .extract().response();
    }

    // Level 1 — Step 1.2: POST (submit) consents
    public Response submitConsents(SubmitConsentsRequestDto request) {
        return post(Routes.POP_CONSENTS, request);
    }

    // Level 1 — Step 2.0: POST user details (PAN + pincode)
    public Response postUserDetails(UserDetailsRequestDto request) {
        return post(Routes.USER_DETAILS, request);
    }

    // Raw versions for negative tests
    public Response submitConsentsRaw(String rawBody) {
        return postRaw(Routes.POP_CONSENTS, rawBody);
    }

    public Response postUserDetailsRaw(String rawBody) {
        return postRaw(Routes.USER_DETAILS, rawBody);
    }

    // Level 2: POST basic details (name, email, dob, gender, occupation, marital status)
    public Response postBasicDetails(BasicDetailsRequestDto request) {
        return post(Routes.USER_DETAILS, request);
    }

    public Response postBasicDetailsRaw(String rawBody) {
        return postRaw(Routes.USER_DETAILS, rawBody);
    }

    // Level 1 — Step 2.1: GET verify pincode
    public Response verifyPincode(String pinCode) {
        return buildSpec()
                .queryParam("pin_code", pinCode)
                .when()
                .get(Routes.VERIFY_PINCODE)
                .then()
                .log().ifError()
                .extract().response();
    }

    // Level 3: POST YBL consents
    public Response postYblConsents(YblConsentsRequestDto request) {
        return post(Routes.YBL_CONSENTS, request);
    }

    public Response postYblConsentsRaw(String rawBody) {
        return postRaw(Routes.YBL_CONSENTS, rawBody);
    }

    // Level 4: POST YBL address
    public Response postYblAddress(YblAddressRequestDto request) {
        return post(Routes.YBL_ADDRESS, request);
    }

    public Response postYblAddressRaw(String rawBody) {
        return postRaw(Routes.YBL_ADDRESS, rawBody);
    }

    // Level 4: GET YBL addresses (verify stored)
    public Response getYblAddresses() {
        return get(Routes.YBL_ADDRESSES);
    }

    // Level 5: POST personal details (name on card + father name)
    public Response postPersonalDetails(PersonalDetailsRequestDto request) {
        return post(Routes.YBL_PERSONAL_DETAILS, request);
    }

    public Response postPersonalDetailsRaw(String rawBody) {
        return postRaw(Routes.YBL_PERSONAL_DETAILS, rawBody);
    }

    // Level 5: GET personal details (verify stored)
    public Response getPersonalDetails() {
        return get(Routes.YBL_PERSONAL_DETAILS);
    }

    // Level 6: GET profession master lists
    public Response getMasterLists() {
        return buildSpec()
                .queryParam("filter", "company")
                .queryParam("filter", "industry")
                .queryParam("filter", "profession")
                .queryParam("filter", "business")
                .queryParam("filter", "bankrelation")
                .queryParam("filter", "designation")
                .queryParam("filter", "companytype")
                .when()
                .get(Routes.YBL_MASTER_LISTS)
                .then()
                .log().ifError()
                .extract().response();
    }

    // Level 6: POST professional details
    public Response postProfessionalDetails(ProfessionalDetailsRequestDto request) {
        return post(Routes.YBL_PROFESSIONAL_DETAILS, request);
    }

    public Response postProfessionalDetailsRaw(String rawBody) {
        return postRaw(Routes.YBL_PROFESSIONAL_DETAILS, rawBody);
    }

    // Level 6: GET professional details (verify stored)
    public Response getProfessionalDetails() {
        return get(Routes.YBL_PROFESSIONAL_DETAILS);
    }
}