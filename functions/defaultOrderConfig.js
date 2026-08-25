// Default values for Settings/orderConfig.
//
// Every module in the order pipeline (ETA engine, payment capture gate,
// rider dispatch, reliability scoring) should read its thresholds from
// this Firestore document instead of hardcoding them, specifically so an
// admin can tune them from this panel without a new app release.
const DEFAULT_ORDER_CONFIG = {

  // Rider dispatch
  riderSearchRadiiKm: [1, 2, 3, 4, 5],
  riderSearchStepDelaySeconds: 5,
  riderSearchTimeoutSeconds: 240,

  // ETA-gated order dispatch to the restaurant
  orderDispatchEtaThresholdMinutes: 60,
  riderTransitBufferMinutes: 25,

  // Fallback average train speed, used only until there is enough live
  // GPS history on an order to compute a real one
  fallbackTrainSpeedKmph: 70,

  // Reliability scoring
  restaurantReliabilityStrikeLimit: 3,
  restaurantReliabilityWindowDays: 30,
  riderReliabilityStrikeLimit: 3,
  riderReliabilityWindowDays: 30,
  reliabilityStartingScore: 100,
  reliabilityStrikePenalty: 15,
  reliabilityCompletionBonus: 2,

  // Failure-mode compensation
  riderAttemptedDeliveryFeePercent: 40,

  // Passenger-abandoned-journey detection
  journeyStallMinutesBeforeCancel: 12,

  // City list shown in the restaurant/rider signup wizard's city dropdown -
  // admin-editable so it doesn't need a new app release to add/remove a city.
  cities: [
    "Karachi", "Lahore", "Islamabad", "Rawalpindi", "Faisalabad",
    "Multan", "Peshawar", "Quetta", "Sialkot", "Gujranwala",
    "Hyderabad", "Bahawalpur", "Sargodha", "Sukkur", "Larkana",
    "Sheikhupura", "Rahim Yar Khan", "Jhang", "Dera Ghazi Khan", "Gujrat",
    "Sahiwal", "Mardan", "Kasur", "Okara", "Mingora",
    "Nawabshah", "Chiniot", "Kotri", "Hafizabad", "Mandi Bahauddin",
    "Jhelum", "Khanewal", "Muzaffargarh", "Vehari", "Abbottabad",
    "Muridke", "Kohat", "Sadiqabad", "Burewala", "Jacobabad",
  ],
};

module.exports = { DEFAULT_ORDER_CONFIG };
