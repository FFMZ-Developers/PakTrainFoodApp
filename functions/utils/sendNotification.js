const admin = require("../config/firebase");

const {
    ROLES,
    NOTIFICATION_TYPES,
    SCREENS
} = require("./constants");

/**
 * @param persist   Set to false for chat messages - a chat notification
 *                  shouldn't leave a permanent record in the Alerts list
 *                  (the chat thread itself IS the permanent record). When
 *                  false, this only sends the FCM push - no Firestore
 *                  Notifications document is created at all, so there's
 *                  nothing to "delete after reading" because nothing was
 *                  ever saved.
 */
async function sendNotification({
    uid,
    role,
    title,
    body,
    data = {},
    persist = true
}) {

    try {

        if (!uid) {
            console.log("UID Missing");
            return;
        }

        if (!role) {
            console.log("Role Missing");
            return;
        }

        let userRef;

        switch (role) {

            case ROLES.PASSENGER:

                userRef = admin.firestore()
                    .collection("Users")
                    .doc("Passenger")
                    .collection("Register")
                    .doc(uid);

                break;

            case ROLES.RESTAURANT:

                userRef = admin.firestore()
                    .collection("Users")
                    .doc("Restaurant")
                    .collection("VerifiedRegister")
                    .doc(uid);

                break;

           case ROLES.DELIVERY:

                userRef = admin.firestore()
                    .collection("Users")
                    .doc("Delivery")
                    .collection("VerifiedRegister")
                    .doc(uid);

                break;

            default:

                console.log("Invalid Role");

                return;

        }

        // ==========================
        // Module: mention the order number in the body.
        //
        // Every order-lifecycle AND payment notification already carries
        // data.orderId (payment templates included - Held/Sent/Refund*
        // all pass it). Rather than editing every single call site across
        // a dozen trigger files to add "Order #0001" text, this looks it
        // up ONCE here and prefixes it automatically - so it's guaranteed
        // consistent everywhere, including any future notification nobody
        // remembers to update by hand.
        // ==========================

        let finalBody = body;
        let orderNumberForData = data.orderNumber || "";

        if (data.orderId && !orderNumberForData) {

            try {

                const orderSnap = await admin.firestore()
                    .collection("Orders").doc(data.orderId).get();

                if (orderSnap.exists) {

                    const num = orderSnap.data().orderNumber;

                    if (typeof num === "number" && num > 0) {
                        orderNumberForData = String(num).padStart(4, "0");
                    }
                }

            } catch (e) {
                // Non-fatal - the notification still sends, just without
                // the order number prefix.
            }
        }

        if (orderNumberForData) {
            finalBody = `[Order #${orderNumberForData}] ${body}`;
        }

        // ==========================
        // Save Notification (skipped entirely for persist: false)
        // ==========================

        let notificationId = null;

        if (persist) {

            const notificationRef = userRef.collection("Notifications").doc();
            notificationId = notificationRef.id;

            await notificationRef.set({

                notificationId: notificationRef.id,

                title: title,

                body: finalBody,

                image: "",

                type: NOTIFICATION_TYPES.ORDER,

                // Respects whatever the caller passed - see the history of
                // this file for why that matters (it used to be hardcoded).
                screen: data.screen || SCREENS.ORDERS,

                priority: "normal",

                orderId: data.orderId || "",

                orderNumber: orderNumberForData,

                deepLinkId: data.orderId || "",

                status: data.status || "",

                receiverUid: uid,

                receiverRole: role,

                isRead: false,

                clickedAt: null,

                createdAt: admin.firestore.FieldValue.serverTimestamp(),

                updatedAt: null,

                version: 1

            });
        }

        // ==========================
        // Get FCM Token
        // ==========================

        const tokenDoc = await admin.firestore()

            .collection("Users")

            .doc("Notification")

            .collection("FCMTokens")

            .doc(uid)

            .get();

        if (!tokenDoc.exists) {

            console.log("FCM Token Not Found");

            return;

        }

        const token = tokenDoc.data().fcmToken;

        if (!token) {

            console.log("FCM Token Empty");

            return;

        }

        // ==========================
        // Send Push Notification
        // ==========================

        // IMPORTANT: no top-level "notification" block here on purpose.
        // If one is present, Android auto-displays the notification itself
        // whenever the app is backgrounded/killed, WITHOUT ever calling
        // onMessageReceived() - which means the in-app notification toggle
        // (and everything else onMessageReceived does) gets silently
        // bypassed. Sending data-only guarantees onMessageReceived always
        // runs, in the foreground and the background alike.
        await admin.messaging().send({

            token,

            data: {

                ...data,

                title,

                body: finalBody,

                screen: data.screen || SCREENS.ORDERS,

                deepLinkId: data.orderId || "",

                notificationType: NOTIFICATION_TYPES.ORDER,

                priority: "normal",

                orderNumber: orderNumberForData,

                // Lets the app open/mark-read the EXACT Firestore document
                // that was just written above, instead of guessing.
                notificationId: notificationId || "",

                persisted: persist ? "true" : "false"

            }

        });

        console.log("Notification Sent Successfully" + (persist ? "" : " (not persisted - chat)"));

    }
    catch (e) {

        console.error(e);

    }

}

module.exports = {

    sendNotification

};
