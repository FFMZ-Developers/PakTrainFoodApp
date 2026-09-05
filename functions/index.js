const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

const { DEFAULT_ORDER_CONFIG } = require("./defaultOrderConfig");

// ============================================================
// CURRENCY CONVERSION
//
// Stripe (test mode) doesn't support PKR as a settlement currency - every
// Stripe API call (charges, transfers, balances) happens in USD. But the
// APP shows the passenger, restaurant, and rider everything in Rs/PKR -
// Firestore's wallet numbers (availableBalance, pendingBalance, order
// totals) are ALWAYS PKR, never touched by this conversion.
//
// This rate is ONLY used at the exact moment we call the Stripe API
// (payoutToPartner below) - converting a PKR amount into the USD cents
// Stripe needs. Must match createPaymentIntent.js's rate in the main
// app's functions project, so a Rs 1000 payout and a Rs 1000 charge
// convert the same way.
// ============================================================
const PKR_TO_USD_RATE = 283;

function pkrToUsdCents(pkrAmount) {
  return Math.max(50, Math.round((Number(pkrAmount) / PKR_TO_USD_RATE) * 100));
}

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

/**
 * Verifies the request's Bearer token belongs to a real admin account.
 *
 * @param {object} req
 * @param {string[]|null} allowedRoles - e.g. ["super-admin"] or
 *   ["super-admin", "finance"]. If provided, the caller's role must be
 *   one of these, even if they have a valid admin token - this stops a
 *   lower-privileged role from hitting the endpoint URL directly.
 *   Leave null to just require "any admin account", regardless of role.
 */
async function requireAdmin(req, allowedRoles = null) {

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

  // Default to the least-privileged role if the document doesn't set one -
  // fail-safe rather than fail-open.
  const role = adminDoc.data().role || "support";

  if (allowedRoles && role !== "super-admin" && !allowedRoles.includes(role)) {
    return { ok: false, status: 403, error: "This action requires super-admin privileges." };
  }

  return { ok: true, uid: decoded.uid, role };
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

  const authCheck = await requireAdmin(req, ["super-admin"]);

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
    //
    // ✅ FIX: Stripe US accounts ke liye "transfers" capability akele nahi
    // maang sakte - "card_payments" bhi sath maangni padti hai (yehi
    // wajah thi "Could not start Stripe connect: You cannot request the
    // `transfers` capability without the `card_payments` capability"
    // error ki). card_payments ka matlab restaurant/rider khud card
    // payments accept kar sakta hai - humare app mein wo istemal nahi
    // hoti, lekin Stripe ka API rule hai ke US account ke liye dono
    // sath maangni zaroori hain.
    const account = await stripe.accounts.create({
      type: "express",
      country: "US", // sandbox/test mode mein fix rakhte hain
      email: email,
      capabilities: {
        transfers: { requested: true },
        card_payments: { requested: true },
      },
      business_type: "individual",
    });

    // Step 2: Firestore mein sahi collection choose karo
    const collectionName = type === "restaurant" ? "Restaurant" : "Delivery";

    // ✅ FIX: pehle "Register" collection ko .update() kiya jata tha, lekin
    // restaurant/rider ka data is app mein KABHI "Register" mein nahi jata -
    // verification wizard seedha "VerifiedRegister" mein likhta hai
    // (Step4SelfieFragment.java). Non-existent document par .update()
    // Firestore mein error deta hai ("NOT_FOUND") - isi wajah se yeh
    // function har baar fail ho raha tha jab bhi koi verified
    // restaurant/rider Stripe connect karne ki koshish karta.
    //
    // Ab pehle VerifiedRegister check karte hain (jahan asal data hota
    // hai), aur sirf agar wahan na mile to Register try karte hain
    // (purane/pending accounts ke liye, agar kabhi exist karein).
    const verifiedRef = db
      .collection("Users")
      .doc(collectionName)
      .collection("VerifiedRegister")
      .doc(uid);

    const verifiedSnap = await verifiedRef.get();

    if (verifiedSnap.exists) {

      await verifiedRef.update({
        stripeAccountId: account.id,
        stripeOnboardingComplete: false,
      });

    } else {

      const registerRef = db
        .collection("Users")
        .doc(collectionName)
        .collection("Register")
        .doc(uid);

      const registerSnap = await registerRef.get();

      if (!registerSnap.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "No profile found for this account (checked VerifiedRegister and Register)."
        );
      }

      await registerRef.update({
        stripeAccountId: account.id,
        stripeOnboardingComplete: false,
      });
    }

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

      // ✅ FIX: same bug as createConnectedAccount above - this used to
      // unconditionally .update() the "Register" collection first, which
      // throws for any already-verified account (they only ever exist in
      // VerifiedRegister). Now VerifiedRegister is checked/updated first
      // (the real, actually-used path), with Register only touched if it
      // genuinely exists.
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

      const registerRef = db
        .collection("Users")
        .doc(collectionName)
        .collection("Register")
        .doc(uid);

      const registerSnap = await registerRef.get();

      if (registerSnap.exists) {
        await registerRef.update({ stripeOnboardingComplete: true });
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
// 4) ADMIN SE RESTAURANT/RIDER KO PAYOUT
//    (POST request - React Admin Panel se fetch() se call hota hai)
//
//    ✅ REWRITE: pehle yeh function SIRF real Stripe transfer karta tha -
//    Firestore wallet update + history + notification sab Payments.jsx
//    (browser) khud karta tha. Do masle:
//      1. Manual/simulated (Easypaisa) payouts is function ko call hi
//         nahi karte the, is liye unke liye koi notification kabhi
//         nahi gayi.
//      2. Browser se Firestore likhna client-side Admin SDK access
//         chahta hai jo yahan available nahi - is liye purani "No
//         document to update" jaisi galtiyan bhi isi wajah se hoti hain.
//
//    Ab yeh EK function poora payout handle karta hai: Stripe transfer
//    (agar valid connected account ho), Firestore wallet reset + history
//    record, AUR restaurant/rider ko notification - dono cases (real
//    Stripe ya simulated Easypaisa) mein.
// ============================================================

/**
 * Mirrors functions/utils/sendNotification.js from the main app's own
 * Cloud Functions project - duplicated here (not require()'d) because
 * this is a SEPARATE Firebase Functions codebase/deployment. Keeps the
 * exact same Firestore Notifications schema + FCM data-only payload so
 * MyFirebaseMessagingService.java on the Android side handles it
 * identically no matter which backend sent it.
 */
async function sendPartnerNotification({ uid, role, title, body, data = {} }) {

  if (!uid || !role) return;

  // ✅ FIX: was hardcoded to "Restaurant" or "Delivery" only, both under
  // VerifiedRegister - broke for "Passenger" (used by resolveDispute's
  // refund notifications), since passengers don't go through the
  // verification wizard and live under Users/Passenger/Register instead.
  let userRef;

  if (role === "Restaurant") {
    userRef = db.collection("Users").doc("Restaurant").collection("VerifiedRegister").doc(uid);
  } else if (role === "Delivery") {
    userRef = db.collection("Users").doc("Delivery").collection("VerifiedRegister").doc(uid);
  } else {
    userRef = db.collection("Users").doc("Passenger").collection("Register").doc(uid);
  }

  try {

    const notificationRef = userRef.collection("Notifications").doc();

    await notificationRef.set({
      notificationId: notificationRef.id,
      title,
      body,
      image: "",
      type: "order",
      screen: data.screen || "orders",
      priority: "normal",
      orderId: data.orderId || "",
      deepLinkId: data.orderId || "",
      status: data.status || "",
      receiverUid: uid,
      receiverRole: role,
      isRead: false,
      clickedAt: null,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: null,
      version: 1,
    });

    const tokenDoc = await db
      .collection("Users")
      .doc("Notification")
      .collection("FCMTokens")
      .doc(uid)
      .get();

    if (!tokenDoc.exists || !tokenDoc.data().fcmToken) {
      console.log("sendPartnerNotification: no FCM token for", uid);
      return;
    }

    await admin.messaging().send({
      token: tokenDoc.data().fcmToken,
      data: {
        ...data,
        title,
        body,
        screen: data.screen || "orders",
        deepLinkId: data.orderId || "",
        notificationType: "order",
        priority: "normal",
      },
    });

    console.log("sendPartnerNotification: sent to", uid);

  } catch (e) {
    console.error("sendPartnerNotification failed for", uid, e);
  }
}

function maskAccount(accountNumber) {
  if (!accountNumber || accountNumber.length <= 4) return accountNumber || "N/A";
  return "••••" + accountNumber.slice(-4);
}

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
      const {
        walletId,
        amount,
        stripeAccountId,
        receiverType, // "Restaurant" or "Delivery"
        name,
        bankName,
        accountNumber,
      } = req.body;

      if (!walletId || !amount || !receiverType) {
        return res.status(400).json({
          success: false,
          error: "Bad Request: walletId, amount, aur receiverType zaroori hain.",
        });
      }

      const hasValidStripeAccount =
        typeof stripeAccountId === "string" && stripeAccountId.startsWith("acct_");

      // ✅ FIX: no more "manual/simulated" fallback path. If the
      // restaurant/rider hasn't connected Stripe yet, we simply can't pay
      // them out for real - so instead of faking a payment, we tell the
      // admin exactly that, and leave the wallet balance untouched (it
      // stays available for whenever they do connect).
      if (!hasValidStripeAccount) {

        await sendPartnerNotification({
          uid: walletId,
          role: receiverType,
          title: "💰 Payment Waiting",
          body: `Rs ${Math.round(Number(amount))} is ready for you, but your Stripe account isn't connected yet. Open your Wallet and tap "Setup Payments" to receive it.`,
          data: { screen: "wallet" },
        });

        return res.status(400).json({
          success: false,
          error: `${name || "This partner"} hasn't connected a Stripe account yet - they've been notified. Their balance is untouched and will be included once they connect.`,
          needsStripeConnect: true,
        });
      }

      // REAL STRIPE SANDBOX TRANSFER - admin ke test balance se receiver
      // ke connected account mein. `amount` yahan PKR hai (Firestore
      // wallet balance) - pkrToUsdCents() se convert karke Stripe ko
      // dollar-cents diye jate hain, taake ye currency-consistent rahe
      // createPaymentIntent.js ke charging side ke sath.
      const transfer = await stripe.transfers.create({
        amount: pkrToUsdCents(amount),
        currency: "usd",
        destination: stripeAccountId,
        description: `Payout to ${name || receiverType} (wallet: ${walletId}) - Rs ${amount}`,
      });

      const walletRef = db
        .collection("Wallets")
        .doc(receiverType)
        .collection("Accounts")
        .doc(walletId);

      await walletRef.update({
        availableBalance: 0,
        pendingBalance: 0,
      });

      const receiptId = "RCPT-" + Date.now() + "-" + Math.floor(Math.random() * 10000);
      const now = new Date().toISOString();

      await walletRef.collection("history").add({
        type: "Paid by Admin",
        amount: Number(amount),
        method: "stripe",
        transferId: transfer.id,
        receiptId,
        receiver: name || "",
        orderId: "ADMIN_PAYOUT",
        date: now,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });

      const amountRounded = Math.round(Number(amount));

      await sendPartnerNotification({
        uid: walletId,
        role: receiverType,
        title: "💰 Payment Sent",
        body: `Rs ${amountRounded} has been sent to your bank via Stripe. Tap to view receipt.`,
        data: {
          screen: "wallet",
          receiptId,
          amount: String(amountRounded),
        },
      });

      return res.status(200).json({
        success: true,
        message: "Real Stripe sandbox transfer completed successfully.",
        transferId: transfer.id,
        method: "stripe",
        amount: Number(amount),
        receiptId,
      });

    } catch (error) {
      console.error("payoutToPartner error:", error);
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

// ============================================================
// 6) DISPUTE RESOLUTION
//    (Admin panel's "Disputes" tab - the ONLY place a rider-reported
//    delivery failure's three-way split gets decided, since
//    onDeliveryFailed.js in the main app's functions project now just
//    freezes the order instead of auto-computing a split.)
//
//    Does the whole thing in one call: credits the restaurant's and
//    rider's wallets directly (they'll receive it via the normal
//    24-hour auto-payout / manual "Pay Now" flow, same as any other
//    earning), issues a REAL Stripe refund for the passenger's share,
//    and notifies all three - with the admin's typed reason attached
//    for anyone whose share was reduced.
// ============================================================
exports.resolveDispute = functions
  .runWith({ secrets: ["STRIPE_SECRET_KEY"] })
  .https.onCall(async (data, context) => {

    const stripe = getStripe();

    const {
      orderId,
      restaurantShare,
      riderShare,
      passengerRefund,
      restaurantReason,
      riderReason
    } = data;

    if (!orderId) {
      throw new functions.https.HttpsError("invalid-argument", "orderId zaroori hai");
    }

    const orderRef = db.collection("Orders").doc(orderId);
    const orderSnap = await orderRef.get();

    if (!orderSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Order not found");
    }

    const order = orderSnap.data();

    if (order.disputeStatus !== "pending_review") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "This order isn't awaiting review (already resolved, or was never disputed)."
      );
    }

    const restaurantId = order.restaurantId;
    const riderId = order.acceptedBy;
    const passengerUid = order.passengerUid;

    const rShare = Number(restaurantShare) || 0;
    const riShare = Number(riderShare) || 0;
    const pRefund = Number(passengerRefund) || 0;

    // ---------------------------------------------
    // Restaurant + Rider - straight to available balance (admin's
    // decision is final, no need for a separate "pending" stage) - same
    // Wallets/{Role}/Accounts/{uid} structure everything else uses.
    // ---------------------------------------------

    // Pending balances must be cleared whether or not the admin awarded
    // anything - a disputed order's pending amount is stale either way
    // (this runs even when the share is 0, which the `> 0` blocks below
    // would otherwise skip entirely).
    if (restaurantId) {

      const pendingToClear = Number(order.subtotal) || 0;

      if (pendingToClear > 0) {
        await db.collection("Wallets").doc("Restaurant").collection("Accounts").doc(restaurantId)
          .set({ pendingBalance: admin.firestore.FieldValue.increment(-pendingToClear) },
            { merge: true });
      }
    }

    // Same for the rider - onRiderAccepted.js credits their deliveryFee
    // to pendingBalance the moment they accept, so a disputed order
    // leaves that amount stranded there too if it isn't cleared here.
    if (riderId) {

      const riderPendingToClear = Number(order.deliveryFee) || 0;

      if (riderPendingToClear > 0) {
        await db.collection("Wallets").doc("Delivery").collection("Accounts").doc(riderId)
          .set({ pendingBalance: admin.firestore.FieldValue.increment(-riderPendingToClear) },
            { merge: true });
      }
    }

    if (restaurantId && rShare > 0) {

      const restaurantWalletRef = db.collection("Wallets").doc("Restaurant")
        .collection("Accounts").doc(restaurantId);

      await restaurantWalletRef.set({
        availableBalance: admin.firestore.FieldValue.increment(rShare)
      }, { merge: true });

      await restaurantWalletRef.collection("history").add({
        type: "Dispute Resolution",
        amount: rShare,
        orderId,
        reason: restaurantReason || "",
        date: new Date().toISOString()
      });

      await sendPartnerNotification({
        uid: restaurantId,
        role: "Restaurant",
        title: "Order Dispute Resolved",
        body: restaurantReason
          ? `Rs ${Math.round(rShare)} credited for order #${orderId} - ${restaurantReason}`
          : `Rs ${Math.round(rShare)} credited for order #${orderId}.`,
        data: { screen: "wallet", orderId }
      });
    }

    if (riderId && riShare > 0) {

      await db.collection("Wallets").doc("Delivery").collection("Accounts").doc(riderId)
        .set({ availableBalance: admin.firestore.FieldValue.increment(riShare) }, { merge: true });

      await db.collection("Wallets").doc("Delivery").collection("Accounts").doc(riderId)
        .collection("history").add({
          type: "Dispute Resolution",
          amount: riShare,
          orderId,
          reason: riderReason || "",
          date: new Date().toISOString()
        });

      await sendPartnerNotification({
        uid: riderId,
        role: "Delivery",
        title: "Order Dispute Resolved",
        body: riderReason
          ? `Rs ${Math.round(riShare)} credited for order #${orderId} - ${riderReason}`
          : `Rs ${Math.round(riShare)} credited for order #${orderId}.`,
        data: { screen: "wallet", orderId }
      });
    }

    // ---------------------------------------------
    // Passenger - REAL Stripe refund (their share only - can be a
    // partial amount, e.g. if the rider gets some credit for an
    // attempted delivery, the passenger's refund is correspondingly
    // smaller). Never wallet credit for passengers - always their card.
    // ---------------------------------------------

    if (passengerUid && pRefund > 0 && order.paymentIntentId) {

      const passengerWalletRef = db.collection("Wallets").doc("Passenger")
        .collection("Accounts").doc(passengerUid);

      await passengerWalletRef.collection("history").add({
        type: "Refund Pending",
        amount: pRefund,
        orderId,
        date: new Date().toISOString()
      });

      await sendPartnerNotification({
        uid: passengerUid,
        role: "Passenger",
        title: "Refund On The Way",
        body: `Rs ${Math.round(pRefund)} is being refunded for order #${orderId}. It's on its way back to your card.`,
        data: { screen: "wallet", orderId }
      });

      try {

        const refund = await stripe.refunds.create({
          payment_intent: order.paymentIntentId,
          amount: pkrToUsdCents(pRefund)
        });

        let cardLast4 = order.cardLast4 || null;

        await passengerWalletRef.collection("history").add({
          type: "Refund Completed",
          amount: pRefund,
          orderId,
          cardLast4: cardLast4 || "N/A",
          refundId: refund.id,
          date: new Date().toISOString()
        });

        await sendPartnerNotification({
          uid: passengerUid,
          role: "Passenger",
          title: "Refund Completed",
          body: `Rs ${Math.round(pRefund)} is back on your card${cardLast4 ? " (ending " + cardLast4 + ")" : ""}.`,
          data: { screen: "wallet", orderId }
        });

      } catch (refundErr) {

        console.error("resolveDispute: Stripe refund failed for order", orderId, refundErr.message);

        await passengerWalletRef.collection("history").add({
          type: "Refund Failed",
          amount: pRefund,
          orderId,
          error: refundErr.message,
          date: new Date().toISOString()
        });
      }
    }

    await orderRef.update({
      orderStatus: "Cancelled",
      disputeStatus: "resolved",
      disputeResolvedAt: Date.now(),
      disputeResolution: {
        restaurantShare: rShare,
        riderShare: riShare,
        passengerRefund: pRefund,
        restaurantReason: restaurantReason || "",
        riderReason: riderReason || ""
      }
    });

    return { success: true };
  });

// ============================================================
// 7) CONNECTED ACCOUNT'S OWN BALANCE
//    (called directly from the Android app's Wallet screen - restaurant/
//    rider taps "Setup Payments" -> connects Stripe -> from then on, this
//    lets their Wallet screen show "In Transit to Bank: Rs X" whenever
//    Stripe has money sitting in THEIR connected account waiting to be
//    auto-swept to their real bank (Stripe does that sweep on its own
//    schedule - "Settle daily" per the Stripe dashboard - this is purely
//    a read-only status check, not something this app triggers).
// ============================================================
exports.getConnectedAccountBalance = functions
  .runWith({ secrets: ["STRIPE_SECRET_KEY"] })
  .https.onCall(async (data) => {

    const stripe = getStripe();

    const { stripeAccountId } = data;

    if (!stripeAccountId || !stripeAccountId.startsWith("acct_")) {
      throw new functions.https.HttpsError("invalid-argument", "Valid stripeAccountId zaroori hai");
    }

    try {

      const balance = await stripe.balance.retrieve({
        stripeAccount: stripeAccountId,
      });

      const usdAvailable = (balance.available || []).find((b) => b.currency === "usd");
      const usdPending = (balance.pending || []).find((b) => b.currency === "usd");

      return {
        success: true,
        availableUsd: usdAvailable ? usdAvailable.amount / 100 : 0,
        pendingUsd: usdPending ? usdPending.amount / 100 : 0,
      };

    } catch (error) {
      console.error("getConnectedAccountBalance error:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  });

// ============================================================
// 9) USER ACCOUNT MANAGEMENT
//    (called from the admin panel's Restaurant / Riders / Passengers
//    tables - lets an admin disable, restrict, or delete an account,
//    and send a direct message with no account change at all.)
// ============================================================

/** Resolves the correct Firestore doc for any of the 3 roles. */
function userDocRef(role, uid) {

  if (role === "Restaurant") {
    return db.collection("Users").doc("Restaurant").collection("VerifiedRegister").doc(uid);
  }
  if (role === "Delivery") {
    return db.collection("Users").doc("Delivery").collection("VerifiedRegister").doc(uid);
  }
  return db.collection("Users").doc("Passenger").collection("Register").doc(uid);
}

/**
 * Disable/enable an account entirely - a real Firebase Auth disable, not
 * just a Firestore flag. This is what actually blocks sign-in: Firebase
 * Auth itself rejects the next login attempt with ERROR_USER_DISABLED,
 * so the app doesn't need any of its own enforcement code for this case.
 * The reason is stored in Firestore purely so the app CAN show it if it
 * wants to (Firebase Auth's own disabled flag carries no reason text).
 */
exports.setAccountDisabled = functions.https.onCall(async (data) => {

  const { uid, role, disabled, reason } = data;

  if (!uid || !role || typeof disabled !== "boolean") {
    throw new functions.https.HttpsError("invalid-argument", "uid, role and disabled (boolean) are required");
  }

  try {

    await admin.auth().updateUser(uid, { disabled });

    await userDocRef(role, uid).set({
      accountDisabled: disabled,
      disabledReason: disabled ? (reason || "") : admin.firestore.FieldValue.delete(),
      accountDisabledAt: disabled ? Date.now() : null,
    }, { merge: true });

    await sendPartnerNotification({
      uid,
      role,
      title: disabled ? "Account Disabled" : "Account Re-enabled",
      body: disabled
        ? `Your account has been disabled${reason ? " - " + reason : ""}. Contact support if you believe this is a mistake.`
        : "Your account has been re-enabled. You can log in again.",
      data: { screen: "home" },
    });

    return { success: true };

  } catch (error) {
    console.error("setAccountDisabled error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * A lighter, reversible restriction - the account can still sign in, but
 * the app itself checks the "isRestricted" flag and blocks the actions
 * that actually matter (placing an order, accepting an order) while
 * still letting them view their existing orders/wallet. Deliberately a
 * SEPARATE flag from "isPaused" (Module 7's automatic reliability pause)
 * - that one is the system's own doing for repeated failures; this one
 * is a manual admin action, and conflating the two would make it
 * impossible to tell why an account is limited.
 */
exports.setAccountRestricted = functions.https.onCall(async (data) => {

  const { uid, role, restricted, reason } = data;

  if (!uid || !role || typeof restricted !== "boolean") {
    throw new functions.https.HttpsError("invalid-argument", "uid, role and restricted (boolean) are required");
  }

  try {

    await userDocRef(role, uid).set({
      isRestricted: restricted,
      restrictionReason: restricted ? (reason || "") : admin.firestore.FieldValue.delete(),
      restrictedAt: restricted ? Date.now() : null,
    }, { merge: true });

    await sendPartnerNotification({
      uid,
      role,
      title: restricted ? "Account Restricted" : "Restriction Lifted",
      body: restricted
        ? `Your account has a restriction${reason ? " - " + reason : ""}. Please resolve this and you can work again.`
        : "Your account restriction has been lifted - you can work normally again.",
      data: { screen: "home" },
    });

    return { success: true };

  } catch (error) {
    console.error("setAccountRestricted error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * Permanently removes the Firebase Auth account and their profile
 * document. Deliberately does NOT touch Orders, Wallets, or chat
 * history - those are financial/audit records that need to survive the
 * account itself being gone (an order already paid for shouldn't lose
 * its trail just because the restaurant was later removed).
 */
exports.deleteAccount = functions.https.onCall(async (data) => {

  const { uid, role } = data;

  if (!uid || !role) {
    throw new functions.https.HttpsError("invalid-argument", "uid and role are required");
  }

  try {

    try {
      await admin.auth().deleteUser(uid);
    } catch (authError) {
      // Already gone from Auth (e.g. deleted once before) - still proceed
      // to clean up the Firestore side rather than getting stuck.
      if (authError.code !== "auth/user-not-found") throw authError;
    }

    await userDocRef(role, uid).delete();

    return { success: true };

  } catch (error) {
    console.error("deleteAccount error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/** A direct message with no account status change at all. */
exports.sendAdminMessage = functions.https.onCall(async (data) => {

  const { uid, role, title, body } = data;

  if (!uid || !role || !body) {
    throw new functions.https.HttpsError("invalid-argument", "uid, role and body are required");
  }

  try {

    await sendPartnerNotification({
      uid,
      role,
      title: title || "Message from PakTrainFood",
      body,
      data: { screen: "home" },
    });

    return { success: true };

  } catch (error) {
    console.error("sendAdminMessage error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

// ============================================================
// 7) ADMIN MANAGEMENT (super-admin only)
//
//    Backs the "Admin Management" tab in the panel - lets a
//    super-admin create a new admin account, list existing ones,
//    change someone's role, or remove an admin entirely, without
//    ever touching the Firebase Console by hand.
//
//    Every function below re-checks the caller's OWN role against
//    Firestore server-side (never trusts the frontend), because this
//    is the one feature that can grant/revoke access to everything
//    else in the panel.
// ============================================================

/** Shared guard: caller must be signed in AND have role "super-admin". */
async function requireSuperAdminCallable(context) {

  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "You must be signed in.");
  }

  const callerDoc = await db.collection("admins").doc(context.auth.uid).get();

  if (!callerDoc.exists || callerDoc.data().role !== "super-admin") {
    throw new functions.https.HttpsError(
        "permission-denied",
        "Only a super-admin can manage admin accounts.",
    );
  }
}

/** Lists every admin account (uid, email, name, role, createdAt). */
exports.listAdmins = functions.https.onCall(async (data, context) => {

  await requireSuperAdminCallable(context);

  try {
    const snap = await db.collection("admins").get();

    const admins = snap.docs.map((d) => {
      const v = d.data();
      return {
        uid: d.id,
        email: v.email || null,
        name: v.name || "",
        role: v.role || "support",
        createdAt: v.createdAt ? v.createdAt.toMillis() : null,
      };
    });

    return { success: true, admins };

  } catch (error) {
    console.error("listAdmins error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/** Creates a brand-new Firebase Auth user AND its admins/{uid} doc. */
exports.createAdminAccount = functions.https.onCall(async (data, context) => {

  await requireSuperAdminCallable(context);

  const { email, password, name, role } = data;

  if (!email || !password || !role) {
    throw new functions.https.HttpsError("invalid-argument", "email, password and role are required.");
  }

  if (password.length < 6) {
    throw new functions.https.HttpsError("invalid-argument", "Password must be at least 6 characters.");
  }

  let userRecord;
  try {
    userRecord = await admin.auth().createUser({
      email,
      password,
      displayName: name || undefined,
    });
  } catch (error) {
    // e.g. auth/email-already-exists, auth/invalid-email
    throw new functions.https.HttpsError("already-exists", error.message);
  }

  try {
    await db.collection("admins").doc(userRecord.uid).set({
      email,
      name: name || "",
      role,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      createdBy: context.auth.uid,
    });
  } catch (error) {
    // Roll back the auth user so we don't end up with an orphaned
    // login that has no admins/ doc (and therefore no role at all).
    await admin.auth().deleteUser(userRecord.uid).catch(() => {});
    console.error("createAdminAccount (firestore write) error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }

  return { success: true, uid: userRecord.uid };
});

/** Changes an existing admin's role. */
exports.updateAdminRole = functions.https.onCall(async (data, context) => {

  await requireSuperAdminCallable(context);

  const { uid, role } = data;

  if (!uid || !role) {
    throw new functions.https.HttpsError("invalid-argument", "uid and role are required.");
  }

  if (uid === context.auth.uid) {
    // Stops a super-admin from accidentally locking themselves out by
    // demoting their own only super-admin account mid-session.
    throw new functions.https.HttpsError("failed-precondition", "You can't change your own role.");
  }

  try {
    await db.collection("admins").doc(uid).set({ role }, { merge: true });
    return { success: true };
  } catch (error) {
    console.error("updateAdminRole error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/** Removes an admin account completely (Auth user + Firestore doc). */
exports.deleteAdminAccount = functions.https.onCall(async (data, context) => {

  await requireSuperAdminCallable(context);

  const { uid } = data;

  if (!uid) {
    throw new functions.https.HttpsError("invalid-argument", "uid is required.");
  }

  if (uid === context.auth.uid) {
    throw new functions.https.HttpsError("failed-precondition", "You can't delete your own account.");
  }

  try {
    // Ignore "already gone" so a half-deleted admin can still be cleaned up.
    await admin.auth().deleteUser(uid).catch(() => {});
    await db.collection("admins").doc(uid).delete();
    return { success: true };
  } catch (error) {
    console.error("deleteAdminAccount error:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
