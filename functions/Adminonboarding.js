const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const Stripe = require("stripe");

if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

function getStripe() {
  return new Stripe(process.env.STRIPE_SECRET_KEY);
}

// ============================================================
// ADMIN PANEL SE PURANE (EXISTING) RESTAURANT/RIDER KO
// STRIPE SE LINK KARNA — Payments.jsx ke "Link Stripe" button se call hoga
//
// createConnectedAccount se farq: ye onRequest hai (plain fetch se call
// hoti hai, jaisa aapki Payments.jsx mein pattern hai — httpsCallable nahi),
// aur admin kisi bhi purane wallet ke liye trigger kar sakta hai.
// ============================================================
exports.adminLinkStripeAccount = functions
  .runWith({ secrets: ["STRIPE_SECRET_KEY"] })
  .https.onRequest(async (req, res) => {
    const stripe = getStripe();

    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    if (req.method === "OPTIONS") return res.status(204).send("");
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, error: "Method Not Allowed" });
    }

    try {
      const { walletId, type, email, name } = req.body;
      // type: "restaurant" | "delivery"

      if (!walletId || !type || !email) {
        return res.status(400).json({
          success: false,
          error: "walletId, type aur email zaroori hain.",
        });
      }

      const collectionName = type === "restaurant" ? "Restaurant" : "Delivery";
      const registerRef = db.collection("Users").doc(collectionName).collection("Register").doc(walletId);
      const verifiedRef = db.collection("Users").doc(collectionName).collection("VerifiedRegister").doc(walletId);

      const verifiedSnap = await verifiedRef.get();
      let stripeAccountId = verifiedSnap.exists ? verifiedSnap.data().stripeAccountId : null;

      // Agar account pehle se nahi bana to naya banao
      if (!stripeAccountId || !stripeAccountId.startsWith("acct_")) {
        const account = await stripe.accounts.create({
          type: "express",
          country: "US",
          email,
          capabilities: { transfers: { requested: true } },
          business_type: "individual",
        });
        stripeAccountId = account.id;

        // FIX: turant dono jagah save karo (Register + VerifiedRegister)
        // taake Payments.jsx turant "Pending Onboarding" dikha sake
        await registerRef.set(
          { stripeAccountId, stripeOnboardingComplete: false },
          { merge: true }
        );

        if (verifiedSnap.exists) {
          await verifiedRef.set(
            { stripeAccountId, stripeOnboardingComplete: false },
            { merge: true }
          );
        }
      }

      // Onboarding link generate karo — ye link restaurant/rider ko
      // WhatsApp/email se bhejni hai, wahan wo apni (test) bank details bharenge
      const accountLink = await stripe.accountLinks.create({
        account: stripeAccountId,
        refresh_url: "https://yourapp.com/reauth",
        return_url: "https://yourapp.com/onboarding-complete",
        type: "account_onboarding",
      });

      return res.status(200).json({
        success: true,
        stripeAccountId,
        onboardingUrl: accountLink.url,
      });
    } catch (error) {
      console.error("adminLinkStripeAccount error:", error);
      return res.status(500).json({ success: false, error: error.message });
    }
  });

// ============================================================
// ADMIN PANEL SE MANUALLY CHECK KARNA KE ONBOARDING COMPLETE HUI YA NAHI
// (Restaurant/Rider link bhejne ke baad admin "Refresh Status" dabaye)
// ============================================================
exports.adminCheckStripeStatus = functions
  .runWith({ secrets: ["STRIPE_SECRET_KEY"] })
  .https.onRequest(async (req, res) => {
    const stripe = getStripe();

    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    if (req.method === "OPTIONS") return res.status(204).send("");
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, error: "Method Not Allowed" });
    }

    try {
      const { walletId, type, stripeAccountId } = req.body;

      if (!walletId || !type || !stripeAccountId) {
        return res.status(400).json({
          success: false,
          error: "walletId, type aur stripeAccountId zaroori hain.",
        });
      }

      const account = await stripe.accounts.retrieve(stripeAccountId);
      const isComplete = account.charges_enabled && account.payouts_enabled;

      const collectionName = type === "restaurant" ? "Restaurant" : "Delivery";

      if (isComplete) {
        const updates = { stripeOnboardingComplete: true };
        await db.collection("Users").doc(collectionName).collection("Register").doc(walletId).set(updates, { merge: true });
        await db.collection("Users").doc(collectionName).collection("VerifiedRegister").doc(walletId).set(updates, { merge: true });
      }

      return res.status(200).json({
        success: true,
        isComplete,
        chargesEnabled: account.charges_enabled,
        payoutsEnabled: account.payouts_enabled,
      });
    } catch (error) {
      console.error("adminCheckStripeStatus error:", error);
      return res.status(500).json({ success: false, error: error.message });
    }
  });