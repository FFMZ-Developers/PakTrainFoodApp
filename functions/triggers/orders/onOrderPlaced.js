const { onDocumentCreated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const walletHelper = require("../../utils/walletHelper");

const { sendNotification } = require("../../utils/sendNotification");

const {
    ROLES,
    ORDER_STATUS
} = require("../../utils/constants");

exports.onOrderPlaced = onDocumentCreated(
    "Orders/{orderId}",
    async (event) => {

        const data = event.data.data();

        const {
            orderId,
            passengerUid,
            restaurantId,
            subtotal,
            deliveryFee,
            adminFee,
            totalPrice
        } = data;

        // ===============================
        // ADMIN MAIN WALLET
        // ===============================

        await walletHelper.adminWalletRef()
            .set({

                balance:
                    admin.firestore.FieldValue.increment(totalPrice)

            }, { merge: true });

        // ===============================
        // RESTAURANT PENDING WALLET
        // ===============================

        if (restaurantId && subtotal) {

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                .set({

                    pendingBalance:
                        admin.firestore.FieldValue.increment(subtotal)

                }, { merge: true });

            await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                .collection("history")
                .add({

                    type: "Pending",

                    amount: subtotal,

                    orderId: orderId,

                    date: new Date().toISOString()

                });

        }

        // ===============================
        // NOTIFICATION TO RESTAURANT
        // ===============================

        if (restaurantId) {

await sendNotification({

                uid: restaurantId,

                role: ROLES.RESTAURANT,

                title: "\ud83c\udf7d\ufe0f New Order",

                body: "You have received a new order.",

                data: {

                    orderId,

                    status: ORDER_STATUS.ACTIVE

                }

            });

        }

        // ===============================
        // NOTIFICATION TO PASSENGER
        // ===============================

        if (passengerUid) {

           // Short line shows in the collapsed notification; fullMessage is
            // revealed when the passenger expands or taps it.
            const amount = Math.round(totalPrice || 0);

            const itemNames = Array.isArray(data.items)
                ? data.items.map((i) => i.name).filter(Boolean).join(", ")
                : "";

            const detail =
                "Payment of Rs " + amount + " received for order #" + orderId +
                (itemNames ? ".\nItems: " + itemNames : ".") +
                "\nYour order has been placed successfully.";

            await sendNotification({

                uid: passengerUid,

                role: ROLES.PASSENGER,

                title: "\u2705 Payment Successful",

                body: "Rs " + amount + " paid successfully",

                data: {

                    orderId,

                    status: ORDER_STATUS.ACTIVE,

                    fullMessage: detail,

                    amount: String(amount)

                }

            });

        }

        console.log("Order Placed Trigger Completed");

    }
);





















// const { onDocumentCreated } = require("firebase-functions/v2/firestore");

// const admin = require("../../config/firebase");

// const { sendNotification } = require("../../utils/sendNotification");

// exports.onOrderPlaced = onDocumentCreated(
//     "Orders/{orderId}",
//     async (event) => {

//         const data = event.data.data();

//         const {
//             orderId,
//             passengerUid,
//             restaurantId,
//             subtotal,
//             deliveryFee,
//             adminFee,
//             totalPrice
//         } = data;

//         // ===============================
//         // ADMIN MAIN WALLET
//         // ===============================

//         await admin.firestore()
//             .collection("Wallets")
//             .doc("admin_wallet")
//             .set({

//                 balance:
//                     admin.firestore.FieldValue.increment(totalPrice)

//             }, { merge: true });

//         // ===============================
//         // RESTAURANT PENDING WALLET
//         // ===============================

//         if (restaurantId && subtotal) {

//             await admin.firestore()
//                 .collection("Wallets")
//                 .doc(restaurantId)
//                 .set({

//                     pendingBalance:
//                         admin.firestore.FieldValue.increment(subtotal)

//                 }, { merge: true });

//             await admin.firestore()
//                 .collection("Wallets")
//                 .doc(restaurantId)
//                 .collection("history")
//                 .add({

//                     type: "Pending",

//                     amount: subtotal,

//                     orderId: orderId,

//                     date: new Date().toISOString()

//                 });

//         }

//         // ===============================
//         // NOTIFICATION TO RESTAURANT
//         // ===============================

//         if (restaurantId) {

//             await sendNotification(

//                 restaurantId,

//                 "🍽️ New Order",

//                 "You have received a new order.",

//                 {

//                     orderId: orderId,

//                     status: "Active"

//                 }

//             );

//         }

//         // ===============================
//         // NOTIFICATION TO PASSENGER
//         // ===============================

//         if (passengerUid) {

//             await sendNotification(

//                 passengerUid,

//                 "✅ Order Placed",

//                 "Your order has been placed successfully.",

//                 {

//                     orderId: orderId,

//                     status: "Active"

//                 }

//             );

//         }

//         console.log("Order Placed Trigger Completed");

//     }
// );
