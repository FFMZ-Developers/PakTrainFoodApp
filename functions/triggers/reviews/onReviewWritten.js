// ============================================================================
// onReviewWritten.js
//
// Module: restaurant rating aggregation.
//
// Keeps a restaurant's `averageRating` and `reviewCount` fields on its own
// profile document always in sync with its Reviews subcollection - so
// anywhere the app lists restaurants (find-restaurant screen etc.) can show
// a rating just by reading the restaurant's own doc, without ever having to
// read every individual review to compute an average on the fly.
//
// Fires on create, edit, AND delete of a review - a passenger editing their
// stars or deleting their review both need to move the average.
// ============================================================================

const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("../../config/firebase");

exports.onReviewWritten = onDocumentWritten(
    "Users/Restaurant/VerifiedRegister/{restaurantId}/Reviews/{reviewId}",
    async (event) => {

        const { restaurantId } = event.params;

        try {

            const reviewsSnap = await admin.firestore()
                .collection("Users").doc("Restaurant")
                .collection("VerifiedRegister").doc(restaurantId)
                .collection("Reviews")
                .get();

            let total = 0;
            let count = 0;

            reviewsSnap.forEach((doc) => {

                const rating = doc.data().rating;

                if (typeof rating === "number" && rating > 0) {
                    total += rating;
                    count += 1;
                }
            });

            const average = count > 0 ? total / count : 0;

            await admin.firestore()
                .collection("Users").doc("Restaurant")
                .collection("VerifiedRegister").doc(restaurantId)
                .update({
                    averageRating: Math.round(average * 10) / 10, // one decimal, e.g. 3.3
                    reviewCount: count
                });

            console.log("onReviewWritten: restaurant", restaurantId,
                "-> averageRating", average.toFixed(1), "from", count, "review(s)");

        } catch (e) {
            console.error("onReviewWritten failed for restaurant", restaurantId, e);
        }
    }
);
