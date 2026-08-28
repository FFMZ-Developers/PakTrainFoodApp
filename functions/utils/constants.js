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

    COMPLETED: "completed"

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