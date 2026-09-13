const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const { sendNotification } = require("../../utils/sendNotification");
const passengerNotifications = require("../../utils/passengerNotifications");

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
        const passengerUid = after.passengerUid;
        const orderId = after.orderId;

        // ✅ FIX: rider used to be able to tap "Hand Over to Passenger"
        // and complete the order on a plain YES/NO confirm - nothing
        // actually verified the passenger was there. Now a 4-digit OTP is
        // generated the moment the rider picks up, stored on the order
        // doc, and sent to the passenger. The rider app requires this
        // code before the order can move to "completed"
        // (Order_Accept_Fragment.java).
        const deliveryOtp = String(Math.floor(1000 + Math.random() * 9000));

        try {

            await event.data.after.ref.update({
                deliveryOtp,
                deliveryOtpGeneratedAt: Date.now()
            });

        } catch (err) {
            console.error("onOrderPickedUp: couldn't write deliveryOtp for", orderId, err);
        }

        // Module 8 - no passenger notification here. From the passenger's
        // point of view, "picked up" is the same moment as "on the way",
        // which they already heard about in onOrderDropped.js (the food
        // leaving the restaurant with the rider). A second ping for what's
        // really an internal rider-app step is unnecessary noise. The OTP
        // notification below is a separate, deliberate exception (same
        // category as paymentHeld/paymentSent in passengerNotifications.js)
        // since the passenger genuinely needs this code to hand it to the
        // rider at delivery.

        await passengerNotifications.deliveryOtp(passengerUid, orderId, deliveryOtp);

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
