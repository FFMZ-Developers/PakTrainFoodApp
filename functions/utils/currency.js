// ============================================================================
// currency.js
//
// Stripe (test mode) doesn't support PKR as a settlement currency, so every
// Stripe API call (charges, transfers, payouts, balances) happens in USD.
// But the app shows the passenger, restaurant, and rider everything in
// Rs/PKR - Firestore's wallet numbers (availableBalance, pendingBalance,
// order totals) are ALWAYS PKR, never touched by this conversion.
//
// This rate is ONLY ever used at the exact moment we call the Stripe API -
// converting a PKR amount into the USD cents Stripe needs. Must match the
// rate used everywhere else a PKR->USD conversion happens (createPaymentIntent.js
// on the charging side, autoPayoutWallets.js and the admin panel's
// payoutToPartner on the payout side), so a Rs 1000 charge and a Rs 1000
// payout convert the same way.
// ============================================================================

const PKR_TO_USD_RATE = 283;
const MIN_USD_CENTS = 50; // Stripe's own minimum charge/transfer for USD

function pkrToUsdCents(pkrAmount) {
    return Math.max(MIN_USD_CENTS, Math.round((Number(pkrAmount) / PKR_TO_USD_RATE) * 100));
}

module.exports = { PKR_TO_USD_RATE, MIN_USD_CENTS, pkrToUsdCents };
