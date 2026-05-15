package com.popclub.api.impl;

import com.popclub.api.enums.Routes;
import com.popclub.api.util.ConfigManager;
import com.popclub.api.dto.PopConsentRequestDto;
import com.popclub.api.dto.UserDetailsBasicRequestDto;
import com.popclub.api.dto.UserDetailsPanRequestDto;
import com.popclub.api.dto.VerifySSORequestDto;
import io.restassured.response.Response;

public class PopService extends BaseService {

    // Step 1: Auth
    public Response verifySSO(VerifySSORequestDto request) {
        return post(Routes.SSO_VERIFY.getPath(), request);
    }

    // Step 1.1: Get POP consents
    public Response getPopConsents() {
        return buildPublicSpec()
                .queryParam("mobile_number", ConfigManager.getMobileNumber())
                .get(Routes.POP_CONSENTS.getPath());
    }

    // Step 1.2: Post POP consents
    public Response postPopConsent(PopConsentRequestDto request) {
        return post(Routes.POP_CONSENTS.getPath(), request);
    }

    // Step 2.0: Update user details - PAN + pincode
    public Response updateUserDetailsPan(UserDetailsPanRequestDto request) {
        return post(Routes.POP_USER_DETAILS.getPath(), request);
    }

    // Step 2.1: Verify pincode
    public Response verifyPincode(String pinCode) {
        return buildSpec()
                .queryParam("pin_code", pinCode)
                .get(Routes.POP_VERIFY_PINCODE.getPath());
    }

    // Step 3.1: Get user details
    public Response getUserDetails() {
        return get(Routes.POP_USER_DETAILS.getPath());
    }

    // Step 3.0: Update user details - basic info
    public Response updateUserDetailsBasic(UserDetailsBasicRequestDto request) {
        return post(Routes.POP_USER_DETAILS.getPath(), request);
    }

    // Journey status check - called after each major step
    public Response getUserJourneyDetail() {
        return get(Routes.POP_USER_JOURNEY.getPath());
    }
}
