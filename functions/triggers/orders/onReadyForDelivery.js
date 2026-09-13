const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

exports.onReadyForDelivery = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (
            before.orderStatus === "ready_for_delivery" ||
            after.orderStatus !== "ready_for_delivery"
        ) {
            return;
        }

        // Module 8 - "ready for delivery" is NOT one of the fixed passenger
        // milestones (order confirmed / preparing / on the way / delivered /
        // cancelled-with-refund). The passenger already knows their order
        // is "preparing" (from onRestaurantAccepted.js) and will next hear
        // "on the way" once a rider actually has the food
        // (onOrderDropped.js) - a separate "ready" ping in between would
        // just be extra noise without a new milestone the passenger can
        // act on.
        //
        // Module 5 - rider notifications are handled by
        // dispatch/dispatchRider.js (expanding-radius search), which fires
        // on this same orderStatus transition.

        console.log("Ready For Delivery - rider dispatch handed off to dispatchRider.js (no passenger notification - not one of the fixed milestones)");

    }
);
