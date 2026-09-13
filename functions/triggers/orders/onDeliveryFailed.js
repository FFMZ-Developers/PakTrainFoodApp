// ============================================================================
// onDeliveryFailed.js
//
// Module - rider-reported delivery failures now go to ADMIN REVIEW instead
// of being auto-resolved.
//
// WHY THIS CHANGED
// The old version auto-computed a fixed split (restaurant gets subtotal,
// rider gets a reduced %, passenger gets the rest) the instant a rider
// tapped "Unable to Complete Delivery". But these situations are genuinely
// disputed - the rider might say "couldn't find the passenger", the
// passenger might say "I was right there" - a fixed formula can't tell who
// was actually at fault. So now this trigger's ONLY job is to freeze the
// order and capture everything an admin would need to make that call fairly:
//
//   - The full timeline: when the order was placed, when the restaurant
//     accepted, when the rider was assigned, when the rider arrived at the
//     restaurant, when pickup happened (if it did), and when the failure
//     was reported.
//   - The rider's own typed reason (Order_Accept_Fragment.java now
//     requires this - no more silent one-tap cancellations).
//   - A snapshot of BOTH the rider's and the passenger's last known
//     location at the moment of the report (from Realtime Database) - so
//     the admin can see, for example, whether the rider's GPS pin was
//     actually near the meal station or not.
//
// NO money moves here at all - not even a hold release. Everything stays
// exactly as it is (restaurant's pending balance, rider's nothing-yet,
// passenger's captured payment) until an admin resolves the dispute via
// the admin panel (resolveDispute.js), which is the ONLY place that
// decides the three-way split from this point on.
// ============================================================================

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");
const { ROLES, ORDER_STATUS } = require("../../utils/constants");

exports.onDeliveryFailed = onDocumentUpdated(
    "Orders/{orderId}",
    async (event) => {

        const before = event.data.before.data();
        const after = event.data.after.data();

        if (before.orderStatus === ORDER_STATUS.DELIVERY_FAILED ||
            after.orderStatus !== ORDER_STATUS.DELIVERY_FAILED) {
            return;
        }

        const orderId = event.params.orderId;
        const orderRef = admin.firestore().collection("Orders").doc(orderId);

        const riderId = after.acceptedBy;

        try {

            // Snapshot both parties' last known location at this exact
            // moment - RTDB nodes get overwritten on every GPS tick, so
            // this is the only chance to capture "where were they when
            // the dispute happened".
            let riderLocation = null;
            let passengerLocation = null;

            try {

                if (riderId) {
                    const riderSnap = await admin.database().ref("DeliveryRiders").child(riderId).get();
                    if (riderSnap.exists()) {
                        const r = riderSnap.val();
                        if (typeof r.lat === "number" && typeof r.lng === "number") {
                            riderLocation = { lat: r.lat, lng: r.lng };
                        }
                    }
                }

                const passengerSnap = await admin.database()
                    .ref("OrderLocations").child(orderId).child("latest").get();

                if (passengerSnap.exists()) {
                    const p = passengerSnap.val();
                    if (typeof p.lat === "number" && typeof p.lng === "number") {
                        passengerLocation = { lat: p.lat, lng: p.lng, timestamp: p.timestamp || null };
                    }
                }

            } catch (locErr) {
                console.error("onDeliveryFailed: location snapshot failed for order", orderId, locErr);
            }

            await orderRef.update({

                // Frozen for admin review - NOT "Cancelled" yet. The admin
                // panel's Disputes tab specifically looks for this status.
                orderStatus: ORDER_STATUS.DISPUTED,
                disputeStatus: "pending_review",
                disputeCreatedAt: Date.now(),

                pickupHadHappened: !!after.pickupConfirmedAt,

                disputeRiderLocation: riderLocation,
                disputePassengerLocation: passengerLocation,

                // Full timeline, all in one place for the admin to read -
                // most of these fields already existed on the order
                // (written by other triggers as the order progressed),
                // just pulled together here under one clear name each.
                timelineOrderPlacedAt: after.timestamp || null,
                timelineAcceptedAt: after.acceptedAt || null,
                timelineRiderAssignedAt: after.riderAssignedAt || null,
                timelineRiderArrivedAt: after.riderArrivedAt || null,
                timelinePickupAt: after.pickupConfirmedAt || null,
                timelineFailureReportedAt: after.failureReportedAt || Date.now()
            });

            console.log("onDeliveryFailed: order", orderId, "frozen for admin review - reason:", after.failureReason);

        } catch (err) {

            console.error("onDeliveryFailed: failed to freeze order", orderId, "for review", err);
        }
    }
);
