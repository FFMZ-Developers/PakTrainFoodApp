// ============================================================================
// checkPrepDeadlines.js
//
// Module 6 - Failure 1: restaurant misses the prep deadline.
//
// onOrderAcceptedSetPrepDeadline.js (Module 4) stores a "etaEndTime"
// deadline the moment a restaurant accepts an order. There's no Firestore
// write that happens automatically when a deadline simply passes with
// nothing done - so this runs on a schedule (every minute) and looks for
// orders that are still "Accepted" (never made it to "ready_for_delivery")
// whose deadline has already passed.
//
// For each one found:
//   - cancel the order
//   - refund the passenger (payment was captured at accept-time - Module 3)
//   - strike the restaurant (Module 7's minimal piece, via reliabilityHelper)
//   - plain-language passenger notification, no technical detail
// ============================================================================

const { onSchedule } = require("firebase-functions/v2/scheduler");

const admin = require("../config/firebase");
const { STRIPE_SECRET_KEY } = require("../config/stripe");
const { refundToPassengerCard } = require("../utils/stripeRefund");
const reliabilityHelper = require("../utils/reliabilityHelper");
const passengerNotifications = require("../utils/passengerNotifications");
const { ROLES, ORDER_STATUS } = require("../utils/constants");

exports.checkPrepDeadlines = onSchedule(
    {
        schedule: "every 1 minutes",
        secrets: [STRIPE_SECRET_KEY]
    },
    async () => {

        const db = admin.firestore();
        const now = Date.now();

        const snap = await db.collection("Orders")
            .where("orderStatus", "==", ORDER_STATUS.ACCEPTED)
            .where("etaEndTime", "<=", now)
            .get();

        if (snap.empty) return;

        console.log("checkPrepDeadlines: found", snap.size, "order(s) past their prep deadline");

        for (const doc of snap.docs) {

            const orderId = doc.id;
            const order = doc.data();

            try {

                await db.collection("Orders").doc(orderId).update({
                    orderStatus: ORDER_STATUS.CANCELLED,
                    cancelReason: "restaurant_missed_prep_deadline",
                    cancelledAt: now
                });

                // Payment was captured when the restaurant accepted
                // (Module 3) - this is a genuine REAL Stripe refund now
                // (not just wallet credit).
                if (order.passengerUid && order.totalPrice > 0) {
                    await refundToPassengerCard(
                        order.passengerUid, order.totalPrice, orderId,
                        order.paymentIntentId,
                        "the restaurant couldn't prepare it in time");
                }

                if (order.restaurantId) {
                    await reliabilityHelper.recordStrike(
                        ROLES.RESTAURANT, order.restaurantId, orderId, "missed_prep_deadline");
                }

                if (order.passengerUid) {
                    // Module 8 - "cancelled-with-refund" milestone template.
                    await passengerNotifications.cancelled(
                        order.passengerUid, orderId,
                        "the restaurant couldn't prepare it in time");
                }

                console.log("checkPrepDeadlines: cancelled + refunded order", orderId);

            } catch (err) {

                console.error("checkPrepDeadlines: failed to process order", orderId, err);
            }
        }
    }
);
