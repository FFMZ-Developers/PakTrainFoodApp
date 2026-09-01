const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const { sendNotification } = require("../../utils/sendNotification");

const {
    ROLES,
    ORDER_STATUS
} = require("../../utils/constants");

exports.onOrderPickedUp = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (
            before.orderStatus === "pick_up" ||
            after.orderStatus !== "pick_up"
        ) {
            return;
        }

        const riderId = after.acceptedBy;
        const restaurantId = after.restaurantId;
        const orderId = after.orderId;

        // Module 8 - no passenger notification here. From the passenger's
        // point of view, "picked up" is the same moment as "on the way",
        // which they already heard about in onOrderDropped.js (the food
        // leaving the restaurant with the rider). A second ping for what's
        // really an internal rider-app step is unnecessary noise.

        // =========================
        // Restaurant Notification
        // =========================

        if (restaurantId) {

            await sendNotification({

                uid: restaurantId,

                role: ROLES.RESTAURANT,

                title: "📦 Order Picked Up",

                body: "The rider has picked up the order and is delivering it.",

                data: {

                    orderId,

                    status: ORDER_STATUS.PICK_UP

                }

            });
        }

        // =========================
        // Rider Notification
        // =========================

        if (riderId) {

            await sendNotification({

                uid: riderId,

                role: ROLES.DELIVERY,

                title: "🚚 Delivery Started",

                body: "You have picked up the order. Deliver it to the passenger.",

                data: {

                    orderId,

                    status: ORDER_STATUS.PICK_UP

                }

            });
        }

        console.log("Pick Up Notifications Sent");

    }
);
