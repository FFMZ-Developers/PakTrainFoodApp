const { sendNotification } = require("../../utils/sendNotification");
const { NOTIFICATION_TYPES, SCREENS } = require("../../utils/constants");

/**
 * Shared handler for both the Restaurant and Delivery verification-status
 * triggers below - one notification template/mechanism for both roles,
 * per the design ("restaurant aur rider dono same rakhna hai").
 *
 * Fires only on the actual transition into "verified" or "rejected", not
 * on every write to the profile document (e.g. the applicant editing their
 * own draft shouldn't re-notify them).
 */
async function handleVerificationStatusChange(beforeData, afterData, uid, role) {

    const before = beforeData || {};
    const after = afterData || {};

    const statusChanged = before.verificationStatus !== after.verificationStatus;

    if (!statusChanged) return;

    if (after.verificationStatus === "verified") {

        await sendNotification({
            uid,
            role,
            title: "\u2705 Documents Verified",
            body: "Your documents have been verified. You can now log in.",
            data: {
                fullMessage:
                    "Your documents have been verified - you can now log in and start using your account.",
                notificationType: NOTIFICATION_TYPES.VERIFICATION,
                screen: SCREENS.PROFILE,
            },
        });

    } else if (after.verificationStatus === "rejected") {

        const reason = after.rejectionReason || "No reason provided.";

        await sendNotification({
            uid,
            role,
            title: "\u26A0\uFE0F Verification Update Needed",
            body: "Your submission needs a small update before it can be approved.",
            data: {
                fullMessage:
                    "Your verification was not approved. Reason: " + reason +
                    " Please update your details in the app and resubmit.",
                notificationType: NOTIFICATION_TYPES.VERIFICATION,
                screen: SCREENS.PROFILE,
            },
        });
    }
}

module.exports = { handleVerificationStatusChange };
