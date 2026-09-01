// ============================================================================
// onOrderEtaThresholdReached.js
//
// Module 4 - restaurant order surfacing.
//
// WHY THIS EXISTS
// A passenger can place an order hours before the train is anywhere near
// the meal station. Showing that order to the restaurant immediately would
// be useless (and confusing) - the food would go cold waiting. So every
// order starts hidden from the restaurant (visibleToRestaurant: false, set
// at order placement - see OrderNowFragment.java) and only becomes visible
// once the train's ETA falls within a sensible "start thinking about this"
// window - Module 0's orderDispatchEtaThresholdMinutes setting (default 60
// minutes, admin-tunable without a new release).
//
// This reacts to trainEtaEndTime changing (Module 2 keeps that field fresh
// via the order-placement estimate, the live-tracking screen, and the
// onLocationUpdated RTDB trigger) - every time it changes, re-check whether
// the order has now crossed the threshold, and if so, flip the flag and
// notify the restaurant.
// ============================================================================

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const { sendNotification } = require("../../utils/sendNotification");
const { ROLES, ORDER_STATUS } = require("../../utils/constants");

const DEFAULT_THRESHOLD_MINUTES = 60;

// ⚠️ TESTING TOGGLE - during testing, the passenger's ETA is often
// nowhere near the configured threshold (e.g. testing from home, far from
// any real train route), so restaurants would never see the order at all
// while iterating on other parts of the flow. This makes every order
// visible to the restaurant immediately, regardless of ETA/route -
// matching dispatchRider.js's TESTING_CITY_WIDE_SEARCH pattern. The real
// threshold-based logic below is untouched and dormant - flip this to
// false once testing is done and orders should only surface as the train
// actually gets close.
const TESTING_INSTANT_RESTAURANT_VISIBILITY = true;

exports.onOrderEtaThresholdReached = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();
        const orderId = event.params.orderId;

        // Already visible - nothing to do (this trigger's whole job is the
        // one-time flip from false -> true).
        if (after.visibleToRestaurant === true) return;

        // Only makes sense for orders still awaiting restaurant action.
        if (after.orderStatus !== ORDER_STATUS.ACTIVE) return;

        const trainEtaEndTime = after.trainEtaEndTime;

        if (!trainEtaEndTime || typeof trainEtaEndTime !== "number") return;

        // Only re-check when the ETA actually changed - avoids re-running
        // this on unrelated field writes to the same order document.
        if (before.trainEtaEndTime === after.trainEtaEndTime) return;

        const etaMinutes = (trainEtaEndTime - Date.now()) / 60000;

        let thresholdMinutes = DEFAULT_THRESHOLD_MINUTES;

        try {

            const cfg = await admin.firestore().collection("Settings").doc("orderConfig").get();

            if (cfg.exists && typeof cfg.data().orderDispatchEtaThresholdMinutes === "number") {
                thresholdMinutes = cfg.data().orderDispatchEtaThresholdMinutes;
            }

        } catch (e) {
            // keep default
        }

        if (!TESTING_INSTANT_RESTAURANT_VISIBILITY && etaMinutes > thresholdMinutes) {
            return; // train's still too far away - stay hidden for now
        }

        const orderRef = admin.firestore().collection("Orders").doc(orderId);

        await orderRef.update({
            visibleToRestaurant: true,
            dispatchedAt: new Date().toISOString()
        });

        const restaurantId = after.restaurantId;

        if (restaurantId) {

            await sendNotification({
                uid: restaurantId,
                role: ROLES.RESTAURANT,
                title: "🍽️ New Order",
                body: "A train is approaching with a food order for you - please review and accept it.",
                data: {
                    orderId,
                    status: ORDER_STATUS.ACTIVE
                }
            });
        }

        console.log(
            "onOrderEtaThresholdReached: order", orderId,
            "now visible to restaurant (ETA", etaMinutes.toFixed(1), "min, threshold", thresholdMinutes, "min)"
        );
    }
);
