package com.portiq.model;

/**
 * The call on a stock. Held positions and new ideas use different verbs on purpose - "buy" and
 * "buy more" are not the same decision, and neither is "don't own this" versus "sell what you own".
 */
public enum RecommendationAction {
    /** New idea worth opening a position in. */
    BUY,
    /** Already held and worth adding to. */
    ACCUMULATE,
    /** Already held; no action indicated. */
    HOLD,
    /** Already held; reduce the position (usually oversized or deteriorating). */
    TRIM,
    /** Already held; exit. */
    SELL,
    /** Not held, and the signals argue against opening a position. */
    AVOID
}
