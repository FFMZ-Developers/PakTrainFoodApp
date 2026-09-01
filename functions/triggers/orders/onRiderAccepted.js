const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const walletHelper = require("../../utils/walletHelper");
const { sendNotification } = require("../../utils/sendNotification");

const {
    ROLES,
    ORDER_STATUS
} = require("../../utils/constants");

exports.onRiderAccepted = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (
            before.orderStatus === "accepted_by_rider" ||
            after.orderStatus !== "accepted_by_rider"
        ) {
            return;
        }

        const riderId = after.acceptedBy;

        // Module: copy the rider's contact details onto the order so the
        // restaurant and passenger can call/chat them without needing read
        // access to the rider's profile document.
        if (riderId) {

            try {

                const riderSnap = await admin.firestore()
                    .collection("Users").doc("Delivery")
                    .collection("VerifiedRegister").doc(riderId).get();

                if (riderSnap.exists) {

                    const r = riderSnap.data();

                    await event.data.after.ref.update({
                        riderPhone: r.phone || null,
                        riderName: r.name || "PakTrain Rider",
                        riderAssignedAt: Date.now()
                    });
                }

            } catch (e) {
                console.error("onRiderAccepted: couldn't copy rider contact details", e);
            }
        }
        const passengerUid = after.passengerUid;
        const restaurantId = after.restaurantId;

        const deliveryFee = after.deliveryFee || 0;
        const orderId = after.orderId;

        if (!riderId || deliveryFee <= 0) {
            return;
        }

        // =========================
        // Rider Wallet
        // =========================

        await walletHelper.walletRef(walletHelper.WALLET_ROLES.DELIVERY, riderId)
            .set({

                pendingBalance:
                    admin.firestore.FieldValue.increment(deliveryFee)

            }, { merge: true });

        await walletHelper.walletRef(walletHelper.WALLET_ROLES.DELIVERY, riderId)
            .collection("history")
            .add({

                type: "Pending",

                amount: deliveryFee,

                orderId,

                date: new Date().toISOString()

            });

        console.log("Rider Pending Wallet Updated");

        // Module 8 - "rider accepted" is NOT one of the fixed passenger
        // milestones. The passenger already knows their order is
        // "preparing"; the next thing they need to hear is "on the way"
        // once a rider actually has the food in hand
        // (onOrderDropped.js) - an intermediate "a rider accepted" ping
        // doesn't change what the passenger needs to do and is just noise.

        // =========================
        // Restaurant Notification
        // =========================

        if (restaurantId) {

            await sendNotification({

    uid: restaurantId,

    role: ROLES.RESTAURANT,

    title: "🛵 Rider Assigned",

    body: "A rider has accepted the order and is coming to collect it.",

    data: {

        orderId,

        status: ORDER_STATUS.ACCEPTED_BY_RIDER

    }

});

        }

        // =========================
        // Rider Notification
        // =========================

       await sendNotification({

    uid: riderId,

    role: ROLES.DELIVERY,

    title: "✅ Order Accepted",

    body: "You accepted the order. Please reach the restaurant.",

    data: {

        orderId,

        status: ORDER_STATUS.ACCEPTED_BY_RIDER

    }

});

    }
);