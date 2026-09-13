// ============================================================================
// checkStalledJourneys.js
//
// Module 6 - Failure 4: passenger disembarked before the meal station.
//
// onLocationUpdated.js (Module 2) tracks, for every order that hasn't been
// dispatched to the restaurant yet, the best (smallest) remaining-distance
// seen so far and when it last improved (lastProgressAt). If a passenger
// gets off the train early, remainingKm stops shrinking - lastProgressAt
// stays frozen while real time keeps moving.
//
// This runs every few minutes and cancels any such order whose
// lastProgressAt has fallen more than journeyStallMinutesBeforeCancel
// (Module 0 setting, default 12) behind now.
//
// Payment was only ever AUTHORIZED at this stage (order hasn't reached the
// restaurant yet, so captureOrderPayment.js never ran) - so this releases
// the hold (Module 3's release path), it does NOT refund. No money was
// ever taken, so there's nothing to give back.
// ============================================================================

const { onSchedule } = require("firebase-functions/v2/scheduler");

const admin = require("../config/firebase");
const { getStripeClient, STRIPE_SECRET_KEY } = require("../config/stripe");
const passengerNotifications = require("../utils/passengerNotifications");
const { ORDER_STATUS } = require("../utils/constants");

const DEFAULT_STALL_MINUTES = 12;

exports.checkStalledJourneys = onSchedule(
    {
        schedule: "every 5 minutes",
        secrets: [STRIPE_SECRET_KEY]
    },
    async () => {

        const db = admin.firestore();

        let stallMinutes = DEFAULT_STALL_MINUTES;

        try {
            const cfg = await db.collection("Settings").doc("orderConfig").get();
            if (cfg.exists && typeof cfg.data().journeyStallMinutesBeforeCancel === "number") {
                stallMinutes = cfg.data().journeyStallMinutesBeforeCancel;
            }
        } catch (e) {
            // keep default
        }

        const stallThreshold = Date.now() - (stallMinutes * 60000);

        // Only orders still in the pre-dispatch window (matches the
        // condition onLocationUpdated.js uses when deciding whether to
        // even track progress for stall purposes).
        const snap = await db.collection("Orders")
            .where("orderStatus", "==", ORDER_STATUS.ACTIVE)
            .where("visibleToRestaurant", "==", false)
            .where("lastProgressAt", "<=", stallThreshold)
            .get();

        if (snap.empty) return;

        console.log("checkStalledJourneys: found", snap.size, "stalled order(s)");

        const stripe = getStripeClient();

        for (const doc of snap.docs) {

            const orderId = doc.id;
            const order = doc.data();

            try {

                await doc.ref.update({
                    orderStatus: ORDER_STATUS.CANCELLED,
                    cancelReason: "passenger_journey_stalled",
                    cancelledAt: Date.now()
                });

                if (order.paymentIntentId && order.paymentCaptured !== true) {
                    await stripe.paymentIntents.cancel(order.paymentIntentId);
                    await doc.ref.update({ paymentStatus: "authorization_released" });
                }

                if (order.passengerUid) {
                    // Module 8 - "cancelled" (no refund claim - payment
                    // was only ever authorized, never captured, so
                    // "refunded" would be inaccurate here).
                    await passengerNotifications.cancelled(
                        order.passengerUid, orderId,
                        "you're no longer approaching the selected station",
                        false);
                }

                console.log("checkStalledJourneys: cancelled stalled order", orderId);

            } catch (err) {

                console.error("checkStalledJourneys: failed to process order", orderId, err);
            }
        }
    }
);
