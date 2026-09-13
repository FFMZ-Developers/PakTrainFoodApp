const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const { sendNotification } = require("../../utils/sendNotification");
const passengerNotifications = require("../../utils/passengerNotifications");

const {
    ROLES,
    ORDER_STATUS
} = require("../../utils/constants");

exports.onOrderDropped = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (
            before.orderStatus === "dropped" ||
            after.orderStatus !== "dropped"
        ) {
            return;
        }

        // Module 6 (Failure 3) - this is the "food physically left the
        // restaurant" moment. If the rider fails to complete the delivery
        // AFTER this point, the restaurant still gets paid for the food
        // they made (see onDeliveryFailed.js) - before this point, they
        // don't, since nothing was actually handed over yet.
        await event.data.after.ref.update({
            pickupConfirmedAt: Date.now()
        });

        const riderId = after.acceptedBy;
        const passengerUid = after.passengerUid;
        const restaurantId = after.restaurantId;
        const orderId = after.orderId;

        // =========================
        // Rider Notification
        // =========================

        if (riderId) {

           await sendNotification({

    uid: riderId,

    role: ROLES.DELIVERY,

    title: "📦 Order Ready for Pickup",

    body: "The restaurant has handed over the order. Please pick it up.",

    data: {

        orderId,

        status: ORDER_STATUS.DROPPED

    }

});

        }

        // =========================
        // Passenger Notification
        //
        // Module 8 - "on the way" milestone. This is the moment the food
        // actually leaves the restaurant with the rider - the clearest,
        // most accurate point to tell the passenger their order is on its
        // way (onOrderPickedUp.js's later "pick_up" status doesn't get a
        // separate passenger ping - from their point of view it's the
        // same "on the way" moment, just an internal rider-app step).
        // =========================

        await passengerNotifications.onTheWay(passengerUid, orderId);

        // =========================
        // Restaurant Notification
        // =========================

        if (restaurantId) {

           await sendNotification({

    uid: restaurantId,

    role: ROLES.RESTAURANT,

    title: "✅ Order Handed Over",

    body: "You have successfully handed over the order to the rider.",

    data: {

        orderId,

        status: ORDER_STATUS.DROPPED

    }

});

        }

        console.log("Dropped Notifications Sent");

    }
);