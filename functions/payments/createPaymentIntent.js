const { onCall } = require("firebase-functions/v2/https");

const { getStripeClient, STRIPE_SECRET_KEY } = require("../config/stripe");

exports.createPaymentIntent = onCall(
    { secrets: [STRIPE_SECRET_KEY] },
    async (request) => {

        if (!request.auth) {

            throw new Error("User not authenticated");

        }

        const { amount } = request.data;

        const stripe = getStripeClient();

        const paymentIntent = await stripe.paymentIntents.create({

            amount: Math.round(amount * 100),

            currency: "PKR"

        });

        return {

            clientSecret: paymentIntent.client_secret

        };

    }
);