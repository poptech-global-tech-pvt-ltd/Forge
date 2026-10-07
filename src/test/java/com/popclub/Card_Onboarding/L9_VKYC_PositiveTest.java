package com.popclub.Card_Onboarding;

import com.popclub.testdata.YblMockRequests;
import com.popclub.cardapi.impl.MockCardService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import com.popclub.testsigma.TestSigmaId;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L9_VKYC_PositiveTest {

    private MockCardService mockCardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: L9_VKYC_PositiveTest (+ve)");
        mockCardService = new MockCardService();
    }

    @TestSigmaId(value = "PO-17197", folder = "happy", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a successful 2xx response")
    @Test(priority = 1)
    public void vkyc_defaultSuccess() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "default");

        assertEquals(response.statusCode(), 200);
    }

    @TestSigmaId(value = "PO-17198", folder = "happy", labels = {"regression", "can_automate"},
        steps = "1. Call mockCardService.postVkyc with the test input\n2. Verify the response",
        expectedResults = "API returns a successful 2xx response")
    @Test(priority = 2)
    public void vkyc_fullSuccess() {
        Response response = mockCardService.postVkyc(YblMockRequests.vkycRequest(), "full_success");

        assertEquals(response.statusCode(), 200);
        assertEquals(response.jsonPath().getString("responseCode"), "0000");
        assertEquals(response.jsonPath().getString("responseMessage"), "Success");
        assertEquals(response.jsonPath().getString("journeyStatus"), "Active");
        assertFalse(response.jsonPath().getString("url").isEmpty(), "URL should not be empty");
    }
}
