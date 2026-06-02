package com.popclub.clp;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one section (widget row) from a CLP API response.
 *
 * Section-level:
 *   sectionTitle    → heading row          e.g. "Trending Now"
 *   sectionSubtitle → sub-text             e.g. "Shop the latest"
 *   cardType        → widget type          e.g. "products_carousel"
 *
 * Item-level (cards inside the carousel / grid / list):
 *   itemTitles      → ["Nike Air Max", "Adidas Originals"]
 *   itemSubtitles   → ["Footwear", "Apparel"]
 *   itemPrices      → ["3149", "2738"]         (selling price, raw number string)
 *   itemMrps        → ["4095", "3999"]         (original / MRP price)
 *   itemDiscounts   → ["23", "32"]             (discount %, e.g. "23" means 23%)
 *   itemPopcoins    → ["350", "484"]           (POPcoins reward)
 *
 * Filter-level (chips shown inside the section):
 *   filters         → ["All", "Men", "Women", "Kids"]
 *
 * Banner-level (CTA buttons on hero/full-width banners):
 *   bannerCtas      → ["Explore", "Shop now"]  (CTA button texts to tap)
 *   isBanner        → true when this section is a banner widget
 */
public class ClpSection {

    public final String       sectionTitle;
    public final String       sectionSubtitle;
    public final String       cardType;

    // Item-level content
    public final List<String> itemTitles;
    public final List<String> itemSubtitles;
    public final List<String> itemPrices;
    public final List<String> itemMrps;
    public final List<String> itemDiscounts;
    public final List<String> itemPopcoins;

    // Filter chips
    public final List<String> filters;

    // Banner CTAs
    public final List<String> bannerCtas;
    public final boolean      isBanner;

    public ClpSection(String       sectionTitle,
                      String       sectionSubtitle,
                      String       cardType,
                      List<String> itemTitles,
                      List<String> itemSubtitles,
                      List<String> itemPrices,
                      List<String> itemMrps,
                      List<String> itemDiscounts,
                      List<String> itemPopcoins,
                      List<String> filters,
                      List<String> bannerCtas,
                      boolean      isBanner) {
        this.sectionTitle    = nonNull(sectionTitle);
        this.sectionSubtitle = nonNull(sectionSubtitle);
        this.cardType        = nonNull(cardType);
        this.itemTitles      = orEmpty(itemTitles);
        this.itemSubtitles   = orEmpty(itemSubtitles);
        this.itemPrices      = orEmpty(itemPrices);
        this.itemMrps        = orEmpty(itemMrps);
        this.itemDiscounts   = orEmpty(itemDiscounts);
        this.itemPopcoins    = orEmpty(itemPopcoins);
        this.filters         = orEmpty(filters);
        this.bannerCtas      = orEmpty(bannerCtas);
        this.isBanner        = isBanner;
    }

    public boolean hasFilters()    { return !filters.isEmpty(); }
    public boolean hasBannerCtas() { return !bannerCtas.isEmpty(); }
    public boolean hasItems()      { return !itemTitles.isEmpty(); }

    /** Price string for item at index i, formatted with ₹ prefix. */
    public String formattedPrice(int i) {
        if (i >= itemPrices.size() || itemPrices.get(i).isEmpty()) return "";
        return "₹" + formatNumber(itemPrices.get(i));
    }

    /** MRP string for item at index i, formatted with ₹ prefix. */
    public String formattedMrp(int i) {
        if (i >= itemMrps.size() || itemMrps.get(i).isEmpty()) return "";
        return "₹" + formatNumber(itemMrps.get(i));
    }

    /** Discount label, e.g. "23% off" */
    public String discountLabel(int i) {
        if (i >= itemDiscounts.size() || itemDiscounts.get(i).isEmpty()) return "";
        return itemDiscounts.get(i) + "% off";
    }

    /** POPcoins label, e.g. "350" */
    public String popcoinLabel(int i) {
        if (i >= itemPopcoins.size() || itemPopcoins.get(i).isEmpty()) return "";
        return itemPopcoins.get(i);
    }

    @Override
    public String toString() {
        return "[" + cardType + "] \"" + sectionTitle + "\""
                + (sectionSubtitle.isEmpty() ? "" : " / \"" + sectionSubtitle + "\"")
                + " (" + itemTitles.size() + " items"
                + (hasFilters()    ? ", " + filters.size()    + " filters"  : "")
                + (hasBannerCtas() ? ", " + bannerCtas.size() + " banners"  : "")
                + ")";
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String nonNull(String s) {
        return s != null ? s.trim() : "";
    }

    private static List<String> orEmpty(List<String> l) {
        return l != null ? l : new ArrayList<>();
    }

    /** Formats "3149" → "3,149" for readability; passes through non-numeric strings. */
    private static String formatNumber(String raw) {
        try {
            long val = Long.parseLong(raw.replaceAll("[^0-9]", ""));
            return String.format("%,d", val);
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
