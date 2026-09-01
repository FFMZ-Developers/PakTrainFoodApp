const { onDocumentCreated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const passengerNotifications = require("../../utils/passengerNotifications");

exports.onOrderPlaced = onDocumentCreated(
    "Orders/{orderId}",
    async (event) => {

        const data = event.data.data();

        const {
            orderId,
            passengerUid,
            totalPrice
        } = data;

        // ✅ FIX: admin wallet + restaurant pending-balance crediting used
        // to happen HERE, immediately at order placement - before the
        // restaurant had even seen the order (Module 4 might not surface
        // it for hours, and the restaurant might reject it entirely).
        // That contradicted Module 3's "authorize now, capture later"
        // design - money should only start moving once it's actually
        // captured. Both wallet credits now happen in
        // captureOrderPayment.js, at the moment the restaurant accepts.

        // Module: assign a human-readable sequential order number.
        // Firestore doc ids are random strings - fine as keys, useless to
        // read out over the phone. A transaction on a single counter doc
        // guarantees no two orders ever get the same number, even if
        // several are placed at the same instant.
        try {

            const counterRef = admin.firestore().collection("Counters").doc("orders");

            const assignedNumber = await admin.firestore().runTransaction(async (tx) => {

                const counterSnap = await tx.get(counterRef);

                const next = counterSnap.exists && typeof counterSnap.data().lastNumber === "number"
                    ? counterSnap.data().lastNumber + 1
                    : 1;

                tx.set(counterRef, { lastNumber: next }, { merge: true });

                return next;
            });

            await event.data.ref.update({ orderNumber: assignedNumber });

            console.log("onOrderPlaced: order", orderId, "assigned number", assignedNumber);

        } catch (e) {
            // Non-fatal - the order still works, it just falls back to
            // showing a shortened doc id until this is retried.
            console.error("onOrderPlaced: couldn't assign sequential order number", e);
        }

        // Module - payment lifecycle: "held" receipt entry in the
        // passenger's wallet history, the moment the card hold is placed
        // (not yet charged). Informational only - no balance change.
        if (passengerUid && totalPrice > 0) {

            await admin.firestore()
                .collection("Wallets").doc("Passenger")
                .collection("Accounts").doc(passengerUid)
                .collection("history").add({
                    type: "Payment Held",
                    amount: totalPrice,
                    orderId,
                    date: new Date().toISOString()
                });

            await passengerNotifications.paymentHeld(passengerUid, orderId, totalPrice);
        }

        // Module 8 - "order confirmed" is one of the fixed passenger
        // milestones. No payment amount, no item list, no order id in the
        // visible copy - see passengerNotifications.js for the policy.
        await passengerNotifications.orderConfirmed(passengerUid, orderId);

        console.log("Order Placed Trigger Completed - payment authorized (hold only), wallet crediting deferred to capture");

    }
);
