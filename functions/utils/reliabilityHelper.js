// ============================================================================
// reliabilityHelper.js
//
// Minimal piece of Module 7 (Reliability score and level), pulled forward
// because Module 6's failure paths need to record strikes against
// restaurants/riders as they happen. The full Module 7 (score bands,
// "Trusted"/"Standard"/"Needs improvement" labels, feeding into Module 5's
// rider ranking) is its own pass - this file only does the recording.
//
// PROFILE DOCUMENT PATHS
//   Users/Restaurant/VerifiedRegister/{uid}
//   Users/Delivery/VerifiedRegister/{uid}
//
// FIELDS THIS WRITES
//   reliabilityScore   number, starts at Settings/orderConfig.reliabilityStartingScore (100),
//                       decremented by reliabilityStrikePenalty per strike, floored at 0
//   strikeCount        number, total strikes ever (lifetime counter, for display)
//   isPaused           boolean, set true once strikes within the rolling
//                       window reach the configured limit
//   strikes (sub-collection) - one doc per strike, used to count how many
//                       fell within the last N days (the rolling window)
// ============================================================================

const admin = require("../config/firebase");
const { sendNotification } = require("./sendNotification");
const { ROLES } = require("./constants");

const PROFILE_PATHS = {
    RESTAURANT: ["Users", "Restaurant", "VerifiedRegister"],
    DELIVERY: ["Users", "Delivery", "VerifiedRegister"]
};

function profileRef(role, uid) {

    const path = PROFILE_PATHS[role];

    if (!path) throw new Error("reliabilityHelper: unknown role " + role);

    return admin.firestore()
        .collection(path[0]).doc(path[1])
        .collection(path[2]).doc(uid);
}

async function loadReliabilitySettings() {

    let startingScore = 100;
    let strikePenalty = 15;
    let strikeLimit = { RESTAURANT: 3, DELIVERY: 3 };
    let windowDays = { RESTAURANT: 30, DELIVERY: 30 };

    try {

        const cfg = await admin.firestore().collection("Settings").doc("orderConfig").get();

        if (cfg.exists) {

            const d = cfg.data();

            if (typeof d.reliabilityStartingScore === "number") startingScore = d.reliabilityStartingScore;
            if (typeof d.reliabilityStrikePenalty === "number") strikePenalty = d.reliabilityStrikePenalty;

            if (typeof d.restaurantReliabilityStrikeLimit === "number") strikeLimit.RESTAURANT = d.restaurantReliabilityStrikeLimit;
            if (typeof d.riderReliabilityStrikeLimit === "number") strikeLimit.DELIVERY = d.riderReliabilityStrikeLimit;

            if (typeof d.restaurantReliabilityWindowDays === "number") windowDays.RESTAURANT = d.restaurantReliabilityWindowDays;
            if (typeof d.riderReliabilityWindowDays === "number") windowDays.DELIVERY = d.riderReliabilityWindowDays;
        }

    } catch (e) {
        // keep defaults
    }

    return { startingScore, strikePenalty, strikeLimit, windowDays };
}

/**
 * Records one strike against a restaurant or rider for a specific order,
 * decrements their reliability score, and auto-pauses their listing if
 * strikes within the rolling window reach the configured limit.
 *
 * @param role   ROLES.RESTAURANT or ROLES.DELIVERY
 * @param uid    the restaurant's or rider's uid
 * @param orderId  which order this strike is for (for the audit trail)
 * @param reason   short machine-readable reason, e.g. "missed_prep_deadline"
 */
async function recordStrike(role, uid, orderId, reason) {

    if (!uid) return;

    const { startingScore, strikePenalty, strikeLimit, windowDays } = await loadReliabilitySettings();

    const ref = profileRef(role, uid);

    // Record the strike itself first (used below to count the rolling window).
    await ref.collection("strikes").add({
        orderId,
        reason,
        at: Date.now()
    });

    const windowStart = Date.now() - (windowDays[role] * 24 * 60 * 60 * 1000);

    const recentStrikesSnap = await ref.collection("strikes")
        .where("at", ">=", windowStart)
        .get();

    const recentStrikeCount = recentStrikesSnap.size;

    const snap = await ref.get();
    const existingScore = snap.exists && typeof snap.data().reliabilityScore === "number"
        ? snap.data().reliabilityScore
        : startingScore;

    const newScore = Math.max(0, existingScore - strikePenalty);

    const shouldPause = recentStrikeCount >= strikeLimit[role];

    const update = {
        reliabilityScore: newScore,
        strikeCount: admin.firestore.FieldValue.increment(1)
    };

    if (shouldPause) {
        update.isPaused = true;
        update.pausedAt = Date.now();
        update.pausedReason = "Too many order-reliability strikes (" + recentStrikeCount + " in " + windowDays[role] + " days).";
    }

    await ref.set(update, { merge: true });

    if (shouldPause) {

        await sendNotification({
            uid,
            role,
            title: "Account Paused",
            body: "Your account has been temporarily paused due to repeated order issues. Please contact support.",
            data: { reason: "reliability_strikes" }
        });
    }

    console.log(
        "recordStrike:", role, uid, "- reason:", reason,
        "- score now", newScore, "- recent strikes", recentStrikeCount,
        shouldPause ? "- PAUSED" : ""
    );
}

/** Small positive nudge on a successfully completed order - capped at the starting/maximum score. */
async function recordCompletion(role, uid) {
    if (!uid) return;

    const { startingScore } = await loadReliabilitySettings();

    let bonus = 2;

    try {
        const cfg = await admin.firestore().collection("Settings").doc("orderConfig").get();
        if (cfg.exists && typeof cfg.data().reliabilityCompletionBonus === "number") {
            bonus = cfg.data().reliabilityCompletionBonus;
        }
    } catch (e) {}

    const ref = profileRef(role, uid);
    const snap = await ref.get();

    const existingScore = snap.exists && typeof snap.data().reliabilityScore === "number"
        ? snap.data().reliabilityScore
        : startingScore;

    const newScore = Math.min(startingScore, existingScore + bonus);

    await ref.set({ reliabilityScore: newScore }, { merge: true });
}

/**
 * Module 7 - derives a display label from a raw reliabilityScore, using
 * admin-configurable band thresholds (Settings/orderConfig). Same bands
 * apply to both roles unless the admin sets role-specific overrides.
 *
 *   score <  reliabilityStandardThreshold (default 50)  -> "Needs Improvement"
 *   score >= that but < reliabilityTrustedThreshold (85) -> "Standard"
 *   score >= reliabilityTrustedThreshold                 -> "Trusted"
 */
async function getLevel(score) {

    let standardThreshold = 50;
    let trustedThreshold = 85;

    try {

        const cfg = await admin.firestore().collection("Settings").doc("orderConfig").get();

        if (cfg.exists) {

            const d = cfg.data();

            if (typeof d.reliabilityStandardThreshold === "number") standardThreshold = d.reliabilityStandardThreshold;
            if (typeof d.reliabilityTrustedThreshold === "number") trustedThreshold = d.reliabilityTrustedThreshold;
        }

    } catch (e) {
        // keep defaults
    }

    if (score >= trustedThreshold) return "Trusted";
    if (score >= standardThreshold) return "Standard";
    return "Needs Improvement";
}

module.exports = {
    recordStrike,
    recordCompletion,
    getLevel,
    profileRef
};
