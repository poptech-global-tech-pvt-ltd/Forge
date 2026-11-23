package com.popclub.Card_Onboarding;

import com.popclub.testdata.YblMockRequests;
import com.popclub.cardapi.impl.MockCardService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import com.popclub.testsigma.TestSigmaId;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L8_EKYC_NegativeTest {

    private MockCardService mockCardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: L8_EKYC_NegativeTest (-ve)");
        mockCardService = new MockCardService();
    }

    @TestSigmaId(value = "PO-17176", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 1)
    public void ekyc_responseCodeNotZero() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "response_code_not_0000");

        assertEquals(response.statusCode(), 200);
        assertNotEquals(response.jsonPath().getString("responseCode"), "0000");
    }

    @TestSigmaId(value = "PO-17177", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 2)
    public void ekyc_journeyStatusNotActive() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "journey_status_not_active");

        assertEquals(response.statusCode(), 200);
        assertNotEquals(response.jsonPath().getString("journeyStatus"), "Active");
    }

    @TestSigmaId(value = "PO-17178", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 3)
    public void ekyc_standardJsonError_400() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "http_non200_standard_json_error");

        assertEquals(response.statusCode(), 400);
    }

    @TestSigmaId(value = "PO-17179", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 4)
    public void ekyc_serverError_500() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "http_non200_common_ybl_error_500");

        assertEquals(response.statusCode(), 500);
    }

    @TestSigmaId(value = "PO-17180", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 5)
    public void ekyc_unprocessableEntity_422() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "http_non200_common_ybl_error_422");

        assertEquals(response.statusCode(), 422);
    }

    @TestSigmaId(value = "PO-17181", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 6)
    public void ekyc_tooManyRequests_429() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "http_non200_common_ybl_error_429");

        assertEquals(response.statusCode(), 429);
    }

    @TestSigmaId(value = "PO-17182", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 7)
    public void ekyc_unauthorized_401() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "http_non200_common_ybl_error_401");

        assertEquals(response.statusCode(), 401);
    }

    @TestSigmaId(value = "PO-17183", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 8)
    public void ekyc_methodNotAllowed_405() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "http_non200_common_ybl_error_405");

        assertEquals(response.statusCode(), 405);
    }

    @TestSigmaId(value = "PO-17184", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 9)
    public void ekyc_encryptedErrorBody_400() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "http_non200_encrypted_error_body");

        assertEquals(response.statusCode(), 400);
    }
}
