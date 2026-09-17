package com.popclub.testdata;

import com.popclub.api.dto.*;

import com.popclub.api.util.ConfigManager;
import lombok.Data;
import java.util.Arrays;

/**
 * Aggregate user model. Holds all test data for one user persona.
 * Use {@link #defaultFixed()} for deterministic tests.
 * Use {@link #()} for parallel-safe tests that need a fresh user.
 */
@Data
public class TestUser {

    // ── Identity ────────────────────────────────────────────────
    private String mobile;
    private String pan;
    private String pinCode;

    // ── Basic details ───────────────────────────────────────────
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String dob;
    private String gender;
    private String occupation;
    private String maritalStatus;

    // ── Personal details ────────────────────────────────────────
    private String nameOnCard;
    private String fatherName;

    // ── Professional details ────────────────────────────────────
    private String companyName;
    private String designation;
    private String annualIncome;
    private String companyType;
    private String profession;
    private String professionalOccupation;

    // ── Current address ─────────────────────────────────────────
    private String currentAddressType;
    private String currentAddressLine1;
    private String currentAddressLine2;
    private String currentAddressLine3;
    private String currentAddressLandmark;
    private String currentAddressCity;
    private String currentAddressState;
    private String currentAddressCountry;
    private String currentAddressPinCode;

    // ── Office address ──────────────────────────────────────────
    private String officeAddressType;
    private String officeAddressLine1;
    private String officeAddressLine2;
    private String officeAddressLine3;
    private String officeAddressLandmark;
    private String officeAddressCity;
    private String officeAddressState;
    private String officeAddressCountry;
    private String officeAddressPinCode;

    // ── Delivery address ────────────────────────────────────────
    private String deliveryAddressType;

    // ── Factory methods ─────────────────────────────────────────

    /**
     * Returns the fixed default user loaded from valid_user_details_card_onboarding.json.
     * Use for sequential tests that share a registered user.
     */
    public static TestUser defaultFixed() {
        return TestDataLoader.loadValidUser();
    }


// ── DTO converters ───────────────────────────────────────────

    public UserDetailsBasicRequestDto toBasicDetailsDto() {
        return UserDetailsBasicRequestDto.builder()
                .firstName(firstName)
                .middleName(middleName)
                .lastName(lastName)
                .email(email)
                .dob(dob)
                .gender(gender)
                .occupation(occupation)
                .maritalStatus(maritalStatus)
                .build();
    }

    public UserDetailsPanRequestDto toPanDto() {
        return UserDetailsPanRequestDto.builder()
                .pan(pan)
                .pinCode(pinCode)
                .build();
    }

    public PersonalDetailsRequestDto toPersonalDetailsDto() {
        return PersonalDetailsRequestDto.builder()
                .nameOnCard(nameOnCard)
                .fatherName(fatherName)
                .build();
    }

    public ProfessionalDetailsRequestDto toProfessionalDetailsDto() {
        return ProfessionalDetailsRequestDto.builder()
                .companyName(companyName)
                .designation(designation)
                .annualIncome(annualIncome)
                .companyType(companyType)
                .profession(profession)
                .occupation(professionalOccupation)
                .build();
    }

    public AddressRequestDto toCurrentAddressDto() {
        return AddressRequestDto.builder()
                .addressType(currentAddressType)
                .addressLine1(currentAddressLine1)
                .addressLine2(currentAddressLine2)
                .addressLine3(currentAddressLine3)
                .landmark(currentAddressLandmark)
                .city(currentAddressCity)
                .state(currentAddressState)
                .country(currentAddressCountry)
                .pinCode(currentAddressPinCode)
                .build();
    }

    public AddressRequestDto toOfficeAddressDto() {
        return AddressRequestDto.builder()
                .addressType(officeAddressType)
                .addressLine1(officeAddressLine1)
                .addressLine2(officeAddressLine2)
                .addressLine3(officeAddressLine3)
                .landmark(officeAddressLandmark)
                .city(officeAddressCity)
                .state(officeAddressState)
                .country(officeAddressCountry)
                .pinCode(officeAddressPinCode)
                .build();
    }

    public AddressRequestDto toDeliveryAddressDto() {
        return AddressRequestDto.builder()
                .addressType(deliveryAddressType)
                .isDeliveryAddress(true)
                .build();
    }

    public PopConsentRequestDto toPopConsentDto() {
        return PopConsentRequestDto.builder()
                .mobileNumber(mobile)
                .consents(Arrays.asList(
                        PopConsentRequestDto.Consent.builder()
                                .name("TandC")
                                .title("I accept the important terms & conditions to apply for the POPcard")
                                .isParent(true).isMandatory(true).value(true).build(),
                        PopConsentRequestDto.Consent.builder()
                                .name("call-sms-email")
                                .title("I authorise POP to call/SMS/e-mail/send Whatsapp messages to me")
                                .isParent(false).isMandatory(true).value(true).build(),
                        PopConsentRequestDto.Consent.builder()
                                .name("PAN")
                                .title("I authorise POP to verify my official details from NSDL using my PAN card.")
                                .isParent(false).isMandatory(true).value(true).build()
                ))
                .build();
    }
}
