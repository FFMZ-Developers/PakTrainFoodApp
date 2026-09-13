const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

// ✅ This trigger's job (telling the passenger their order was accepted)
// moved into captureOrderPayment.js, which fires on the exact same
// orderStatus -> "Accepted" transition. Doing it there instead means the
// passenger is told "payment processed" only once the Stripe capture
// actually succeeds - sending it from here (a separate trigger on the
// same event) could fire before/regardless of whether the capture
// succeeded, since Firestore doesn't guarantee ordering between two
// triggers on the same write.
//
// Kept as a registered no-op (rather than removed) purely so `firebase
// deploy` doesn't prompt to delete it - avoids a confusing decision for
// whoever runs the next deploy.
exports.onRestaurantAccepted = onDocumentUpdated(
    "Orders/{orderId}",
    async () => {
        return null;
    }
);
