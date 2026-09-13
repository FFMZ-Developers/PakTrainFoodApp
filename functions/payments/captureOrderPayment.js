// ============================================================================
// captureOrderPayment.js
//
// Module 3 - the "capture" half of "authorize now, capture later".
//
// WHAT HAPPENS
// createPaymentIntent.js creates the Stripe PaymentIntent with
// capture_method: "manual" - so when the passenger confirms PaymentSheet,
// Stripe only puts a HOLD on their card. No money has actually moved yet.
//
// This trigger fires the moment a restaurant accepts a pending order
// (Orders/{orderId}.orderStatus changes to "Accepted" - see
// ActiveOrdersFragment.java's Accept button). That's the signal that the
// order is real and going ahead, so THIS is when we actually take the
// passenger's money - by calling Stripe's capture endpoint on the
// PaymentIntent that was authorized at order-placement time.
//
// If the restaurant instead REJECTS the order, releaseOrderAuthorization
// (onOrderPaymentReversal.js) cancels the hold instead - the passenger's
// card is never charged for an order that never happened.
// ============================================================================

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../config/firebase");
const { getStripeClient, STRIPE_SECRET_KEY } = require("../config/stripe");
const walletHelper = require("../utils/walletHelper");
const passengerNotifications = require("../utils/passengerNotifications");
const { ORDER_STATUS } = require("../utils/constants");

exports.captureOrderPayment = onDocumentUpdated(
    {
        document: "Orders/{orderId}",
        secrets: [STRIPE_SECRET_KEY]
    },
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        // Only act on the exact moment orderStatus becomes "Accepted".
        if (before.orderStatus === ORDER_STATUS.ACCEPTED ||
            after.orderStatus !== ORDER_STATUS.ACCEPTED) {
            return;
        }

        const orderId = event.params.orderId;
        const paymentIntentId = after.paymentIntentId;

        if (!paymentIntentId) {
            // Orders placed before Module 3 shipped won't have this field -
            // nothing to capture, and nothing to break either.
            console.log("captureOrderPayment: no paymentIntentId on order", orderId, "- skipping");
            return;
        }

        if (after.paymentCaptured === true) {
            // Already captured (e.g. a duplicate trigger firing) - never
            // capture twice.
            console.log("captureOrderPayment: order", orderId, "already captured - skipping");
            return;
        }

        const orderRef = admin.firestore().collection("Orders").doc(orderId);

        try {

            const stripe = getStripeClient();

            await stripe.paymentIntents.capture(paymentIntentId);

            // Module - fetch the card's last 4 digits for the passenger's
            // "Payment Sent" receipt (stored once here so we don't need to
            // hit Stripe again every time it's displayed).
            let cardLast4 = null;

            try {

                const intent = await stripe.paymentIntents.retrieve(paymentIntentId, {
                    expand: ["latest_charge.payment_method_details"]
                });

                cardLast4 = intent.latest_charge &&
                    intent.latest_charge.payment_method_details &&
                    intent.latest_charge.payment_method_details.card
                    ? intent.latest_charge.payment_method_details.card.last4
                    : null;

            } catch (e) {
                // Non-fatal - receipt just won't show the card digits.
            }

            await orderRef.update({
                paymentCaptured: true,
                paymentStatus: "captured",
                capturedAt: new Date().toISOString(),
                // Module - dispute-review timeline (see onDeliveryFailed.js).
                acceptedAt: Date.now(),
                cardLast4: cardLast4 || null
            });

            // ============================================================
            // ✅ FIX: admin wallet + restaurant's pending balance now credit
            // HERE (when the money is actually captured), not at raw
            // order-placement time (onOrderPlaced.js used to do this
            // immediately, before the restaurant ever saw the order - so
            // a rejected/never-accepted order's money would have already
            // shown up in the admin's wallet, which was wrong given
            // Module 3's whole "authorize now, capture later" design).
            // ============================================================

            const totalPrice = after.totalPrice || 0;
            const subtotal = after.subtotal || 0;
            const restaurantId = after.restaurantId;

            if (totalPrice > 0) {
                await walletHelper.adminWalletRef().set({
                    balance: admin.firestore.FieldValue.increment(totalPrice)
                }, { merge: true });
            }

            if (restaurantId && subtotal > 0) {

                await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                    .set({
                        pendingBalance: admin.firestore.FieldValue.increment(subtotal)
                    }, { merge: true });

                await walletHelper.walletRef(walletHelper.WALLET_ROLES.RESTAURANT, restaurantId)
                    .collection("history")
                    .add({
                        type: "Pending",
                        amount: subtotal,
                        orderId,
                        date: new Date().toISOString()
                    });
            }

            console.log("captureOrderPayment: captured", paymentIntentId, "for order", orderId);

            // Module - payment lifecycle: "Payment Sent" receipt in the
            // passenger's wallet history + notification (with card last 4)
            // - this is when the money actually left their card, distinct
            // from the earlier "Payment Held" entry at order placement.
            if (after.passengerUid && totalPrice > 0) {

                await admin.firestore()
                    .collection("Wallets").doc("Passenger")
                    .collection("Accounts").doc(after.passengerUid)
                    .collection("history").add({
                        type: "Payment Sent",
                        amount: totalPrice,
                        orderId,
                        cardLast4: cardLast4 || "N/A",
                        date: new Date().toISOString()
                    });

                await passengerNotifications.paymentSent(after.passengerUid, orderId, totalPrice, cardLast4);
            }

            // Module 8 - "preparing" milestone, sent ONLY after a
            // successful capture (not from the Accept click itself) so the
            // passenger is never told "payment processed" if the capture
            // actually failed - see the catch block below for that case.
            await passengerNotifications.preparing(after.passengerUid, orderId);

        } catch (err) {

            console.error("captureOrderPayment: capture failed for order", orderId, err);

            // Record the failure on the order itself so it's visible/
            // actionable (e.g. admin follow-up) rather than silently
            // vanishing into the logs - but don't throw, since a payment
            // failure here shouldn't retry-loop this trigger indefinitely.
            await orderRef.update({
                paymentStatus: "capture_failed",
                paymentCaptureError: err.message || String(err)
            }).catch(() => {});
        }
    }
);
