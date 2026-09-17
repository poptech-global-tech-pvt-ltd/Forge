package com.popclub.api.impl;

import com.popclub.api.enums.Routes;
import com.popclub.api.dto.AddressRequestDto;
import com.popclub.api.dto.PersonalDetailsRequestDto;
import com.popclub.api.dto.ProfessionalDetailsRequestDto;
import com.popclub.api.dto.YblConsentRequestDto;
import io.restassured.response.Response;

public class YblService extends BaseService {

    // Step 4.1: Get YBL consents
    public Response getYblConsents() {
        return get(Routes.YBL_CONSENTS.getPath());
    }

    // Step 4.0: Save YBL consents
    public Response saveYblConsents(YblConsentRequestDto request) {
        return post(Routes.YBL_CONSENTS.getPath(), request);
    }

    // Step 5.0: Save current address
    public Response saveAddressCurrent(AddressRequestDto request) {
        return post(Routes.YBL_ADDRESS.getPath(), request);
    }

    // Step 5.1: Get addresses
    public Response getAddresses() {
        return get(Routes.YBL_ADDRESSES.getPath());
    }

    // Step 6.0: Save personal details
    public Response savePersonalDetails(PersonalDetailsRequestDto request) {
        return post(Routes.YBL_PERSONAL_DETAILS.getPath(), request);
    }

    // Step 6.1: Get personal details
    public Response getPersonalDetails() {
        return get(Routes.YBL_PERSONAL_DETAILS.getPath());
    }

    // Step 7.0 GET: Get profession master lists
    public Response getProfessionMasters() {
        return get(Routes.YBL_MASTER_LISTS.getPath());
    }

    // Step 7.0 POST: Save professional details
    public Response saveProfessionalDetails(ProfessionalDetailsRequestDto request) {
        return post(Routes.YBL_PROFESSIONAL_DETAILS.getPath(), request);
    }

    // Step 8.0: Save office address
    public Response saveAddressOffice(AddressRequestDto request) {
        return post(Routes.YBL_ADDRESS.getPath(), request);
    }

    // Step 9.0: Save delivery address
    public Response saveAddressDelivery(AddressRequestDto request) {
        return post(Routes.YBL_ADDRESS.getPath(), request);
    }
}
