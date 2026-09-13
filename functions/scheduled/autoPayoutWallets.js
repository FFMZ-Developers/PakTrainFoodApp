// ============================================================================
// autoPayoutWallets.js
//
// Module: automatic REAL payouts every 24 hours.
//
// WHAT HAPPENS
// Every 24 hours, this looks at every restaurant and rider wallet with an
// availableBalance > 0:
//
//   - If they have a real, fully-onboarded Stripe Connect account
//     (stripeAccountId starting with "acct_" AND stripeOnboardingComplete),
//     a REAL Stripe transfer is made - the wallet resets to 0, a receipt is
//     recorded, and they get a "Payment Sent" notification.
//
//   - If they DON'T have Stripe connected yet, NO payment attempt is made
//     at all - the balance is left untouched (it keeps accumulating) and
//     they instead get a reminder notification telling them to connect
//     Stripe in their Wallet screen. The very next 24-hour cycle after
//     they connect, this same run will pick up and pay out whatever has
//     built up since. There is no more "simulated" payout path - if
//     Stripe isn't connected, money simply doesn't move until it is.
//
// Passengers are NOT included - they only ever receive refunds (real
// Stripe refunds now - see utils/stripeRefund.js), there's nothing
// recurring to "pay out" on a schedule for them.
// ============================================================================

const { onSchedule } = require("firebase-functions/v2/scheduler");

const admin = require("../config/firebase");
const { getStripeClient, STRIPE_SECRET_KEY } = require("../config/stripe");
const { sendNotification } = require("../utils/sendNotification");
const { pkrToUsdCents } = require("../utils/currency");
const { ROLES } = require("../utils/constants");

const ROLE_CONFIG = [
    {
        walletFolder: "Restaurant",
        profileCollection: "Restaurant",
        notificationRole: ROLES.RESTAURANT
    },
    {
        walletFolder: "Delivery",
        profileCollection: "Delivery",
        notificationRole: ROLES.DELIVERY
    }
];

exports.autoPayoutWallets = onSchedule(
    {
        schedule: "every 24 hours",
        secrets: [STRIPE_SECRET_KEY]
    },
    async () => {

        const db = admin.firestore();
        const stripe = getStripeClient();

        for (const config of ROLE_CONFIG) {

            const accountsSnap = await db
                .collection("Wallets")
                .doc(config.walletFolder)
                .collection("Accounts")
                .get();

            for (const walletDoc of accountsSnap.docs) {

                const uid = walletDoc.id;
                const walletData = walletDoc.data();
                const available = Number(walletData.availableBalance || 0);

                if (available <= 0) continue;

                try {

                    const profileRef = db.collection("Users").doc(config.profileCollection)
                        .collection("VerifiedRegister").doc(uid);

                    const profileSnap = await profileRef.get();

                    if (!profileSnap.exists) {
                        console.log("autoPayoutWallets: no profile for", uid, "- skipping");
                        continue;
                    }

                    const profile = profileSnap.data();
                    const name = profile.restaurantName || profile.name || "Partner";

                    const hasRealStripeAccount =
                        profile.stripeAccountId &&
                        profile.stripeAccountId.startsWith("acct_") &&
                        profile.stripeOnboardingComplete === true;

                    if (!hasRealStripeAccount) {

                        // No real payout rail connected yet - don't touch
                        // the balance, just remind them where to fix that.
                        await sendNotification({
                            uid,
                            role: config.notificationRole,
                            title: "💰 Payment Waiting",
                            body: `You have Rs ${Math.round(available)} ready to be paid out, but your Stripe account isn't connected yet. Open your Wallet and tap "Setup Payments" to receive it.`,
                            data: { screen: "wallet" }
                        });

                        console.log("autoPayoutWallets:", config.walletFolder, uid, "- no Stripe account, sent reminder, balance untouched");
                        continue;
                    }

                    const transfer = await stripe.transfers.create({
                        amount: pkrToUsdCents(available),
                        currency: "usd",
                        destination: profile.stripeAccountId
                    });

                    const receiptId = "RCPT-" + Date.now() + "-" + Math.floor(Math.random() * 10000);
                    const now = new Date().toISOString();

                    const walletRef = db.collection("Wallets").doc(config.walletFolder)
                        .collection("Accounts").doc(uid);

                    await walletRef.update({ availableBalance: 0 });

                    await walletRef.collection("history").add({
                        type: "Auto Payout",
                        amount: available,
                        method: "stripe",
                        receiptId,
                        transferId: transfer.id,
                        orderId: "AUTO_PAYOUT_24H",
                        date: now
                    });

                    const amountRounded = Math.round(available);

                    await sendNotification({
                        uid,
                        role: config.notificationRole,
                        title: "💰 Payment Sent",
                        body: `Rs ${amountRounded} has been sent to your bank via Stripe. Tap to view receipt.`,
                        data: {
                            screen: "wallet",
                            receiptId,
                            amount: String(amountRounded)
                        }
                    });

                    console.log("autoPayoutWallets:", config.walletFolder, uid, "- paid out Rs", amountRounded, "via real Stripe transfer", transfer.id);

                } catch (err) {

                    console.error("autoPayoutWallets: failed for", config.walletFolder, uid, err);
                }
            }
        }
    }
);
