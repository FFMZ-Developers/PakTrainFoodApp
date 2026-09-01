// ============================================================================
// onOrderPaymentReversal.js
//
// Module 3 - the two "it didn't happen after all" paths for a payment that
// was only ever AUTHORIZED (held), not necessarily captured yet:
//
//   1. releaseOrderAuthorization - the order is rejected/cancelled BEFORE
//      captureOrderPayment ever ran. Nothing has been taken from the
//      passenger's card - Stripe's hold is simply cancelled. This path
//      NEVER touches the wallet, because no money was ever taken.
//
//   2. refund path - the order is rejected/cancelled AFTER capture already
//      happened (paymentCaptured === true). The money genuinely left the
//      passenger's card, so this credits it back into their in-app wallet
//      (Wallets/Passenger/Accounts/{uid}.availableBalance) with a "Refund"
//      history entry - reusing the exact same helper the rest of the app
//      already uses for refunds (walletHelper.refundToPassenger).
//
// Both paths are triggered the same way: Orders/{orderId}.orderStatus
// changing to "Rejected" or "Cancelled". Which of the two runs depends
// entirely on whether paymentCaptured is already true at that point - the
// two paths are kept as clearly separate functions below (not merged
// logic) specifically so it's always obvious which one ran and why.
// ============================================================================

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../config/firebase");
const { getStripeClient, STRIPE_SECRET_KEY } = require("../config/stripe");
const { refundToPassengerCard } = require("../utils/stripeRefund");
const { sendNotification } = require("../utils/sendNotification");
const passengerNotifications = require("../utils/passengerNotifications");
const { ROLES, ORDER_STATUS } = require("../utils/constants");

const TERMINAL_REVERSAL_STATUSES = [ORDER_STATUS.REJECTED, ORDER_STATUS.CANCELLED];

/**
 * Cancels the Stripe hold outright - used when the order never got far
 * enough to be captured. No wallet involvement: the passenger's card was
 * never actually charged, so there's nothing to "give back".
 */
async function releaseOrderAuthorization(paymentIntentId, orderRef, orderId) {

    const stripe = getStripeClient();

    await stripe.paymentIntents.cancel(paymentIntentId);

    await orderRef.update({
        paymentStatus: "authorization_released",
        releasedAt: new Date().toISOString()
    });

    console.log("releaseOrderAuthorization: released hold", paymentIntentId, "for order", orderId);
}

/**
 * The order's payment WAS already captured (real money left the card)
 * before it got rejected/cancelled - so this issues a REAL Stripe refund
 * back to the same card, instead of trying to cancel a hold that no
 * longer exists (a captured PaymentIntent can't be cancelled via Stripe's
 * cancel endpoint; refunding is the correct undo for money that already
 * moved).
 */
async function refundCapturedPayment(after, orderRef, orderId) {

    const passengerUid = after.passengerUid;
    const amount = after.totalPrice || 0;

    if (!passengerUid || amount <= 0) {
        console.log("refundCapturedPayment: missing passengerUid/amount for order", orderId, "- skipping refund");
        return;
    }

    await refundToPassengerCard(
        passengerUid, amount, orderId, after.paymentIntentId,
        "this order was cancelled");

    await orderRef.update({
        paymentStatus: "refunded",
        refundedAt: new Date().toISOString()
    });

    console.log("refundCapturedPayment: refunded Rs", amount, "to passenger", passengerUid, "for order", orderId);
}

/**
 * Module 4 - "surface alternative open restaurants" (the part that was
 * left out of the first pass). Restaurants are registered against a
 * "city" field that matches the meal station name with common suffixes
 * trimmed - mirrors cleanCityName() in
 * Passanger_Resturent_list_Fragment.java on the Android side, so the same
 * restaurants a passenger would see browsing that station normally are
 * the ones offered here.
 */
async function findAlternativeRestaurants(mealStation, excludeRestaurantId) {

    if (!mealStation) return [];

    // ✅ FIX: matches CityNameUtils.java exactly. Real station docs are
    // stored WITHOUT spaces (e.g. "MandiBahauddin", "MalakwalJn"), so the
    // suffix is anchored to the END of the string (not word-boundaries,
    // which don't exist between concatenated CamelCase words), and ALL
    // whitespace is removed (not just collapsed) so "Mandi Bahauddin"
    // (restaurant's spinner city) and "MandiBahauddin" (station doc id)
    // normalize identically.
    const cleanedCity = String(mealStation)
        .trim()
        .replace(/\s*(jn|jct|junction|cantt|cant|railway station|station)$/i, "")
        .replace(/\s+/g, "")
        .toLowerCase();

    if (!cleanedCity) return [];

    const db = admin.firestore();

    const snap = await db.collection("Users").doc("Restaurant")
        .collection("VerifiedRegister")
        .where("cityNormalized", "==", cleanedCity)
        .limit(8)
        .get();

    const alternatives = [];

    snap.forEach((doc) => {

        if (alternatives.length >= 5) return;
        if (doc.id === excludeRestaurantId) return;

        const data = doc.data();

        alternatives.push({
            restaurantId: doc.id,
            restaurantName: data.restaurantName || "Restaurant"
        });
    });

    return alternatives;
}

exports.onOrderPaymentReversal = onDocumentUpdated(
    {
        document: "Orders/{orderId}",
        secrets: [STRIPE_SECRET_KEY]
    },
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        const wasAlreadyTerminal = TERMINAL_REVERSAL_STATUSES.includes(before.orderStatus);
        const isNowTerminal = TERMINAL_REVERSAL_STATUSES.includes(after.orderStatus);

        if (wasAlreadyTerminal || !isNowTerminal) {
            return; // only act on the moment it FIRST becomes Rejected/Cancelled
        }

        const orderId = event.params.orderId;
        const orderRef = admin.firestore().collection("Orders").doc(orderId);

        try {

            if (after.paymentCaptured === true) {

                await refundCapturedPayment(after, orderRef, orderId);

            } else if (after.paymentIntentId) {

                await releaseOrderAuthorization(after.paymentIntentId, orderRef, orderId);

            } else {

                console.log("onOrderPaymentReversal: order", orderId, "has no paymentIntentId and wasn't captured - nothing to reverse");
            }

            // ============================================================
            // Module 4 - passenger notification, in plain language (per
            // the "keep it simple" rule for passenger-facing copy: no
            // technical detail, no ETA numbers, just what happened and
            // what to do next).
            //
            // ============================================================

            if (after.orderStatus === ORDER_STATUS.REJECTED && after.passengerUid) {

                // Module 8 - "cancelled-with-refund" milestone template,
                // using the restaurant's own reason if they gave one
                // (falls back to a generic phrase - see ActiveOrdersFragment.java).
                const reason = after.rejectionReason
                    ? `${after.rejectionReason} - please choose another restaurant`
                    : "this restaurant couldn't accept your order - please choose another restaurant";

                await passengerNotifications.cancelled(after.passengerUid, orderId, reason, true, "home");

                // Module 4 - "surface alternative open restaurants". Best
                // effort: if this lookup fails for any reason, the
                // passenger still got the notification above, so the
                // whole reversal doesn't need to fail over it.
                try {

                    const alternatives = await findAlternativeRestaurants(
                        after.mealStation, after.restaurantId);

                    await orderRef.update({
                        alternativeRestaurants: alternatives,
                        hasAlternatives: alternatives.length > 0
                    });

                } catch (altErr) {

                    console.error("onOrderPaymentReversal: alternative-restaurant lookup failed for order", orderId, altErr);
                }
            }

        } catch (err) {

            console.error("onOrderPaymentReversal: failed for order", orderId, err);

            await orderRef.update({
                paymentStatus: "reversal_failed",
                paymentReversalError: err.message || String(err)
            }).catch(() => {});
        }
    }
);
