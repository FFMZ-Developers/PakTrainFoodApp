const admin = require("../config/firebase");

async function addWalletHistory(
    uid,
    type,
    amount,
    orderId
) {

    await admin.firestore()
        .collection("Wallets")
        .doc(uid)
        .collection("history")
        .add({

            type,

            amount,

            orderId,

            date: new Date().toISOString()

        });

}

module.exports = {

    addWalletHistory

};