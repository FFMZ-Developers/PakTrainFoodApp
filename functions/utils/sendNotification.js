const admin = require("../config/firebase");

const {

    ROLES,

    NOTIFICATION_TYPES,

    SCREENS

} = require("./constants");

async function sendNotification({
    uid,
    role,
    title,
    body,
    data = {}
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
        // Save Notification
        // ==========================

        const notificationRef =
userRef.collection("Notifications").doc();

await notificationRef.set({

    notificationId: notificationRef.id,

    title: title,

    body: body,

    image: "",

    type: NOTIFICATION_TYPES.ORDER,

    screen: SCREENS.ORDERS,

    priority: "normal",

    orderId: data.orderId || "",

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

                body,

                screen: SCREENS.ORDERS,

                 deepLinkId: data.orderId || "",

                  notificationType: NOTIFICATION_TYPES.ORDER,

                 priority: "normal"

            }

        });

        console.log("Notification Sent Successfully");

    }
    catch (e) {

        console.error(e);

    }

}

module.exports = {

    sendNotification

};


















// 
// const admin = require("../config/firebase");

// async function sendNotification(uid, title, body, data = {}) {

//     try {

//         const tokenDoc = await admin.firestore()
//             .collection("Users")
//             .doc("Notification")
//             .collection("FCMTokens")
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

//     }
//     catch (e) {

//         console.error(e);

//     }

// }

// module.exports = {
//     sendNotification
// };