import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getAuth } from "firebase/auth";
import { getFunctions } from "firebase/functions";
import { getDatabase } from "firebase/database";

const firebaseConfig = {
  apiKey: "AIzaSyDeA29tUGfig2XLve_jbQfOr-U-yIgdAt4",
  authDomain: "paktrainfoodservice.firebaseapp.com",
  databaseURL: "https://paktrainfoodservice-default-rtdb.firebaseio.com",
  projectId: "paktrainfoodservice",
  storageBucket: "paktrainfoodservice.firebasestorage.app",
  messagingSenderId: "584020651389",
  appId: "1:584020651389:web:7a30af9e2cfa6455c1f014",
  measurementId: "G-NFYS1Q09TE"
};

const app = initializeApp(firebaseConfig);

export const db = getFirestore(app);
export const auth = getAuth(app);
// Module: needed so Payments.jsx can call the "Connect Stripe" onCall
// functions (createConnectedAccount / checkStripeAccountStatus) instead
// of only the onRequest ones it already used fetch() for.
export const functions = getFunctions(app);

// Module: needed for the live-locations map (LiveMap.jsx) - rider and
// passenger positions live in the Realtime Database, not Firestore, same
// as the Android app.
export const rtdb = getDatabase(app);