const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

const { DEFAULT_ORDER_CONFIG } = require("./defaultOrderConfig");

// ============================================================
// 0) APP-WIDE SETTINGS (rider search radius, ETA thresholds,
//    reliability strike limits, etc) - the Settings tab in this
//    admin panel reads/writes these.
//
//    Unlike the payout functions below, these two verify the
//    caller's identity server-side (via the Firebase ID token
//    sent in the Authorization header) before allowing a write,
//    because these settings gate payment/dispatch behaviour for
//    every order on the platform and are worth the extra check.
// ============================================================

/** Verifies the request's Bearer token belongs to a real admin account. */
async function requireAdmin(req) {

  const authHeader = req.headers.authorization || "";
  const idToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : null;

  if (!idToken) {
    return { ok: false, status: 401, error: "Missing Authorization token." };
  }

  let decoded;
  try {
    decoded = await admin.auth().verifyIdToken(idToken);
  } catch (e) {
    return { ok: false, status: 401, error: "Invalid or expired token." };
  }

  const adminDoc = await db.collection("admins").doc(decoded.uid).get();

  if (!adminDoc.exists) {
    return { ok: false, status: 403, error: "Not an admin account." };
  }

  return { ok: true, uid: decoded.uid };
}

exports.getOrderConfig = functions.https.onRequest(async (req, res) => {

  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    return res.status(204).send("");
  }

  try {
    const ref = db.collection("Settings").doc("orderConfig");
    const snap = await ref.get();

    if (!snap.exists) {
      // Nothing saved yet - hand back the defaults so the Settings
      // screen still has something to display and edit.
      return res.status(200).json({ success: true, config: DEFAULT_ORDER_CONFIG, isDefault: true });
    }

    return res.status(200).json({ success: true, config: snap.data(), isDefault: false });

  } catch (error) {
    console.error("getOrderConfig error:", error);
    return res.status(500).json({ success: false, error: error.message });
  }
});

exports.updateOrderConfig = functions.https.onRequest(async (req, res) => {

  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    return res.status(204).send("");
  }

  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method Not Allowed" });
  }

  const authCheck = await requireAdmin(req);

  if (!authCheck.ok) {
    return res.status(authCheck.status).json({ success: false, error: authCheck.error });
  }

  try {
    const updates = req.body || {};
    const allowedKeys = Object.keys(DEFAULT_ORDER_CONFIG);
    const sanitized = {};

    for (const key of allowedKeys) {
      if (Object.prototype.hasOwnProperty.call(updates, key)) {
        sanitized[key] = updates[key];
      }
    }

    if (Object.keys(sanitized).length === 0) {
      return res.status(400).json({ success: false, error: "No recognised settings fields were provided." });
    }

    await db.collection("Settings").doc("orderConfig")
        .set(sanitized, { merge: true });

    return res.status(200).json({ success: true, updated: Object.keys(sanitized) });

  } catch (error) {
    console.error("updateOrderConfig error:", error);
    return res.status(500).json({ success: false, error: error.message });
  }
});

// 🔑 Stripe secret key config se aa rahi hai
// Set karne ka command (ek dafa terminal mein chalayein):
// firebase functions:config:set stripe.secret_key="sk_test_xxxxxxxxxxxx"
const Stripe = require("stripe");

function getStripe() {
  return new Stripe(process.env.STRIPE_SECRET_KEY);
}
// ============================================================
// 1) ADMIN STRIPE BALANCE FETCH KARNA (GET request)
//
// ⚠️ NOTE: Ye maine RECONSTRUCT kiya hai based on Payments.jsx
// jo response expect kar raha hai. Agar aapke paas ye function
// PEHLE SE likha hua hai aur kaam kar raha hai, tou APNA WALA
// HI RAKHEIN, is wale ko delete/ignore kar dein — taake purana
// working code na tootay.
// ============================================================
exports.getAdminBalance = functions
  .runWith({
    secrets: ["STRIPE_SECRET_KEY"],
  })
  .https.onRequest(async (req, res) => {
         const stripe = getStripe();

  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    return res.status(204).send("");
  }

  try {
    const balance = await stripe.balance.retrieve();

    return res.status(200).json({
      success: true,
      available: balance.available,
      pending: balance.pending,
    });
  } catch (error) {
    console.error("Error fetching Stripe balance:", error);
    return res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});


// ============================================================
// 2) RESTAURANT / RIDER SIGNUP KE WAQT STRIPE CONNECTED ACCOUNT
//    Android app se call hota hai (Firebase Functions SDK / onCall)
// ============================================================
exports.createConnectedAccount = functions
  .runWith({
    secrets: ["STRIPE_SECRET_KEY"],
  })
  .https.onCall(async (data, context) => {

    const stripe = getStripe();
  const { email, uid, type } = data;

  if (!email || !uid || !type) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Email, uid aur type zaroori hain"
    );
  }

  try {
    // Step 1: Stripe Test Connected Account create karo (sandbox mein)
    const account = await stripe.accounts.create({
      type: "express",
      country: "US", // sandbox/test mode mein fix rakhte hain
      email: email,
      capabilities: {
        transfers: { requested: true },
      },
      business_type: "individual",
    });

    // Step 2: Firestore mein sahi collection choose karo
    const collectionName = type === "restaurant" ? "Restaurant" : "Delivery";

    // Step 3: Existing signup document ko update karo (Register collection)
    await db
      .collection("Users")
      .doc(collectionName)
      .collection("Register")
      .doc(uid)
      .update({
        stripeAccountId: account.id,
        stripeOnboardingComplete: false,
      });

    // Step 4: Onboarding link generate karo (test details fill karne ke liye)
    const accountLink = await stripe.accountLinks.create({
      account: account.id,
      refresh_url: "https://yourapp.com/reauth",
      return_url: "https://yourapp.com/onboarding-complete",
      type: "account_onboarding",
    });

    return {
      success: true,
      accountId: account.id,
      onboardingUrl: accountLink.url,
    };
  } catch (error) {
    throw new functions.https.HttpsError("internal", error.message);
  }
});


// ============================================================
// 3) ONBOARDING STATUS CHECK KARNA
//    (Restaurant/Rider profile screen se call ho sakta hai)
// ============================================================
exports.checkStripeAccountStatus = functions
  .runWith({
    secrets: ["STRIPE_SECRET_KEY"],
  })
  .https.onCall(async (data, context) => {

    const stripe = getStripe();
  const { stripeAccountId, uid, type } = data;

  if (!stripeAccountId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "stripeAccountId zaroori hai"
    );
  }

  try {
    const account = await stripe.accounts.retrieve(stripeAccountId);

    const isComplete = account.charges_enabled && account.payouts_enabled;

    if (isComplete && uid && type) {
      const collectionName = type === "restaurant" ? "Restaurant" : "Delivery";

      // Register collection mein update
      await db
        .collection("Users")
        .doc(collectionName)
        .collection("Register")
        .doc(uid)
        .update({ stripeOnboardingComplete: true });

      // Agar VerifiedRegister document bhi maujood hai, wahan bhi update karo
      // (kyunki Payments.jsx isi collection se stripeAccountId read karta hai)
      const verifiedRef = db
        .collection("Users")
        .doc(collectionName)
        .collection("VerifiedRegister")
        .doc(uid);

      const verifiedSnap = await verifiedRef.get();
      if (verifiedSnap.exists) {
        await verifiedRef.update({
          stripeAccountId: stripeAccountId,
          stripeOnboardingComplete: true,
        });
      }
    }

    return {
      isComplete,
      chargesEnabled: account.charges_enabled,
      payoutsEnabled: account.payouts_enabled,
    };
  } catch (error) {
    throw new functions.https.HttpsError("internal", error.message);
  }
});


// ============================================================
// 4) ADMIN SE RESTAURANT/RIDER KO REAL STRIPE SANDBOX PAYOUT
//    (POST request - React Admin Panel se fetch() se call hota hai)
// ============================================================
exports.payoutToPartner = functions
  .runWith({
    secrets: ["STRIPE_SECRET_KEY"],
  })
  .https.onRequest(async (req, res) => {

    const stripe = getStripe();
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    return res.status(204).send("");
  }

  if (req.method !== "POST") {
    return res.status(405).json({
      success: false,
      error: "Method Not Allowed",
    });
  }

  try {
    const { walletId, amount, stripeAccountId, receiverType, name } = req.body;

    if (!walletId || !amount || !stripeAccountId) {
      return res.status(400).json({
        success: false,
        error: "Bad Request: walletId, amount, aur stripeAccountId zaroori hain.",
      });
    }

    if (
      typeof stripeAccountId !== "string" ||
      !stripeAccountId.startsWith("acct_")
    ) {
      return res.status(400).json({
        success: false,
        error: "Invalid Stripe Connected Account ID.",
      });
    }

    // ===================================================
    // REAL STRIPE SANDBOX TRANSFER
    // Admin ke test balance se receiver ke connected
    // account mein transfer hoga (sandbox mein hi hota hai)
    // ===================================================
    const transfer = await stripe.transfers.create({
      amount: Math.round(Number(amount) * 100), // cents mein
      currency: "usd", // ⚠️ Admin balance jis currency mein hai, usi se match karein
                        // (agar Admin balance "eur" mein hai, tou "eur" karein)
      destination: stripeAccountId,
      description: `Payout to ${name || receiverType || "partner"} (wallet: ${walletId})`,
    });

    return res.status(200).json({
      success: true,
      message: "Real Stripe sandbox transfer completed successfully.",
      transferId: transfer.id,
      amount: Number(amount),
      status: "succeeded",
      arrivalDate: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Stripe transfer error:", error);
    return res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});


// ============================================================
// 5) OLD MOCK FUNCTION — ab is ki zaroorat nahi
//    (payoutToPartner isko replace kar chuka hai real transfer se)
//    Chahein tou delete kar dein, ya rakh dein (harm nahi karega)
// ============================================================
exports.simulateSandboxPayout = functions.https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    return res.status(204).send("");
  }

  if (req.method !== "POST") {
    return res.status(405).json({
      success: false,
      error: "Method Not Allowed",
    });
  }

  try {
    const { walletId, amount, role, name } = req.body;

    if (!walletId || typeof amount === "undefined") {
      return res.status(400).json({
        success: false,
        error: "Bad Request: Missing parameters.",
      });
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));

    const mockTransferId = "tr_sandbox_" + Math.random().toString(36).substring(2, 15).toUpperCase();

    return res.status(200).json({
      success: true,
      message: "Stripe Connect Sandbox Node resolved successfully.",
      transferId: mockTransferId,
      amount: Number(amount),
      status: "succeeded",
      arrivalDate: new Date().toISOString()
    });

  } catch (error) {
    console.error("Critical Cloud Function Exception Trace:", error);
    return res.status(500).json({
      success: false,
      error: "Internal Server Processing Crash"
    });
  }
});