const { onCall, HttpsError } = require("firebase-functions/v2/https");

const { getStripeClient, STRIPE_SECRET_KEY } = require("../config/stripe");

// Stripe has no supported presentment/settlement currency for PKR (Pakistan
// isn't a supported Stripe country), so the charge itself must be made in a
// currency Stripe accepts. USD is used here purely for the Stripe API call;
// everything the passenger sees in the app UI stays in Rs/PKR.
const PKR_TO_USD_RATE = 283;
const MIN_USD_CENTS = 50; // Stripe's own minimum charge for USD

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

        const usdCents = Math.max(
            MIN_USD_CENTS,
            Math.round((amount / PKR_TO_USD_RATE) * 100)
        );

        try {

            const stripe = getStripeClient();

            const paymentIntent = await stripe.paymentIntents.create({

                amount: usdCents,

                currency: "usd",

                metadata: {
                    originalAmountPKR: amount
                }

            });

            return {

                clientSecret: paymentIntent.client_secret

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