const { defineSecret } = require("firebase-functions/params");
const Stripe = require("stripe");

// STRIPE_SECRET_KEY is a Firebase Secret Manager secret (not stored in code or in a file).
// Set it once with:  firebase functions:secrets:set STRIPE_SECRET_KEY
const STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");

/**
 * Returns a Stripe client built from the Secret Manager value.
 * Must be called from inside a function that declares
 * `secrets: [STRIPE_SECRET_KEY]` in its onCall/onRequest options,
 * otherwise the secret will not be available at runtime.
 */
function getStripeClient() {
  return new Stripe(STRIPE_SECRET_KEY.value());
}

module.exports = { getStripeClient, STRIPE_SECRET_KEY };