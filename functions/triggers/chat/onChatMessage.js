// ============================================================================
// onChatMessage.js
//
// Module: chat push notifications.
//
// Fires whenever a message is added to either of an order's two chat
// threads and notifies the OTHER party - never the sender. Which two
// people are in a thread depends on which thread it is:
//
//   chats_restaurant : rider  <->  restaurant
//   chats_passenger  : rider  <->  passenger
//
// The notification carries screen: "chat" plus the orderId and chatType,
// so tapping it opens that exact conversation (see MainActivity's
// handleNotificationIntent) rather than just the app's home screen.
// ============================================================================

const { onDocumentCreated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const { sendNotification } = require("../../utils/sendNotification");
const { ROLES } = require("../../utils/constants");

async function notifyChatCounterparty(event, chatType) {

    const message = event.data.data();
    const orderId = event.params.orderId;

    if (!message || !message.senderId) return;

    const orderSnap = await admin.firestore().collection("Orders").doc(orderId).get();

    if (!orderSnap.exists) return;

    const order = orderSnap.data();

    const riderId = order.acceptedBy;

    const otherPartyId = chatType === "restaurant"
        ? order.restaurantId
        : order.passengerUid;

    const otherPartyRole = chatType === "restaurant"
        ? ROLES.RESTAURANT
        : ROLES.PASSENGER;

    // Work out who did NOT send this message - that's who gets notified.
    let recipientUid = null;
    let recipientRole = null;

    if (message.senderId === riderId) {
        recipientUid = otherPartyId;
        recipientRole = otherPartyRole;
    } else if (message.senderId === otherPartyId) {
        recipientUid = riderId;
        recipientRole = ROLES.DELIVERY;
    } else {
        console.log("onChatMessage: sender", message.senderId,
            "is neither party on order", orderId, "- not notifying anyone");
        return;
    }

    if (!recipientUid) return;

    const preview = (message.text || "").length > 80
        ? message.text.substring(0, 80) + "..."
        : (message.text || "New message");

    await sendNotification({
        uid: recipientUid,
        role: recipientRole,
        title: message.senderName || "New message",
        body: preview,
        data: {
            screen: "chat",
            orderId,
            chatType,
            senderName: message.senderName || ""
        },
        // Module: chat notifications don't get saved to the Alerts list -
        // the chat thread itself is the permanent record. Only the push
        // is sent; tapping it opens the conversation directly and there's
        // nothing left over to mark-read or delete afterward.
        persist: false
    });

    console.log("onChatMessage: notified", recipientUid, "about", chatType, "message on order", orderId);
}

exports.onRestaurantChatMessage = onDocumentCreated(
    "Orders/{orderId}/chats_restaurant/{messageId}",
    async (event) => {
        try {
            await notifyChatCounterparty(event, "restaurant");
        } catch (e) {
            console.error("onRestaurantChatMessage failed", e);
        }
    }
);

exports.onPassengerChatMessage = onDocumentCreated(
    "Orders/{orderId}/chats_passenger/{messageId}",
    async (event) => {
        try {
            await notifyChatCounterparty(event, "passenger");
        } catch (e) {
            console.error("onPassengerChatMessage failed", e);
        }
    }
);
