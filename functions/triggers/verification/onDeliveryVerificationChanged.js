const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const { ROLES } = require("../../utils/constants");
const { handleVerificationStatusChange } = require("./handleVerificationStatusChange");

exports.onDeliveryVerificationChanged = onDocumentUpdated(
    "Users/Delivery/VerifiedRegister/{uid}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();
        const { uid } = event.params;

        await handleVerificationStatusChange(before, after, uid, ROLES.DELIVERY);
    }
);
