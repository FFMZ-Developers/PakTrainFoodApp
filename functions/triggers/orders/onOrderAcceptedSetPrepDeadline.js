// ============================================================================
// onOrderAcceptedSetPrepDeadline.js
//
// Module 4 - the moment a restaurant accepts an order, work out the exact
// deadline by which the food needs to be ready:
//
//     prepDeadline = now + (etaMinutes - riderTransitBufferMinutes)
//
// etaMinutes comes from Module 2's trainEtaEndTime (the train's live ETA to
// the meal station). riderTransitBufferMinutes (Module 0 setting, default
// 25) is how long a rider needs to get from the restaurant to the platform
// - subtracting it gives the restaurant a deadline that still leaves enough
// time for the handoff, not just "whenever the train arrives".
//
// ⚠️ FIELD NAME: this writes to "etaEndTime" - NOT "trainEtaEndTime".
// AcceptedOrdersFragment.java's "Ready for Delivery" countdown timer
// (updateTimer()) already reads "etaEndTime" - it existed, unused, since
// before Module 2 (that's exactly why Module 2's live train-ETA was given
// its own separate field, trainEtaEndTime, instead of colliding with this
// one). This trigger is the ONE place that should ever write to
// "etaEndTime" going forward.
// ============================================================================

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const { ORDER_STATUS } = require("../../utils/constants");

const DEFAULT_RIDER_TRANSIT_BUFFER_MINUTES = 25;
const MIN_PREP_MINUTES = 5; // always give the restaurant at least this long
const FALLBACK_PREP_MINUTES = 30; // used only if trainEtaEndTime is somehow missing

exports.onOrderAcceptedSetPrepDeadline = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (before.orderStatus === ORDER_STATUS.ACCEPTED ||
            after.orderStatus !== ORDER_STATUS.ACCEPTED) {
            return;
        }

        const orderId = event.params.orderId;
        const orderRef = admin.firestore().collection("Orders").doc(orderId);

        const trainEtaEndTime = after.trainEtaEndTime;

        let riderTransitBufferMinutes = DEFAULT_RIDER_TRANSIT_BUFFER_MINUTES;

        try {

            const cfg = await admin.firestore().collection("Settings").doc("orderConfig").get();

            if (cfg.exists && typeof cfg.data().riderTransitBufferMinutes === "number") {
                riderTransitBufferMinutes = cfg.data().riderTransitBufferMinutes;
            }

        } catch (e) {
            // keep default
        }

        let prepDeadline;

        if (trainEtaEndTime && typeof trainEtaEndTime === "number") {

            const etaMinutesFromNow = (trainEtaEndTime - Date.now()) / 60000;
            let prepMinutes = etaMinutesFromNow - riderTransitBufferMinutes;

            // Never hand the restaurant a deadline that's already passed or
            // absurdly short - Module 6's "missed prep deadline" handling
            // is what deals with genuinely-too-late orders, not this line.
            if (prepMinutes < MIN_PREP_MINUTES) prepMinutes = MIN_PREP_MINUTES;

            prepDeadline = Date.now() + prepMinutes * 60000;

        } else {

            // Shouldn't normally happen - Module 2 always sets an ETA
            // estimate at order placement - but fall back gracefully
            // rather than leaving the restaurant with no deadline at all.
            prepDeadline = Date.now() + FALLBACK_PREP_MINUTES * 60000;

            console.log("onOrderAcceptedSetPrepDeadline: order", orderId, "had no trainEtaEndTime - using flat fallback");
        }

        await orderRef.update({
            etaEndTime: prepDeadline
        });

        console.log(
            "onOrderAcceptedSetPrepDeadline: order", orderId,
            "prep deadline set to", new Date(prepDeadline).toISOString()
        );
    }
);
