const admin = require("../config/firebase");

/**
 * Wallet layout in Firestore
 * --------------------------
 *   Wallets (collection)
 *     └── Passenger | Restaurant | Delivery | Admin   (document per role)
 *           └── Accounts (sub-collection)
 *                 └── {uid}  -> availableBalance, pendingBalance, bank details
 *                       └── history (sub-collection)
 *
 * This mirrors how Users is organised (Users/Passenger/Register/{uid}) so each
 * role's money is easy to find and read on its own.
 */

const WALLET_ROLES = {
    PASSENGER: "Passenger",
    RESTAURANT: "Restaurant",
    DELIVERY: "Delivery",
    ADMIN: "Admin"
};

/** Document reference for one user's wallet. */
function walletRef(role, uid) {

    return admin.firestore()
        .collection("Wallets")
        .doc(role)
        .collection("Accounts")
        .doc(uid);
}

/** The platform's own wallet lives under the Admin role. */
function adminWalletRef() {
    return walletRef(WALLET_ROLES.ADMIN, "admin_wallet");
}

async function addHistory(role, uid, entry) {

    await walletRef(role, uid)
        .collection("history")
        .add({
            type: entry.type,
            amount: entry.amount,
            orderId: entry.orderId || "-",
            date: entry.date || new Date().toISOString()
        });
}

async function addPendingBalance(role, uid, amount, orderId) {

    await walletRef(role, uid).set({

        pendingBalance: admin.firestore.FieldValue.increment(amount)

    }, { merge: true });

    await addHistory(role, uid, {
        type: "Pending",
        amount,
        orderId
    });
}

async function movePendingToAvailable(role, uid, amount, orderId) {

    await walletRef(role, uid).set({

        pendingBalance: admin.firestore.FieldValue.increment(-amount),

        availableBalance: admin.firestore.FieldValue.increment(amount)

    }, { merge: true });

    await addHistory(role, uid, {
        type: "Credited",
        amount,
        orderId
    });
}

/** Refund straight into a passenger's available balance (cancelled order). */
async function refundToPassenger(uid, amount, orderId) {

    await walletRef(WALLET_ROLES.PASSENGER, uid).set({

        availableBalance: admin.firestore.FieldValue.increment(amount)

    }, { merge: true });

    await addHistory(WALLET_ROLES.PASSENGER, uid, {
        type: "Refund",
        amount,
        orderId
    });
}

async function addAdminBalance(amount, orderId) {

    await adminWalletRef().set({

        balance: admin.firestore.FieldValue.increment(amount)

    }, { merge: true });

    await addHistory(WALLET_ROLES.ADMIN, "admin_wallet", {
        type: "Commission",
        amount,
        orderId
    });
}

module.exports = {
    WALLET_ROLES,
    walletRef,
    adminWalletRef,
    addHistory,
    addPendingBalance,
    movePendingToAvailable,
    refundToPassenger,
    addAdminBalance
};
