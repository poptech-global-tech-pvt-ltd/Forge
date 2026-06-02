package com.popclub.clp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popclub.api.impl.PopApiClient;
import com.popclub.api.impl.PopRequestSpecification;
import com.popclub.api.enums.Authorization;
import com.popclub.api.enums.HttpMethod;
import com.popclub.api.util.ApiConstants;
import com.popclub.core.TestContext;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClpApiClient — fetches CLP (Category Landing Page) widget data from
 * presentation.popclub.co.in for home, shop, and card CLPs.
 *
 * Supports full pagination:
 *   GET presentation-layer/{userId}/{pageId}?page=0
 *   GET presentation-layer/{userId}/{pageId}?page=1
 *   … until the response data is empty or hasMore is false.
 *
 * Auth:
 *   - Authorization: Token <APP_TOKEN>    (always)
 *   - X-Access-Token: Bearer <USER_TOKEN> (if USER_TOKEN set in local.properties)
 *
 * Usage:
 *   ClpApiClient client = new ClpApiClient("userId-or-null");
 *
 *   // Single page (backward-compatible):
 *   Response page0 = client.fetch(Page.SHOP);
 *
 *   // All pages merged:
 *   List<JsonNode> allSections = client.fetchAllSections(Page.SHOP);
 */
public class ClpApiClient {

    private static final Logger log = LoggerFactory.getLogger(ClpApiClient.class);

    /** Safety cap — never fetch more than this many pages in one run. */
    private static final int MAX_PAGES = 20;

    public enum Page {
        HOME  ("pophomenew",    "Home CLP"),
        SHOP  ("shopmerch-30",  "Shop CLP"),
        CARD  ("credit-card-2", "Card CLP");

        public final String pageId;
        public final String displayName;

        Page(String pageId, String displayName) {
            this.pageId      = pageId;
            this.displayName = displayName;
        }
    }

    private final String userId;

    /**
     * @param userId The logged-in user's ID.  Pass "null" (string) to get
     *               non-personalised data, which still returns the full widget list.
     */
    public ClpApiClient(String userId) {
        this.userId = (userId == null || userId.isBlank()) ? "null" : userId;
    }

    // ── Convenience single-page shortcuts ─────────────────────────────────────

    public Response fetchHome() { return fetch(Page.HOME, 0); }
    public Response fetchShop() { return fetch(Page.SHOP, 0); }
    public Response fetchCard() { return fetch(Page.CARD, 0); }

    /** Fetches page 0 only (backward-compatible). */
    public Response fetch(Page page) { return fetch(page, 0); }

    /** Fetches a specific page number. */
    public Response fetch(Page page, int pageNum) {
        String url = buildUrl(page, pageNum);
        log.info("[ClpApiClient] Fetching {} page={} → {}", page.displayName, pageNum, url);

        PopRequestSpecification spec = PopRequestSpecification.builder()
                .baseUrl(url)
                .authorization(Authorization.APP_API)
                .headers(buildHeaders())
                .build();

        Response response = PopApiClient.executeRequest(HttpMethod.GET, spec);
        log.info("[ClpApiClient] {} page={} → HTTP {}", page.displayName, pageNum, response.statusCode());
        return response;
    }

    // ── All-sections fetch (single call, no pagination) ───────────────────────

    /**
     * Fetches all sections for the given CLP page in a single API call.
     * The CLP API returns all sections at once — no page parameter needed.
     *
     * @return flat list of every section JsonNode from the response
     */
    public List<JsonNode> fetchAllSections(Page page) throws Exception {
        ObjectMapper   mapper = new ObjectMapper();
        List<JsonNode> all    = new ArrayList<>();

        log.info("[ClpApiClient] fetchAllSections({}) …", page.displayName);

        Response response = fetch(page, 0);   // pageNum ignored — URL has no ?page param

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "CLP API returned HTTP " + response.statusCode()
                    + " for " + page.displayName);
        }

        JsonNode root = mapper.readTree(response.body().asString());
        int added = collectDataNodes(root, all);

        log.info("[ClpApiClient] fetchAllSections({}) → {} sections", page.displayName, added);
        return all;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String buildUrl(Page page, int pageNum) {
        // No page query parameter — the CLP API returns all sections in one call.
        return ApiConstants.PRESENTATION_BASE_URL
                + "presentation-layer/" + userId + "/" + page.pageId;
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("platform",    "android");
        headers.put("app_version", "89");
        headers.put("device_id",   "b1bee75238a76999");
        headers.put("Accept",      "application/json");

        // Attach user JWT if available (personalised response)
        String userToken = TestContext.getUserToken();
        if (userToken == null || userToken.isBlank()) userToken = ApiConstants.USER_TOKEN;
        if (userToken != null && !userToken.isBlank()) {
            headers.put("X-Access-Token", "Bearer " + userToken);
        }

        return headers;
    }

    /**
     * Collects all section nodes from the response into {@code target}.
     * Handles both shapes:
     *   { data: [ ... ] }
     *   { data: { tabs: [ { sections: [ ... ] } ] } }
     *
     * @return number of nodes added
     */
    private int collectDataNodes(JsonNode root, List<JsonNode> target) {
        int before = target.size();
        JsonNode data = root.path("data");

        if (data.isArray()) {
            data.forEach(target::add);
        } else if (data.isObject()) {
            JsonNode tabs = data.path("tabs");
            if (tabs.isArray()) {
                for (JsonNode tab : tabs) {
                    JsonNode sections = tab.path("sections");
                    if (sections.isArray()) sections.forEach(target::add);
                }
            }
        }

        return target.size() - before;
    }

    /**
     * Returns {@code true} ONLY when the API explicitly signals more pages.
     *
     * Checks (in order):
     *   1. meta.hasMore / meta.has_more                         (boolean)
     *   2. meta.nextPage / meta.next_page                       (non-null = more pages)
     *   3. pagination.hasMore / pagination.has_more             (boolean)
     *   4. pagination.nextPage / next_page / next               (non-null = more pages)
     *   5. pagination.totalPages / total_pages                  (int comparison)
     *   6. top-level hasMore / has_more                         (boolean)
     *
     * DEFAULT: false — if none of the above signals are present, stop after the
     * current page.  This prevents page=1 of a non-paginated CLP endpoint from
     * accidentally returning a DIFFERENT CLP's sections (e.g. shopmerch-30?page=1
     * returning home sections) and polluting the parsed section list.
     */
    private boolean hasMorePages(JsonNode root, int currentPage) {

        // Check meta object
        JsonNode meta = root.path("meta");
        if (!meta.isMissingNode()) {
            if (hasBooleanField(meta, "hasMore", "has_more"))
                return getBool(meta, "hasMore", "has_more");
            if (hasNullableField(meta, "nextPage", "next_page"))
                return !isNullField(meta, "nextPage", "next_page");
        }

        // Check pagination object
        JsonNode pagination = root.path("pagination");
        if (!pagination.isMissingNode()) {
            if (hasBooleanField(pagination, "hasMore", "has_more"))
                return getBool(pagination, "hasMore", "has_more");
            if (hasNullableField(pagination, "nextPage", "next_page", "next"))
                return !isNullField(pagination, "nextPage", "next_page", "next");
            for (String f : new String[]{"totalPages", "total_pages"}) {
                JsonNode n = pagination.path(f);
                if (!n.isMissingNode() && n.isInt())
                    return currentPage + 1 < n.asInt();
            }
        }

        // Check top-level hasMore
        if (hasBooleanField(root, "hasMore", "has_more"))
            return getBool(root, "hasMore", "has_more");

        // No explicit pagination signal — stop here.
        // Prevents non-paginated endpoints (e.g. shopmerch-30) from fetching page=1
        // which may return unrelated CLP data from the backend.
        log.debug("[ClpApiClient] No pagination signal on page={} — stopping.", currentPage);
        return false;
    }

    private boolean hasBooleanField(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (!n.isMissingNode() && n.isBoolean()) return true;
        }
        return false;
    }

    private boolean getBool(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (!n.isMissingNode() && n.isBoolean()) return n.asBoolean();
        }
        return false;
    }

    private boolean hasNullableField(JsonNode node, String... fields) {
        for (String f : fields) {
            if (!node.path(f).isMissingNode()) return true;
        }
        return false;
    }

    private boolean isNullField(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (!n.isMissingNode()) return n.isNull();
        }
        return true;
    }
}
