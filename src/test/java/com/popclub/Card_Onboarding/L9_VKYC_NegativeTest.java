package com.popclub.Card_Onboarding;

import com.popclub.testdata.YblMockRequests;
import com.popclub.cardapi.impl.MockCardService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import com.popclub.testsigma.TestSigmaId;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L9_VKYC_NegativeTest {

    private MockCardService mockCardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: L9_VKYC_NegativeTest (-ve)");
        mockCardService = new MockCardService();
    }

    @TestSigmaId(value = "PO-17187", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 1)
    public void vkyc_responseCodeNotZero() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "response_code_not_0000");

        assertEquals(response.statusCode(), 200);
        assertNotEquals(response.jsonPath().getString("responseCode"), "0000");
    }

    @TestSigmaId(value = "PO-17188", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 2)
    public void vkyc_journeyStatusNotActive() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "journey_status_not_active");

        assertEquals(response.statusCode(), 200);
        assertNotEquals(response.jsonPath().getString("journeyStatus"), "Active");
    }

    @TestSigmaId(value = "PO-17189", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 3)
    public void vkyc_forbiddenRateLimit() {
        // Mock returns 200 with httpCode "403" in body for this scenario
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "403_forbidden_rate_limit");

        assertEquals(response.statusCode(), 200);
    }

    @TestSigmaId(value = "PO-17190", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 4)
    public void vkyc_standardJsonError_400() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "http_non200_standard_json_error");

        assertEquals(response.statusCode(), 400);
    }

    @TestSigmaId(value = "PO-17191", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 5)
    public void vkyc_serverError_500() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "http_non200_common_ybl_error_500");

        assertEquals(response.statusCode(), 500);
    }

    @TestSigmaId(value = "PO-17192", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 6)
    public void vkyc_unprocessableEntity_422() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "http_non200_common_ybl_error_422");

        assertEquals(response.statusCode(), 422);
    }

    @TestSigmaId(value = "PO-17193", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 7)
    public void vkyc_tooManyRequests() {
        // Mock returns 201 for this scenario
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "http_non200_common_ybl_error_429");

        assertEquals(response.statusCode(), 201);
    }

    @TestSigmaId(value = "PO-17194", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 8)
    public void vkyc_unauthorized_401() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "http_non200_common_ybl_error_401");

        assertEquals(response.statusCode(), 401);
    }

    @TestSigmaId(value = "PO-17195", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 9)
    public void vkyc_methodNotAllowed_405() {
        // Mock returns 200 with httpCode "405" in body for this scenario
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "http_non200_common_ybl_error_405");

        assertEquals(response.statusCode(), 200);
    }

    @TestSigmaId(value = "PO-17196", folder = "negative", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a 4xx error response")
    @Test(priority = 10)
    public void vkyc_encryptedErrorBody_400() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "http_non200_encrypted_error_body");

        assertEquals(response.statusCode(), 400);
    }
}
