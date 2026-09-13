exports.createPaymentIntent =
require("./payments/createPaymentIntent").createPaymentIntent;

// Module 3 - authorize now, capture later
exports.captureOrderPayment =
require("./payments/captureOrderPayment").captureOrderPayment;

exports.onOrderPaymentReversal =
require("./payments/onOrderPaymentReversal").onOrderPaymentReversal;

// Order Triggers

exports.onOrderPlaced =
require("./triggers/orders/onOrderPlaced").onOrderPlaced;

exports.onRestaurantAccepted =
require("./triggers/orders/onRestaurantAccepted").onRestaurantAccepted;

exports.onReadyForDelivery =
require("./triggers/orders/onReadyForDelivery").onReadyForDelivery;

// Module 5 - expanding-radius rider dispatch
exports.dispatchRider =
require("./dispatch/dispatchRider").dispatchRider;

exports.onRiderAccepted =
require("./triggers/orders/onRiderAccepted").onRiderAccepted;

exports.onRiderArrived =
require("./triggers/orders/onRiderArrived").onRiderArrived;

exports.onOrderDropped =
require("./triggers/orders/onOrderDropped").onOrderDropped;

exports.onOrderPickedUp =
require("./triggers/orders/onOrderPickedUp").onOrderPickedUp;

exports.onOrderCompleted =
require("./triggers/orders/onOrderCompleted").onOrderCompleted;

// Module 6 - failure-mode handling
exports.checkPrepDeadlines =
require("./scheduled/checkPrepDeadlines").checkPrepDeadlines;

exports.onDeliveryFailed =
require("./triggers/orders/onDeliveryFailed").onDeliveryFailed;

exports.checkStalledJourneys =
require("./scheduled/checkStalledJourneys").checkStalledJourneys;

// Module - automatic payouts every 24 hours
exports.autoPayoutWallets =
require("./scheduled/autoPayoutWallets").autoPayoutWallets;

// Module 4 - restaurant order surfacing + prep deadline
exports.onOrderEtaThresholdReached =
require("./triggers/orders/onOrderEtaThresholdReached").onOrderEtaThresholdReached;

exports.onOrderAcceptedSetPrepDeadline =
require("./triggers/orders/onOrderAcceptedSetPrepDeadline").onOrderAcceptedSetPrepDeadline;

// Module 2 - keeps trainEtaEndTime fresh automatically as GPS updates
// arrive, even if nobody has the live-tracking screen open.
exports.onLocationUpdated =
require("./triggers/orders/onLocationUpdated").onLocationUpdated;

// Verification Triggers (Module 1)

exports.onRestaurantVerificationChanged =
require("./triggers/verification/onRestaurantVerificationChanged").onRestaurantVerificationChanged;

exports.onDeliveryVerificationChanged =
require("./triggers/verification/onDeliveryVerificationChanged").onDeliveryVerificationChanged;

// Module 7 - reliability score/level
exports.onRiderReliabilityMirror =
require("./triggers/verification/onRiderReliabilityMirror").onRiderReliabilityMirror;













// const { onCall } = require("firebase-functions/v2/https");
// const { onDocumentCreated } = require("firebase-functions/v2/firestore");
// const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
// const admin = require("firebase-admin");
// const stripe = require("stripe")("sk_test_51T2vhzDKgsKyivl6Oa9EOYPoVo92u0caUiGvJpwOwzgYMNhGBbrPOnmE9zMTcqQILGU3m6b0tWe3TgPSIB88rNR000UuZKzW8O");

// admin.initializeApp();

// async function sendNotification(uid, title, body, data = {}) {

//     try {

//         const tokenDoc = await admin.firestore()
//             .collection("Users")
//             .doc("Notification")
//           .collection("FCMTokens")
//             .doc(uid)
//             .get();

//         if (!tokenDoc.exists) {
//             console.log("FCM Token not found:", uid);
//             return;
//         }

//         const token = tokenDoc.data().fcmToken;

//         if (!token) {
//             console.log("Token is empty");
//             return;
//         }

//         await admin.messaging().send({

//             token: token,

//             notification: {
//                 title: title,
//                 body: body
//             },

//             data: data

//         });

//         console.log("Notification Sent");

//     } catch (e) {

//         console.error(e);

//     }

// }
// // ========================================
// // PAYMENT INTENT
// // ========================================
// exports.createPaymentIntent = onCall(async (request) => {

//     if (!request.auth) {
//         throw new Error("User not authenticated");
//     }

//     const { amount } = request.data;

//     const paymentIntent = await stripe.paymentIntents.create({
//         amount: Math.round(amount * 100),
//         currency: "PKR",
//     });

//     return {
//         clientSecret: paymentIntent.client_secret
//     };
// });


// // ========================================
// // ORDER CREATED
// // ========================================
// exports.onOrderPlaced = onDocumentCreated(
//     "Orders/{orderId}",
//     async (event) => {

//         const data = event.data.data();

//         const {
//             orderId,
//               passengerUid,
//              restaurantId,
//             subtotal,
//             deliveryFee,
//             adminFee,
//             totalPrice
//         } = data;

//         // ADMIN MAIN WALLET
//         await admin.firestore()
//             .collection("Wallets")
//             .doc("admin_wallet")
//             .set({
//                 balance: admin.firestore.FieldValue.increment(totalPrice)
//             }, { merge: true });

//         // RESTAURANT PENDING
//         if (restaurantId && subtotal) {

//             await admin.firestore()
//                 .collection("Wallets")
//                 .doc(restaurantId)
//                 .set({
//                     pendingBalance:
//                         admin.firestore.FieldValue.increment(subtotal),
//                 }, { merge: true });

//             await admin.firestore()
//                 .collection("Wallets")
//                 .doc(restaurantId)
//                 .collection("history")
//                 .add({
//                     type: "Pending",
//                     amount: subtotal,
//                     orderId,
//                     date: new Date().toISOString()
//                 });
//         }

//     // ===============================
// // Notification To Restaurant
// // ===============================

// if (restaurantId) {

//     await sendNotification(
//         restaurantId,
//         "🍽️ New Order",
//         "You have received a new order.",
//         {
//             orderId: orderId,
//             status: "Active"
//         }
//     );

// }

// // ===============================
// // Notification To Passenger
// // ===============================

// if (passengerUid) {

//     await sendNotification(
//         passengerUid,
//         "✅ Order Placed",
//         "Your order has been placed successfully.",
//         {
//             orderId: orderId,
//             status: "Active"
//         }
//     );

// }
//     }
// );

// exports.onRestaurantAccepted = onDocumentUpdated(
//     "Orders/{orderId}",
//     async (event) => {

//         const before = event.data.before.data();
//         const after = event.data.after.data();

//         if (
//             before.orderStatus === "Accepted" ||
//             after.orderStatus !== "Accepted"
//         ) {
//             return;
//         }

//         const passengerUid = after.passengerUid;
//         const orderId = after.orderId;

//         if (passengerUid) {

//             await sendNotification(
//                 passengerUid,
//                 "🍽️ Order Accepted",
//                 "Your order has been accepted by the restaurant and is being prepared.",
//                 {
//                     orderId: orderId,
//                     status: "Accepted"
//                 }
//             );

//         }

//         console.log("Restaurant Accepted Notification Sent");

//     }
// );

// exports.onReadyForDelivery = onDocumentUpdated(
//     "Orders/{orderId}",
//     async (event) => {

//         const before = event.data.before.data();
//         const after = event.data.after.data();

//         if (
//             before.orderStatus === "ready_for_delivery" ||
//             after.orderStatus !== "ready_for_delivery"
//         ) {
//             return;
//         }

//         const passengerUid = after.passengerUid;
//         const orderId = after.orderId;
//         const mealStation = after.mealStation;

//         // Passenger Notification
//         if (passengerUid) {

//             await sendNotification(
//                 passengerUid,
//                 "📦 Order Ready",
//                 "Your order has been prepared and is ready for delivery.",
//                 {
//                     orderId: orderId,
//                     status: "ready_for_delivery"
//                 }
//             );

//         }

//         // Find Riders of Same City (Meal Station)
//         const riders = await admin.firestore()
//             .collection("Users")
//             .doc("Delivery")
//             .collection("VerifiedRegister")
//            .where("city", "==", mealStation)
//             .where("isVerified", "==", true)
//             .get();

//         for (const rider of riders.docs) {

//             await sendNotification(
//                 rider.id,
//                 "🛵 New Delivery Order",
//                 "A new delivery order is available near you.",
//                 {
//                     orderId: orderId,
//                     status: "ready_for_delivery"
//                 }
//             );

//         }

//         console.log("Ready For Delivery Notifications Sent");

//     }
// );
//  // =========================
//         // Order Accepted By Rider
//         // =========================

// exports.onRiderAccepted = onDocumentUpdated(
//     "Orders/{orderId}",
//     async (event) => {

//         const before = event.data.before.data();
//         const after = event.data.after.data();

//         if (
//             before.orderStatus === "accepted_by_rider" ||
//             after.orderStatus !== "accepted_by_rider"
//         ) {
//             return;
//         }

//         const riderId = after.acceptedBy;
//         const passengerUid = after.passengerUid;
//         const restaurantId = after.restaurantId;

//         const deliveryFee = after.deliveryFee || 0;
//         const orderId = after.orderId;

//         if (!riderId || deliveryFee <= 0) {
//             return;
//         }

//         // =========================
//         // Rider Wallet
//         // =========================

//         await admin.firestore()
//             .collection("Wallets")
//             .doc(riderId)
//             .set({
//                 pendingBalance:
//                     admin.firestore.FieldValue.increment(deliveryFee),
//             }, { merge: true });

//         await admin.firestore()
//             .collection("Wallets")
//             .doc(riderId)
//             .collection("history")
//             .add({
//                 type: "Pending",
//                 amount: deliveryFee,
//                 orderId,
//                 date: new Date().toISOString()
//             });

//         console.log("Rider Pending Wallet Updated");

//         // =========================
//         // Passenger Notification
//         // =========================

//         if (passengerUid) {

//             await sendNotification(
//                 passengerUid,
//                 "🛵 Rider Accepted",
//                 "A rider has accepted your order and is on the way to the restaurant.",
//                 {
//                     orderId,
//                     status: "accepted_by_rider"
//                 }
//             );

//         }

//         // =========================
//         // Restaurant Notification
//         // =========================

//         if (restaurantId) {

//             await sendNotification(
//                 restaurantId,
//                 "🛵 Rider Assigned",
//                 "A rider has accepted the order and is coming to collect it.",
//                 {
//                     orderId,
//                     status: "accepted_by_rider"
//                 }
//             );

//         }

//         // =========================
//         // Rider Notification
//         // =========================

//         await sendNotification(
//             riderId,
//             "✅ Order Accepted",
//             "You accepted the order. Please reach the restaurant.",
//             {
//                 orderId,
//                 status: "accepted_by_rider"
//             }
//         );

//     }
// );
// // ========================================
// // rider arrive at resturent
// // ========================================
// exports.onRiderArrived = onDocumentUpdated(
//     "Orders/{orderId}",
//     async (event) => {

//         const before = event.data.before.data();
//         const after = event.data.after.data();

//         if (
//             before.orderStatus === "arrive_rider_at_resturent" ||
//             after.orderStatus !== "arrive_rider_at_resturent"
//         ) {
//             return;
//         }

//         const passengerUid = after.passengerUid;
//         const restaurantId = after.restaurantId;
//         const riderId = after.acceptedBy;
//         const orderId = after.orderId;

//         // Passenger

//         if (passengerUid) {

//             await sendNotification(
//                 passengerUid,
//                 "📍 Rider Arrived",
//                 "The rider has arrived at the restaurant to collect your order.",
//                 {
//                     orderId,
//                     status: "arrive_rider_at_resturent"
//                 }
//             );

//         }

//         // Restaurant

//         if (restaurantId) {

//             await sendNotification(
//                 restaurantId,
//                 "📍 Rider Arrived",
//                 "The rider has arrived. Please hand over the order.",
//                 {
//                     orderId,
//                     status: "arrive_rider_at_resturent"
//                 }
//             );

//         }

//         // Rider

//         if (riderId) {

//             await sendNotification(
//                 riderId,
//                 "📍 Arrival Confirmed",
//                 "You have arrived at the restaurant. Wait for the restaurant to hand over the order.",
//                 {
//                     orderId,
//                     status: "arrive_rider_at_resturent"
//                 }
//             );

//         }

//         console.log("Rider Arrived Notifications Sent");

//     }
// );

// // ========================================
// // ORDER droped
// // ========================================
// exports.onOrderDropped = onDocumentUpdated(
//     "Orders/{orderId}",
//     async (event) => {

//         const before = event.data.before.data();
//         const after = event.data.after.data();

//         if (
//             before.orderStatus === "dropped" ||
//             after.orderStatus !== "dropped"
//         ) {
//             return;
//         }

//         const riderId = after.acceptedBy;
//         const passengerUid = after.passengerUid;
//         const restaurantId = after.restaurantId;
//         const orderId = after.orderId;

//         // Rider
//         if (riderId) {

//             await sendNotification(
//                 riderId,
//                 "📦 Order Ready for Pickup",
//                 "The restaurant has handed over the order. Please pick it up.",
//                 {
//                     orderId,
//                     status: "dropped"
//                 }
//             );

//         }

//         // Passenger
//         if (passengerUid) {

//             await sendNotification(
//                 passengerUid,
//                 "🍽️ Order Handover",
//                 "The restaurant has handed your order to the rider.",
//                 {
//                     orderId,
//                     status: "dropped"
//                 }
//             );

//         }

//         // Restaurant
//         if (restaurantId) {

//             await sendNotification(
//                 restaurantId,
//                 "✅ Order Handed Over",
//                 "You have successfully handed over the order to the rider.",
//                 {
//                     orderId,
//                     status: "dropped"
//                 }
//             );

//         }

//         console.log("Dropped Notifications Sent");

//     }
// );
// // ========================================
// // ORDER pickup
// // ========================================
// exports.onOrderPickedUp = onDocumentUpdated(
//     "Orders/{orderId}",
//     async (event) => {

//         const before = event.data.before.data();
//         const after = event.data.after.data();

//         if (
//             before.orderStatus === "pick_up" ||
//             after.orderStatus !== "pick_up"
//         ) {
//             return;
//         }

//         const riderId = after.acceptedBy;
//         const passengerUid = after.passengerUid;
//         const restaurantId = after.restaurantId;
//         const orderId = after.orderId;

//         // Passenger
//         if (passengerUid) {

//             await sendNotification(
//                 passengerUid,
//                 "🛵 Order Picked Up",
//                 "Your order has been picked up and is on the way.",
//                 {
//                     orderId,
//                     status: "pick_up"
//                 }
//             );

//         }

//         // Restaurant
//         if (restaurantId) {

//             await sendNotification(
//                 restaurantId,
//                 "📦 Order Picked Up",
//                 "The rider has picked up the order and is delivering it.",
//                 {
//                     orderId,
//                     status: "pick_up"
//                 }
//             );

//         }

//         // Rider
//         if (riderId) {

//             await sendNotification(
//                 riderId,
//                 "🚚 Delivery Started",
//                 "You have picked up the order. Deliver it to the passenger.",
//                 {
//                     orderId,
//                     status: "pick_up"
//                 }
//             );

//         }

//         console.log("Pick Up Notifications Sent");

//     }
// );


// // ========================================
// // ORDER COMPLETED
// // ========================================
// exports.onOrderCompleted = onDocumentUpdated(
//     "Orders/{orderId}",
//     async (event) => {


//         const before = event.data.before.data();
//         const after = event.data.after.data();
//        const passengerUid = after.passengerUid;
//         const restaurantId = after.restaurantId;
//         const riderId = after.acceptedBy;
//         const orderId = after.orderId;
//         if (
//             before.orderStatus === "completed" ||
//             after.orderStatus !== "completed"
//         ) {
//             return;
//         }

    
//         const subtotal = after.subtotal || 0;
//         const deliveryFee = after.deliveryFee || 0;
     

//         // RESTAURANT
//         if (restaurantId) {

//             await admin.firestore()
//                 .collection("Wallets")
//                 .doc(restaurantId)
//                 .set({
//                     pendingBalance:
//                         admin.firestore.FieldValue.increment(-subtotal),

//                     availableBalance:
//                         admin.firestore.FieldValue.increment(subtotal)

//                 }, { merge: true });

//             await admin.firestore()
//                 .collection("Wallets")
//                 .doc(restaurantId)
//                 .collection("history")
//                 .add({
//                     type: "Available",
//                     amount: subtotal,
//                     orderId,
//                     date: new Date().toISOString()
//                 });
//         }

//         // RIDER
//       // RIDER
// if (riderId && deliveryFee) {

//     await admin.firestore()
//         .collection("Wallets")
//         .doc(riderId)
//         .set({
//             pendingBalance:
//                 admin.firestore.FieldValue.increment(-deliveryFee),

//             availableBalance:
//                 admin.firestore.FieldValue.increment(deliveryFee)

//         }, { merge: true });

//     await admin.firestore()
//         .collection("Wallets")
//         .doc(riderId)
//         .collection("history")
//         .add({
//             type: "Available",
//             amount: deliveryFee,
//             orderId,
//             date: new Date().toISOString()
//         });
// }
// // ======================================
// // Passenger Notification
// // ======================================

// if (passengerUid) {

//     await sendNotification(
//         passengerUid,
//         "✅ Order Completed",
//         "Your order has been delivered successfully. Thank you for choosing Pak Train Food.",
//         {
//             orderId,
//             status: "completed"
//         }
//     );

// }

// // ======================================
// // Restaurant Notification
// // ======================================

// if (restaurantId) {

//     await sendNotification(
//         restaurantId,
//         "✅ Order Completed",
//         "The order has been delivered successfully.",
//         {
//             orderId,
//             status: "completed"
//         }
//     );

// }

// // ======================================
// // Rider Notification
// // ======================================

// if (riderId) {

//     await sendNotification(
//         riderId,
//         "🎉 Delivery Completed",
//         "You have successfully completed the delivery.",
//         {
//             orderId,
//             status: "completed"
//         }
//     );

// }
//         console.log("Order Completed");
//     }
// );

// Module - chat push notifications
exports.onRestaurantChatMessage =
require("./triggers/chat/onChatMessage").onRestaurantChatMessage;

exports.onPassengerChatMessage =
require("./triggers/chat/onChatMessage").onPassengerChatMessage;

// Module - restaurant rating aggregation
exports.onReviewWritten =
require("./triggers/reviews/onReviewWritten").onReviewWritten;
