package com.popclub.api.impl;

import com.popclub.api.dto.SearchRequestDto;
import com.popclub.api.enums.Routes;
import io.restassured.response.Response;

/**
 * Search API service — wraps POST /api/search/v2/plp/
 *
 * Example:
 *   SearchService search = new SearchService();
 *   search.attachTokens(AuthApiClient.loginFull("1234561122", "560102"));
 *   Response r = search.searchPlp("t-shirt", 1);
 */
public class SearchService extends AppService {

    /** POST /api/search/v2/plp/?page=<page> with optional pincode delivery filter */
    public Response searchPlp(String query, String pincode, int page) {
        return buildSpec()
                .queryParam("page", page)
                .body(new SearchRequestDto(query, pincode))
                .post(Routes.SEARCH_PLP_V2.getPath());
    }

    /** POST /api/search/v2/plp/?page=<page> — no pincode filter */
    public Response searchPlp(String query, int page) {
        return searchPlp(query, null, page);
    }

    /** POST /api/search/v2/plp/?page=1 — convenience for first page */
    public Response searchPlp(String query) {
        return searchPlp(query, null, 1);
    }
}
