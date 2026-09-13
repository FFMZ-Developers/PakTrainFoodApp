// ============================================================================
// stripeRefund.js
//
// Module - REAL Stripe refunds for passengers (replaces the old
// wallet-credit-only "refund" in walletHelper.js).
//
// WHY THIS CHANGED
// Passengers only ever pay by card (Stripe PaymentSheet) - they never set
// up a Stripe Connect account or bank details (unlike restaurants/riders,
// who DO need their own connected account to receive payouts). So a
// passenger's "refund" should go back to the SAME card they paid with,
// via Stripe's own refund API - not become in-app store credit.
//
// FLOW
//   1. "Refund Pending" notification + wallet history entry, the moment
//      the refund is initiated.
//   2. stripe.refunds.create() is called against the order's original
//      PaymentIntent.
//   3. In Stripe TEST mode, card refunds resolve synchronously almost
//      always (status: "succeeded" comes back immediately) - so this
//      fires the "Refund Completed" notification + history entry right
//      after, in the same function call. In LIVE mode, a real refund can
//      take days to actually reach the card - a production build would
//      need a Stripe webhook (charge.refund.updated) to fire the
//      "completed" step instead of assuming it's immediate. That webhook
//      isn't set up in this project yet - documented here so it's not
//      forgotten when going live.
// ============================================================================

const admin = require("../config/firebase");
const { getStripeClient } = require("../config/stripe");
const passengerNotifications = require("./passengerNotifications");

const WALLET_ROLE_PASSENGER = "Passenger";

function passengerWalletRef(uid) {
    return admin.firestore().collection("Wallets").doc(WALLET_ROLE_PASSENGER)
        .collection("Accounts").doc(uid);
}

/**
 * @param passengerUid  who to refund
 * @param amountPkr     PKR amount to refund (can be less than the full
 *                       order total - Failure 3/dispute resolution can
 *                       refund a partial amount)
 * @param orderId
 * @param paymentIntentId  the order's Stripe PaymentIntent - required,
 *                       there's no real refund without it
 * @param reason         short plain-language phrase for the passenger
 *                       notification (matches passengerNotifications.js
 *                       policy - no technical detail)
 */
async function refundToPassengerCard(passengerUid, amountPkr, orderId, paymentIntentId, reason) {

    if (!passengerUid || !amountPkr || amountPkr <= 0) return;

    const walletRef = passengerWalletRef(passengerUid);
    const now = new Date().toISOString();

    // Step 1 - "Refund Pending" - recorded immediately, before we even
    // call Stripe, so the passenger sees SOMETHING right away even if the
    // Stripe call is slow.
    await walletRef.collection("history").add({
        type: "Refund Pending",
        amount: amountPkr,
        orderId,
        date: now
    });

    await passengerNotifications.refundPending(passengerUid, orderId, amountPkr, reason);

    if (!paymentIntentId) {
        console.error("refundToPassengerCard: no paymentIntentId for order", orderId, "- cannot issue a real refund");
        return;
    }

    try {

        const stripe = getStripeClient();

        const { pkrToUsdCents } = require("./currency");
        const refundUsdCents = pkrToUsdCents(amountPkr);

        const refund = await stripe.refunds.create({
            payment_intent: paymentIntentId,
            amount: refundUsdCents
        });

        // Card last 4 digits, for the passenger-facing receipt - pulled
        // from the PaymentIntent's charge, not stored anywhere before now.
        let cardLast4 = null;

        try {

            const intent = await stripe.paymentIntents.retrieve(paymentIntentId, {
                expand: ["latest_charge.payment_method_details"]
            });

            cardLast4 = intent.latest_charge &&
                intent.latest_charge.payment_method_details &&
                intent.latest_charge.payment_method_details.card
                ? intent.latest_charge.payment_method_details.card.last4
                : null;

        } catch (e) {
            // Non-fatal - receipt just won't show the card digits.
        }

        // Step 2 - "Refund Completed" (test mode: essentially immediate).
        await walletRef.collection("history").add({
            type: "Refund Completed",
            amount: amountPkr,
            orderId,
            cardLast4: cardLast4 || "N/A",
            refundId: refund.id,
            date: new Date().toISOString()
        });

        await passengerNotifications.refundCompleted(passengerUid, orderId, amountPkr, cardLast4);

        console.log("refundToPassengerCard: refunded Rs", amountPkr, "for order", orderId, "- Stripe refund", refund.id);

    } catch (err) {

        console.error("refundToPassengerCard: Stripe refund failed for order", orderId, err);

        await walletRef.collection("history").add({
            type: "Refund Failed",
            amount: amountPkr,
            orderId,
            error: err.message || String(err),
            date: new Date().toISOString()
        });
    }
}

module.exports = { refundToPassengerCard };
