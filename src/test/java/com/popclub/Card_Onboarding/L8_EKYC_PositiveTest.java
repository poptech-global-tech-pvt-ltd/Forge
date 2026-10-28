package com.popclub.Card_Onboarding;

import com.popclub.testdata.YblMockRequests;
import com.popclub.cardapi.impl.MockCardService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import com.popclub.testsigma.TestSigmaId;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L8_EKYC_PositiveTest {

    private MockCardService mockCardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: L8_EKYC_PositiveTest (+ve)");
        mockCardService = new MockCardService();
    }

    @TestSigmaId(value = "PO-17185", folder = "happy", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a successful 2xx response")
    @Test(priority = 1)
    public void ekyc_defaultSuccess() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "default");

        assertEquals(response.statusCode(), 200);
    }

    @TestSigmaId(value = "PO-17186", folder = "happy", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postEkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a successful 2xx response")
    @Test(priority = 2)
    public void ekyc_fullSuccess() {
        Response response = mockCardService.postEkyc(YblMockRequests.ekycRequest(), "full_success");

        assertEquals(response.statusCode(), 200);
        assertEquals(response.jsonPath().getString("responseCode"), "0000");
        assertEquals(response.jsonPath().getString("responseMessage"), "Success");
        assertEquals(response.jsonPath().getString("journeyStatus"), "Active");
        assertEquals(response.jsonPath().getString("ckycAllowed"), "Y");
        assertFalse(response.jsonPath().getString("url").isEmpty(), "URL should not be empty");
    }
}
