const { onCall, HttpsError } = require("firebase-functions/v2/https");

const { getStripeClient, STRIPE_SECRET_KEY } = require("../config/stripe");
const { pkrToUsdCents } = require("../utils/currency");

exports.createPaymentIntent = onCall(
    { secrets: [STRIPE_SECRET_KEY] },
    async (request) => {

        if (!request.auth) {

            throw new HttpsError("unauthenticated", "User not authenticated");

        }

        const { amount } = request.data;

        if (!amount || amount <= 0) {

            throw new HttpsError("invalid-argument", "Invalid order amount");

        }

        const usdCents = pkrToUsdCents(amount);

        try {

            const stripe = getStripeClient();

            const paymentIntent = await stripe.paymentIntents.create({

                amount: usdCents,

                currency: "usd",

                // ============================================
                // Module 3 - authorize now, capture later.
                //
                // "manual" means Stripe places a HOLD on the passenger's
                // card for this amount right now (when they confirm
                // PaymentSheet), but does NOT actually take the money yet.
                // The hold only turns into a real charge when
                // captureOrderPayment (functions/payments/captureOrderPayment.js)
                // runs - which happens when the restaurant accepts the
                // order. If the restaurant never accepts (rejects, or the
                // order times out), releaseOrderAuthorization cancels the
                // hold instead - the passenger's card is never actually
                // charged for an order that never got made.
                // ============================================
                capture_method: "manual",

                metadata: {
                    originalAmountPKR: amount
                }

            });

            return {

                clientSecret: paymentIntent.client_secret,

                // The app saves this onto the order document so the backend
                // knows which PaymentIntent to capture/cancel later.
                paymentIntentId: paymentIntent.id

            };

        } catch (err) {

            // Log the real Stripe error server-side, but also pass a useful
            // message back to the app instead of a bare "internal" error.
            console.error("Stripe paymentIntents.create failed:", err);

            throw new HttpsError(
                "internal",
                err.message || "Payment processing failed. Please try again."
            );

        }
    }
);