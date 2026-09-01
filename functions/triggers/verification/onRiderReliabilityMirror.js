// ============================================================================
// onRiderReliabilityMirror.js
//
// Module 7 - dispatchRider.js (Module 5) needs to rank/filter online riders
// by reliabilityScore/isPaused on every radius step of every dispatch - and
// it already reads their lat/lng from Realtime Database (DeliveryRiders),
// not Firestore. Doing a Firestore read per candidate rider on every radius
// step would be slow and wasteful, so instead: whenever a rider's
// reliabilityScore or isPaused flag changes in Firestore
// (Users/Delivery/VerifiedRegister/{uid}), mirror just those two fields
// into the same RTDB node their location already lives in
// (DeliveryRiders/{uid}). Dispatch then reads everything it needs in one
// RTDB fetch, no extra round-trips.
// ============================================================================

const { onDocumentWritten } = require("firebase-functions/v2/firestore");

const admin = require("../../config/firebase");

exports.onRiderReliabilityMirror = onDocumentWritten(
    "Users/Delivery/VerifiedRegister/{uid}",
    async (event) => {

        const after = event.data.after.exists ? event.data.after.data() : null;

        if (!after) return; // profile deleted - nothing to mirror

        const { uid } = event.params;

        const before = event.data.before.exists ? event.data.before.data() : {};

        const scoreChanged = before.reliabilityScore !== after.reliabilityScore;
        const pausedChanged = before.isPaused !== after.isPaused;

        if (!scoreChanged && !pausedChanged) return;

        await admin.database().ref("DeliveryRiders").child(uid).update({
            reliabilityScore: typeof after.reliabilityScore === "number" ? after.reliabilityScore : 100,
            isPaused: after.isPaused === true
        });

        console.log("onRiderReliabilityMirror: mirrored rider", uid, "score/pause status to RTDB");
    }
);
