// ============================================================================
// passengerNotifications.js
//
// Module 8 - notification content policy (passenger side).
//
// Passenger-facing notifications are limited to a FIXED, SMALL set of
// milestone messages: order confirmed, preparing, on the way, delivered,
// cancelled-with-refund. Every passenger sendNotification() call in this
// codebase should come from one of the functions below - never build an
// ad-hoc passenger notification string directly at a trigger call site.
// If a new passenger-facing moment is ever needed, add a function HERE
// first, don't inline a new sendNotification() call elsewhere.
//
// STRICT RULE: never include ETA numbers, GPS/speed/distance details, or
// internal reasoning ("recalculated because train slowed down") in
// passenger copy. If a human product-writer wouldn't say it out loud to a
// passenger, it doesn't belong here - reasons passed in below must always
// be short, plain-language phrases, not technical explanations.
//
// This is specific to the PASSENGER role. Restaurant/rider-facing
// notifications are NOT bound by this file - they can and should include
// the operational detail they actually need (countdowns, deadlines, order
// contents, rejection reasons, strike notices). Those are sent directly
// via sendNotification() at their own call sites in triggers/orders/*.js,
// dispatch/*.js, etc. - this restriction is specific to the passenger
// side, not a blanket rule for every role.
// ============================================================================

const { sendNotification } = require("./sendNotification");
const { ROLES } = require("./constants");

async function orderConfirmed(passengerUid, orderId) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "✅ Order Confirmed",
        body: "Your order has been placed successfully.",
        data: { orderId, milestone: "confirmed" }
    });
}

async function preparing(passengerUid, orderId) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "🍽️ Preparing Your Order",
        body: "Your order has been accepted and payment has been processed. The restaurant is preparing it now.",
        data: { orderId, milestone: "preparing" }
    });
}

async function onTheWay(passengerUid, orderId) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "🛵 Order On The Way",
        body: "Your order is on its way to you.",
        data: { orderId, milestone: "on_the_way" }
    });
}

async function delivered(passengerUid, orderId) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "✅ Order Delivered",
        body: "Your order has been delivered. Thank you for choosing Pak Train Food!",
        data: { orderId, milestone: "delivered" }
    });
}

/**
 * @param reason    short plain-language phrase, e.g. "the restaurant
 *                  couldn't prepare it in time" - no technical detail,
 *                  no ETA numbers, no internal reasoning.
 * @param refunded  true if money is actually coming back to the
 *                  passenger (refund-to-wallet). false for the
 *                  hold-release case (Module 3) - no money was ever
 *                  taken, so the copy shouldn't claim a refund happened.
 */
async function cancelled(passengerUid, orderId, reason, refunded = true, screen = null) {

    if (!passengerUid) return;

    // Module: an apology is included on every cancellation - this is the
    // passenger losing a meal they'd already paid for and planned around,
    // so the message shouldn't read like a neutral status update.
    const body = refunded
        ? `We're sorry - your order was cancelled because ${reason}. Your payment is being refunded in full.`
        : `We're sorry - your order was cancelled because ${reason}. You have not been charged.`;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "Order Cancelled",
        body,
        // Module: a rejected order can pass screen: "home" so tapping the
        // notification lands the passenger back on the journey/restaurant
        // picker - that order is over, there's nothing to return to.
        data: screen
            ? { orderId, milestone: "cancelled", screen }
            : { orderId, milestone: "cancelled" }
    });
}

/**
 * The one deliberate, documented exception to the 5-milestone rule: used
 * only when the passenger genuinely needs to make a decision (e.g. no
 * rider found yet - wait or cancel). Still held to the same plain-
 * language, no-technical-detail standard as everything else in this file.
 */
async function actionNeeded(passengerUid, orderId, title, body) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title,
        body,
        data: { orderId, milestone: "action_needed" }
    });
}

/**
 * Module: delivery handover OTP. A third, deliberate exception alongside
 * actionNeeded() above - the passenger needs this exact code to hand to
 * the rider at delivery, so (unlike every other milestone in this file)
 * the message must include a specific number, not just a plain-language
 * status update. Sent once, the moment the rider picks the order up
 * (see onOrderPickedUp.js, which also stores the same code on the order
 * doc as "deliveryOtp" for the rider app to verify against).
 */
async function deliveryOtp(passengerUid, orderId, otp) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "🔐 Delivery Code",
        body: `Share this code with the rider when they hand over your order: ${otp}`,
        data: { orderId, milestone: "delivery_otp", otp }
    });
}

// ============================================================================
// PAYMENT LIFECYCLE - a second, deliberate exception category alongside
// actionNeeded() above. These aren't delivery-progress milestones - they're
// the passenger's own money moving, which they're entitled to know about
// regardless of the "5 fixed messages" rule (a bank would never hide a
// charge or refund notification from you). Still no ETA/GPS/internal
// reasoning - just plain confirmation of what happened to their payment.
// ============================================================================

async function paymentHeld(passengerUid, orderId, amountPkr) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "Payment Held",
        body: `Rs ${Math.round(amountPkr)} has been held for your order. You won't be charged unless the restaurant accepts it.`,
        data: { screen: "wallet", orderId, milestone: "payment_held" }
    });
}

async function paymentSent(passengerUid, orderId, amountPkr, cardLast4) {

    if (!passengerUid) return;

    const cardText = cardLast4 ? ` (card ending ${cardLast4})` : "";

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "Payment Sent",
        body: `Rs ${Math.round(amountPkr)} has been charged for your order${cardText}.`,
        data: { screen: "wallet", orderId, milestone: "payment_sent" }
    });
}

async function refundPending(passengerUid, orderId, amountPkr, reason) {

    if (!passengerUid) return;

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "Refund On The Way",
        body: `Rs ${Math.round(amountPkr)} is being refunded${reason ? " - " + reason : ""}. It's on its way back to your card.`,
        data: { screen: "wallet", orderId, milestone: "refund_pending" }
    });
}

async function refundCompleted(passengerUid, orderId, amountPkr, cardLast4) {

    if (!passengerUid) return;

    const cardText = cardLast4 ? ` (card ending ${cardLast4})` : "";

    await sendNotification({
        uid: passengerUid,
        role: ROLES.PASSENGER,
        title: "Refund Completed",
        body: `Rs ${Math.round(amountPkr)} is back on your card${cardText}.`,
        data: { screen: "wallet", orderId, milestone: "refund_completed" }
    });
}

module.exports = {
    orderConfirmed,
    preparing,
    onTheWay,
    delivered,
    cancelled,
    actionNeeded,
    deliveryOtp,
    paymentHeld,
    paymentSent,
    refundPending,
    refundCompleted
};
