const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("../../config/firebase");
const { sendNotification } = require("../../utils/sendNotification");
const {
    ROLES,
    ORDER_STATUS
} = require("../../utils/constants");

exports.onRiderArrived = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (
            before.orderStatus === "arrive_rider_at_resturent" ||
            after.orderStatus !== "arrive_rider_at_resturent"
        ) {
            return;
        }

        // Module - dispute-review timeline (see onDeliveryFailed.js).
        await event.data.after.ref.update({ riderArrivedAt: Date.now() });

        const passengerUid = after.passengerUid;
        const restaurantId = after.restaurantId;
        const riderId = after.acceptedBy;
        const orderId = after.orderId;

        // Module 8 - "rider arrived at restaurant" is NOT one of the
        // fixed passenger milestones - it's an operational step that
        // doesn't change anything for the passenger (they're still just
        // waiting on "on the way"). Restaurant/rider still get their own
        // notifications below, since operational detail is fine for them.

        // =========================
        // Restaurant
        // =========================

        if (restaurantId) {

          await sendNotification({

    uid: restaurantId,

    role: ROLES.RESTAURANT,

    title: "📍 Rider Arrived",

    body: "The rider has arrived. Please hand over the order.",

    data: {

        orderId,

        status: ORDER_STATUS.ARRIVE_RIDER_AT_RESTAURANT

    }

});

        }

        // =========================
        // Rider
        // =========================

        if (riderId) {

           await sendNotification({

    uid: riderId,

    role: ROLES.DELIVERY,

    title: "📍 Arrival Confirmed",

    body: "You have arrived at the restaurant. Wait for the restaurant to hand over the order.",

    data: {

        orderId,

        status: ORDER_STATUS.ARRIVE_RIDER_AT_RESTAURANT

    }

});d

        }

        console.log("Rider Arrived Notifications Sent");

    }
);