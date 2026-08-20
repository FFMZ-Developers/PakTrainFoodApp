const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const walletHelper = require("../../utils/walletHelper");

const { sendNotification } = require("../../utils/sendNotification");
const {
    ROLES,
    ORDER_STATUS
} = require("../../utils/constants");

exports.onOrderCompleted = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (
            before.orderStatus === "completed" ||
            after.orderStatus !== "completed"
        ) {
            return;
        }

        const passengerUid = after.passengerUid;
        const restaurantId = after.restaurantId;
        const riderId = after.acceptedBy;
        const orderId = after.orderId;

        const subtotal = after.subtotal || 0;
        const deliveryFee = after.deliveryFee || 0;

        // =====================================
        // Restaurant Wallet
        // =====================================

        if (restaurantId) {

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                .set({

                    pendingBalance:
                        admin.firestore.FieldValue.increment(-subtotal),

                    availableBalance:
                        admin.firestore.FieldValue.increment(subtotal)

                }, { merge: true });

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                .collection("history")
                .add({

                    type: "Available",

                    amount: subtotal,

                    orderId,

                    date: new Date().toISOString()

                });

        }

        // =====================================
        // Rider Wallet
        // =====================================

        if (riderId && deliveryFee) {

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.DELIVERY, riderId)
                .set({

                    pendingBalance:
                        admin.firestore.FieldValue.increment(-deliveryFee),

                    availableBalance:
                        admin.firestore.FieldValue.increment(deliveryFee)

                }, { merge: true });

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.DELIVERY, riderId)
                .collection("history")
                .add({

                    type: "Available",

                    amount: deliveryFee,

                    orderId,

                    date: new Date().toISOString()

                });

        }

        // =====================================
        // Passenger Notification
        // =====================================

        if (passengerUid) {

           await sendNotification({

    uid: passengerUid,

    role: ROLES.PASSENGER,

    title: "✅ Order Completed",

    body: "Your order has been delivered successfully. Thank you for choosing Pak Train Food.",

    data: {

        orderId,

        status: ORDER_STATUS.COMPLETED

    }

});

        }

        // =====================================
        // Restaurant Notification
        // =====================================

        if (restaurantId) {

           await sendNotification({

    uid: restaurantId,

    role: ROLES.RESTAURANT,

    title: "✅ Order Completed",

    body: "The order has been delivered successfully.",

    data: {

        orderId,

        status: ORDER_STATUS.COMPLETED

    }

});

        }

        // =====================================
        // Rider Notification
        // =====================================

        if (riderId) {

          await sendNotification({

    uid: riderId,

    role: ROLES.DELIVERY,

    title: "🎉 Delivery Completed",

    body: "You have successfully completed the delivery.",

    data: {

        orderId,

        status: ORDER_STATUS.COMPLETED

    }

});

        }

        console.log("Order Completed");

    }
);