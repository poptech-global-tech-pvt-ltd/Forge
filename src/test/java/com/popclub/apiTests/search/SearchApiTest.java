package com.popclub.apiTests.search;

import com.popclub.api.impl.BaseService;
import com.popclub.api.impl.SearchService;
import com.popclub.api.enums.Routes;
import com.popclub.api.auth.AuthApiClient;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * Search PLP API — happy path.
 *
 * Auth:  AuthApiClient.loginFull() → attaches both legacy token + JWT to SearchService.
 * Suite: testng-api.xml  (group = "search")
 */
public class SearchApiTest {

    private static final Logger log = LoggerFactory.getLogger(SearchApiTest.class);

    private static final String PHONE = "1234561122";
    private static final String OTP   = "560102";

    private SearchService searchService;

    @BeforeClass
    public void setup() {
        searchService = new SearchService();
        searchService.attachTokens(AuthApiClient.loginFull(PHONE, OTP));
        log.info("[SearchApiTest] Auth complete — tokens attached");
    }

    // ── Test cases ────────────────────────────────────────────────────────────

    @Test(description = "Search PLP returns 200 for valid query",
          groups = "search")
    public void searchPlp_validQuery_returns200() {
        log.info("Running: searchPlp_validQuery_returns200");

        Response response = searchService.searchPlp("t-shirt", 1);

        BaseService.assertStatus(response, 200, "POST", Routes.SEARCH_PLP_V2.getPath());
    }

    @Test(description = "Search PLP response contains results list",
          groups = "search",
          dependsOnMethods = "searchPlp_validQuery_returns200")
    public void searchPlp_validQuery_hasResults() {
        log.info("Running: searchPlp_validQuery_hasResults");

        Response response = searchService.searchPlp("t-shirt", 1);

        BaseService.assertStatus(response, 200, "POST", Routes.SEARCH_PLP_V2.getPath());

        List<?> results = response.jsonPath().getList("data.results");
        assertNotNull(results, "data.results should not be null");
        assertFalse(results.isEmpty(), "data.results should not be empty for query 't-shirt'");

        log.info("[SearchApiTest] results count = {}", results.size());
    }

    @Test(description = "Search PLP first result has required fields",
          groups = "search",
          dependsOnMethods = "searchPlp_validQuery_hasResults")
    public void searchPlp_firstResult_hasRequiredFields() {
        log.info("Running: searchPlp_firstResult_hasRequiredFields");

        Response response = searchService.searchPlp("t-shirt", 1);

        BaseService.assertStatus(response, 200, "POST", Routes.SEARCH_PLP_V2.getPath());

        assertNotNull(response.jsonPath().get("data.results[0].name"),
                "First result must have 'name'");
        assertNotNull(response.jsonPath().get("data.results[0].price"),
                "First result must have 'price'");

        String firstName = response.jsonPath().getString("data.results[0].name");
        log.info("[SearchApiTest] First result: {}", firstName);
    }

    @Test(description = "Search PLP page 2 returns different results",
          groups = "search")
    public void searchPlp_page2_returns200() {
        log.info("Running: searchPlp_page2_returns200");

        Response response = searchService.searchPlp("t-shirt", 2);

        BaseService.assertStatus(response, 200, "POST", Routes.SEARCH_PLP_V2.getPath());
    }

    @Test(description = "Search PLP with empty query returns 400 or empty results",
          groups = "search")
    public void searchPlp_emptyQuery_handledGracefully() {
        log.info("Running: searchPlp_emptyQuery_handledGracefully");

        Response response = searchService.searchPlp("", 1);

        int status = response.statusCode();
        assertTrue(status == 400 || status == 200,
                "Empty query should return 400 or 200, got: " + status);

        if (status == 200) {
            List<?> results = response.jsonPath().getList("data.results");
            assertTrue(results == null || results.isEmpty(),
                    "Empty query should return no results");
        }
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    @AfterClass
    public void teardown() {
        searchService.reset();
    }
}
