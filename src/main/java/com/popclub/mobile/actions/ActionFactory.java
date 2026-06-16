package com.popclub.mobile.actions;

public class ActionFactory {

    public static Action get(String action) {
        switch (action) {
            // ── Core gestures ──────────────────────────────────────────────────
            case "tap":               return new TapAction();
            case "enterText":         return new EnterTextAction();
            case "launchApp":         return new LaunchAppAction();
            case "tapIfPresent":      return new TapIfPresentAction();
            case "waitFor":           return new WaitForAction();
            case "pressKey":          return new PressKeyAction();
            case "pullToRefresh":     return new PullToRefreshAction();

            // ── Assertions ────────────────────────────────────────────────────
            case "verifyElement":     return new VerifyElementAction();
            case "scanTags":          return new ScanTagsAction();

            // ── Auth ──────────────────────────────────────────────────────────
            case "captureToken":      return new CaptureTokenAction();
            case "loginIfNeeded":     return new LoginIfNeededAction();

            // ── CLP — setup (must run first) ──────────────────────────────────
            case "verifyCLP":         return new VerifyClpAction();

            // ── CLP — content verification ────────────────────────────────────
            case "verifyCLPFullyLoaded":    return new VerifyClpFullyLoadedAction();
            case "verifyCLPOrder":          return new VerifyClpOrderAction();
            case "verifyCLPPrices":         return new VerifyClpPricesAction();
            case "verifyCLPBanners":        return new VerifyClpBannersAction();
            case "verifyCLPFilters":        return new VerifyClpFiltersAction();
            case "verifyCLPCarouselSwipe":  return new VerifyClpCarouselSwipeAction();
            case "verifyCLPSeeAll":         return new VerifyClpSeeAllAction();
            case "verifyCLPRedirects":      return new VerifyClpRedirectAction();
            case "verifyClpSectionItems":   return new VerifyClpSectionItemsAction();

            // ── CLP — interaction helpers ─────────────────────────────────────
            case "tapByText":         return new TapByTextAction();
            case "tapClpItem":        return new TapClpItemAction();

            // ── Text capture / assertion primitives ───────────────────────────
            case "captureText":              return new CaptureTextAction();
            case "assertStoredText":         return new AssertStoredTextAction();
            case "assertText":               return new AssertTextAction();

            // ── API calls ─────────────────────────────────────────────────────
            case "fetchApi":                 return new FetchApiAction();

            // ── Conditional flow / reusable call ─────────────────────────────
            // These are intercepted by TestExecutor before ActionFactory is reached.
            // Registered here only to prevent "Unknown action" errors on lookup.
            case "ifPresent":                return step -> {}; // no-op sentinel
            case "ifNotPresent":             return step -> {}; // no-op sentinel
            case "ifVarEmpty":               return step -> {}; // no-op sentinel
            case "ifVarNotEmpty":            return step -> {}; // no-op sentinel
            case "ifVarEquals":              return step -> {}; // no-op sentinel
            case "ifVarNotEquals":           return step -> {}; // no-op sentinel
            case "logVar":                   return step -> {}; // no-op sentinel
            case "call":                     return step -> {}; // no-op sentinel

            // ── Search / Cart / Wishlist flows ────────────────────────────────
            case "searchAndVerifyCartPrice": return new SearchCartPriceAction();
            case "cartRemoveItem":           return new CartRemoveAction();
            case "verifyWishlistFlow":       return new WishlistFlowAction();

            default: throw new RuntimeException("Unknown action: " + action);
        }
    }
}
