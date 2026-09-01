// ======================================
// USER ROLES
// ======================================

const ROLES = {

    PASSENGER: "Passenger",

    RESTAURANT: "Restaurant",

    DELIVERY: "Delivery"

};

// ======================================
// ORDER STATUS
// ======================================

const ORDER_STATUS = {

    ACTIVE: "Active",

    ACCEPTED: "Accepted",

    READY_FOR_DELIVERY: "ready_for_delivery",

    ACCEPTED_BY_RIDER: "accepted_by_rider",

    ARRIVE_RIDER_AT_RESTAURANT: "arrive_rider_at_resturent",

    DROPPED: "dropped",

    PICK_UP: "pick_up",

    COMPLETED: "completed",

    // Module 3 - needed so a pending order can be declined before capture,
    // and so a captured order that still fails later has a status to land
    // on for the refund path.
    REJECTED: "Rejected",

    CANCELLED: "Cancelled",

    // Module 6 (Failure 3) - rider reports they couldn't complete an
    // in-progress delivery.
    DELIVERY_FAILED: "delivery_failed",

    // Module - frozen, awaiting admin's manual three-way split decision
    // (see onDeliveryFailed.js + the admin panel's Disputes tab).
    DISPUTED: "disputed"

};

// ======================================
// NOTIFICATION TYPES
// ======================================

const NOTIFICATION_TYPES = {

    ORDER: "order",

    WALLET: "wallet",

    CHAT: "chat",

    OFFER: "offer",

    VERIFICATION: "verification",

    SYSTEM: "system"

};

// ======================================
// SCREENS
// ======================================

const SCREENS = {

    ORDERS: "orders",

    HOME: "home",

    WALLET: "wallet",

    PROFILE: "profile",

    NOTIFICATIONS: "notifications"

};

module.exports = {

    ROLES,

    ORDER_STATUS,

    NOTIFICATION_TYPES,

    SCREENS

};















// const ORDER_STATUS = {

//     ACTIVE: "Active",

//     ACCEPTED: "Accepted",

//     READY_FOR_DELIVERY: "ready_for_delivery",

//     ACCEPTED_BY_RIDER: "accepted_by_rider",

//     ARRIVE_RIDER_AT_RESTAURANT: "arrive_rider_at_resturent",

//     DROPPED: "dropped",

//     PICK_UP: "pick_up",

//     COMPLETED: "completed"

// };

// module.exports = {
//     ORDER_STATUS
// };