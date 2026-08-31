import { useEffect, useState, useCallback } from "react";
import "./Payments.css";

import {
  collection,
  doc,
  getDoc,
  getDocs,
} from "firebase/firestore";

import { httpsCallable } from "firebase/functions";

import { db, functions } from "../firebase/config";

// Currency formatting utility shifted to PKR
const formatCurrency = (amount, currency = "PKR") => {
  return new Intl.NumberFormat("en-PK", {
    style: "currency",
    currency,
    minimumFractionDigits: 0
  }).format(Number(amount || 0));
};

const Payments = () => {
  const [loading, setLoading] = useState(true);

  // Admin Wallet State
  const [adminWallet, setAdminWallet] = useState({
    available: 0,
    pending: 0,
    currency: "PKR"
  });

  // Wallets + History Arrays
  const [wallets, setWallets] = useState([]);
  const [history, setHistory] = useState([]);

  // Module: separate per-role lists so each role gets its own section
  // with its own totals, instead of one mixed table.
  const [restaurantWallets, setRestaurantWallets] = useState([]);
  const [riderWallets, setRiderWallets] = useState([]);
  const [passengerWallets, setPassengerWallets] = useState([]);

  // Metrics Display States
  const [restaurantPending, setRestaurantPending] = useState(0);
  const [riderPending, setRiderPending] = useState(0);
  const [totalPaid, setTotalPaid] = useState(0);

  // PAYOUT SHEET MODAL STATES
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedWallet, setSelectedWallet] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [payoutError, setPayoutError] = useState("");

  // Module: Stripe Connect onboarding state (per-wallet loading flag so
  // only the button that was actually clicked shows "Connecting...").
  const [connectingId, setConnectingId] = useState(null);

  // ----------------------------------------------------
  // REAL TIME ADMIN STRIPE WALLET FETCH
  // ----------------------------------------------------
  const loadStripeBalance = useCallback(async (isMounted = { current: true }) => {
    try {
      const response = await fetch(
        "https://us-central1-paktrainfoodservice.cloudfunctions.net/getAdminBalance",
        {
          method: "GET",
          headers: {
            "Content-Type": "application/json"
          }
        }
      );

      const data = await response.json();

      if (!data || data.success !== true) return;

      const availableItems = data.available || [];
      const pendingItems = data.pending || [];

      // ✅ FIX: this used to blindly take availableItems[0] - whatever
      // currency Stripe happened to list FIRST for this account. Our
      // entire payment/transfer system only ever uses USD
      // (pkrToUsdCents() everywhere), but this Stripe account apparently
      // also has a EUR balance (shown as index 0), so the dashboard was
      // displaying an unrelated €9,097 EUR figure while the ACTUAL USD
      // balance our transfers draw from could be genuinely low/zero -
      // which is exactly why "insufficient available funds" kept
      // happening even though the card showed a healthy number. Now
      // specifically finds the USD entry.
      const usdAvailable = availableItems.find((item) => item.currency === "usd");
      const usdPending = pendingItems.find((item) => item.currency === "usd");

      const availableAmt = usdAvailable ? (usdAvailable.amount || 0) / 100 : 0;
      const pendingAmt = usdPending ? (usdPending.amount || 0) / 100 : 0;

      if (isMounted.current) {
        setAdminWallet({
          available: Number(availableAmt),
          pending: Number(pendingAmt),
          currency: "USD"
        });
      }
    } catch (err) {
      console.error("Network or JSON Serialization Error inside Admin Wallet:", err);
    }
  }, []);

  // ----------------------------------------------------
  // FETCH FIRESTORE WALLETS DATA
  //
  // ✅ FIX: this used to read `collection(db, "Wallets")` as if every
  // wallet were a flat top-level document (Wallets/{uid}). The app's
  // ACTUAL structure (WalletPaths.java / functions/utils/walletHelper.js
  // on the backend) is nested by role:
  //
  //   Wallets/Restaurant/Accounts/{uid}
  //   Wallets/Delivery/Accounts/{uid}
  //   Wallets/Passenger/Accounts/{uid}
  //
  // Reading the top-level "Wallets" collection directly only ever finds
  // the three role "folder" documents themselves (Restaurant/Delivery/
  // Passenger), never the actual per-user wallets nested under them -
  // which is exactly why nothing showed up here before. Now each role's
  // Accounts subcollection is queried directly and kept in its own
  // separate list.
  // ----------------------------------------------------
  const fetchWalletsForRole = useCallback(async (roleFolder, uiRole) => {

    const accountsSnapshot = await getDocs(
      collection(db, "Wallets", roleFolder, "Accounts")
    );

    const walletList = [];
    const historyList = [];

    for (const walletDoc of accountsSnapshot.docs) {

      const walletData = walletDoc.data();
      const available = Number(walletData.availableBalance || 0);
      const pending = Number(walletData.pendingBalance || 0);

      let user = null;
      let name = "";
      let phone = "";
      let email = "";
      let city = "";

      let bankName = "Not Set";
      let accountTitle = "Not Set";
      let accountNumber = "Not Available";

      let stripeAccountId = "No Stripe Account Linked";
      let stripeOnboardingComplete = false;

      // Where each role's real profile document lives (matches the
      // Android app's own docRef() logic for each role).
      let profileRef = null;

      if (uiRole === "Restaurant") {
        profileRef = doc(db, "Users", "Restaurant", "VerifiedRegister", walletDoc.id);
      } else if (uiRole === "Delivery") {
        profileRef = doc(db, "Users", "Delivery", "VerifiedRegister", walletDoc.id);
      } else {
        profileRef = doc(db, "Users", "Passenger", "Register", walletDoc.id);
      }

      const profileSnap = await getDoc(profileRef);

      if (profileSnap.exists()) {
        user = profileSnap.data();
        name = user.restaurantName || user.ownerName || user.name || (uiRole + " User");
        phone = user.phone || "";
        email = user.email || "";
        city = user.city || "";
        bankName = user.bankName || "Not Set";
        accountTitle = user.bankAccountHolder || user.ownerName || name;
        accountNumber = user.bankAccountNumber || "Not Available";

        if (user.stripeAccountId) stripeAccountId = user.stripeAccountId;
        stripeOnboardingComplete = user.stripeOnboardingComplete || false;
      }

      walletList.push({
        id: walletDoc.id,
        role: uiRole,
        name,
        phone,
        email,
        city,

        available,
        pending,

        bankName,
        accountTitle,
        accountNumber,

        stripeAccountId,
        stripeOnboardingComplete
      });

      // Full transaction history for this wallet.
      const historySnapshot = await getDocs(
        collection(db, "Wallets", roleFolder, "Accounts", walletDoc.id, "history")
      );

      historySnapshot.forEach((item) => {
        const h = item.data();
        historyList.push({
          walletId: walletDoc.id,
          role: uiRole,
          name,
          phone,
          amount: h.amount || 0,
          orderId: h.orderId || "-",
          type: h.type || "",
          date: h.date || "",
          transferId: h.transferId || "-",
          method: h.method || "manual"
        });
      });
    }

    return { walletList, historyList };
  }, []);

  const fetchWallets = useCallback(async (isMounted = { current: true }) => {
    try {
      if (isMounted.current) setLoading(true);

      const [restaurantResult, riderResult, passengerResult] = await Promise.all([
        fetchWalletsForRole("Restaurant", "Restaurant"),
        fetchWalletsForRole("Delivery", "Delivery"),
        fetchWalletsForRole("Passenger", "Passenger")
      ]);

      const allWallets = [
        ...restaurantResult.walletList,
        ...riderResult.walletList,
        ...passengerResult.walletList
      ];

      const allHistory = [
        ...restaurantResult.historyList,
        ...riderResult.historyList,
        ...passengerResult.historyList
      ];

      let paidTotal = 0;
      allHistory.forEach((h) => {
        if (h.type === "Paid by Admin") paidTotal += Number(h.amount || 0);
      });

      if (isMounted.current) {
        setRestaurantWallets(restaurantResult.walletList);
        setRiderWallets(riderResult.walletList);
        setPassengerWallets(passengerResult.walletList);

        setWallets(allWallets);
        setHistory(allHistory);

        setRestaurantPending(
          restaurantResult.walletList.reduce((sum, w) => sum + w.pending, 0)
        );
        setRiderPending(
          riderResult.walletList.reduce((sum, w) => sum + w.pending, 0)
        );
        setTotalPaid(paidTotal);
        setLoading(false);
      }
    } catch (error) {
      console.error("Error reading Firestore collection nodes:", error);
      if (isMounted.current) setLoading(false);
    }
  }, [fetchWalletsForRole]);

  // ----------------------------------------------------
  // INITIAL LOAD AND SCHEDULER SUBSCRIPTION
  // ----------------------------------------------------
  useEffect(() => {
    const isMounted = { current: true };

    const init = async () => {
      await loadStripeBalance(isMounted);
      await fetchWallets(isMounted);
    };

    init();

    const interval = setInterval(() => {
      loadStripeBalance(isMounted);
    }, 10000);

    return () => {
      isMounted.current = false;
      clearInterval(interval);
    };
  }, [loadStripeBalance, fetchWallets]);

  // ----------------------------------------------------
  // STRIPE CONNECT - creates a connected account + onboarding link for a
  // wallet that isn't linked yet, and lets the admin re-check status once
  // the restaurant/rider has completed onboarding externally.
  // ----------------------------------------------------
  const handleConnectStripe = async (wallet) => {

    if (!wallet.email) {
      alert("This account has no email on file - can't create a Stripe account without one.");
      return;
    }

    setConnectingId(wallet.id);

    try {
      const createConnectedAccount = httpsCallable(functions, "createConnectedAccount");

      const result = await createConnectedAccount({
        email: wallet.email,
        uid: wallet.id,
        type: wallet.role === "Restaurant" ? "restaurant" : "rider"
      });

      if (result.data && result.data.onboardingUrl) {
        window.open(result.data.onboardingUrl, "_blank");
        alert(
          "Stripe onboarding opened in a new tab. Once " + wallet.name +
          " completes it, come back and click \"Check Status\" to confirm."
        );
      }

      await fetchWallets();

    } catch (error) {
      console.error("Stripe connect failed:", error);
      alert("Could not start Stripe connect: " + (error.message || ""));
    } finally {
      setConnectingId(null);
    }
  };

  const handleCheckStripeStatus = async (wallet) => {

    if (!wallet.stripeAccountId || !wallet.stripeAccountId.startsWith("acct_")) return;

    setConnectingId(wallet.id);

    try {
      const checkStripeAccountStatus = httpsCallable(functions, "checkStripeAccountStatus");

      const result = await checkStripeAccountStatus({
        stripeAccountId: wallet.stripeAccountId,
        uid: wallet.id,
        type: wallet.role === "Restaurant" ? "restaurant" : "rider"
      });

      if (result.data && result.data.isComplete) {
        alert(wallet.name + "'s Stripe account is fully connected and ready for payouts.");
      } else {
        alert(wallet.name + " hasn't finished Stripe onboarding yet - still pending.");
      }

      await fetchWallets();

    } catch (error) {
      console.error("Stripe status check failed:", error);
      alert("Could not check Stripe status: " + (error.message || ""));
    } finally {
      setConnectingId(null);
    }
  };

  // ----------------------------------------------------
  // OPEN PAYOUT SHEET TRIGGER
  // ----------------------------------------------------
  const handleOpenPayoutSheet = (wallet) => {
    setPayoutError("");
    setSelectedWallet(wallet);
    setIsModalOpen(true);
  };

  // ----------------------------------------------------
  // EXECUTE REAL TRANSFER/MUTATION LAYER
  // ----------------------------------------------------
  const handleConfirmPayout = async () => {
    if (!selectedWallet) return;

    setPayoutError("");

    try {
      setIsProcessing(true);

      // ✅ REWRITE: this used to only call the backend for a real Stripe
      // transfer, and did the wallet reset + history write + (missing)
      // notification directly from the browser for the manual/simulated
      // case. Now payoutToPartner ALWAYS runs, for both cases - it's the
      // one place that updates Firestore (with the correct nested
      // Wallets/{Role}/Accounts/{uid} path) AND sends the restaurant/
      // rider their "payment sent" notification. Fixes both the "No
      // document to update" crash and manual payouts never notifying
      // anyone.
      const response = await fetch(
        "https://us-central1-paktrainfoodservice.cloudfunctions.net/payoutToPartner",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            walletId: selectedWallet.id,
            amount: selectedWallet.available,
            stripeAccountId: selectedWallet.stripeAccountId,
            receiverType: selectedWallet.role,
            name: selectedWallet.name,
            bankName: selectedWallet.bankName,
            accountNumber: selectedWallet.accountNumber
          })
        }
      );

      const result = await response.json();

      if (!result.success) {

        if (result.needsStripeConnect) {
          // Module - simulated payouts removed: this is expected, not a
          // crash - the partner just hasn't connected Stripe yet (they've
          // already been notified server-side).
          setIsModalOpen(false);
          setSelectedWallet(null);
          alert(result.error);
          await fetchWallets();
          return;
        }

        throw new Error(result.error || "Payout failed");
      }

      setIsModalOpen(false);
      setSelectedWallet(null);

      if (result.method === "stripe") {
        alert("Real Stripe sandbox transfer successful! " + selectedWallet.name + " has been notified.");
      } else {
        alert("Payout recorded and " + selectedWallet.name + " has been notified (manual/simulated - no Stripe account linked yet).");
      }

      // Admin ka real balance turant refresh karo (Stripe se live fetch)
      await loadStripeBalance();
      await fetchWallets();
    } catch (error) {
      console.error("Disbursement operation mutation crash:", error);
      setPayoutError(error.message || "Payment processing failure.");
      alert("Payment processing failure: " + (error.message || ""));
    } finally {
      setIsProcessing(false);
    }
  };

  // ----------------------------------------------------
  // DERIVED METRICS
  // ----------------------------------------------------
  const totalWalletBalance = wallets.reduce((sum, w) => sum + Number(w.available || 0), 0);
  const totalRestaurants = wallets.filter((w) => w.role === "Restaurant").length;
  const totalRiders = wallets.filter((w) => w.role === "Delivery").length;

  const sortedHistory = [...history].sort((a, b) => new Date(b.date || 0) - new Date(a.date || 0));
  const sortedWallets = [...wallets].sort((a, b) => b.available - a.available);

  // ----------------------------------------------------
  // One reusable section per role: heading, its own Total
  // Available / Total Pending summary, and a table with a "View" button
  // on every row (opens the same payout sheet - Pay stays disabled there
  // if there's nothing to pay out). Full payout workflows (auto-payout
  // schedules, payout history filters, etc.) are a later pass - this is
  // just the visibility piece for now.
  // ----------------------------------------------------
  const renderWalletSection = (title, walletList) => {

    const sectionSorted = [...walletList].sort((a, b) => b.available - a.available);
    const totalAvailable = walletList.reduce((sum, w) => sum + Number(w.available || 0), 0);
    const totalPending = walletList.reduce((sum, w) => sum + Number(w.pending || 0), 0);

    return (
      <div className="payments-table-card" style={{ marginTop: "20px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "20px", flexWrap: "wrap", gap: "10px" }}>
          <h3 style={{ padding: 0 }}>{title} ({walletList.length})</h3>
          <div style={{ display: "flex", gap: "20px", fontWeight: "bold" }}>
            <span style={{ color: "#2e7d32" }}>Total Available: {formatCurrency(totalAvailable, "PKR")}</span>
            <span style={{ color: "#e07b00" }}>Total Pending: {formatCurrency(totalPending, "PKR")}</span>
          </div>
        </div>

        <table className="payments-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Phone</th>
              <th>Stripe Status</th>
              <th>Available Balance</th>
              <th>Pending Balance</th>
              <th>Action</th>
            </tr>
          </thead>

          <tbody>
            {loading ? (
              <tr><td colSpan="6">Loading...</td></tr>
            ) : sectionSorted.length === 0 ? (
              <tr><td colSpan="6">No {title} Found</td></tr>
            ) : (
              sectionSorted.map((wallet) => (
                <tr key={wallet.id}>
                  <td>{wallet.name}</td>
                  <td>{wallet.phone}</td>
                  <td>
                    {wallet.stripeAccountId?.startsWith("acct_") ? (
                      wallet.stripeOnboardingComplete ? (
                        <span style={{ color: "green", fontWeight: "bold" }}>Linked ✓</span>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: "4px", alignItems: "flex-start" }}>
                          <span style={{ color: "#a15c00", fontWeight: "bold" }}>Pending Onboarding</span>
                          <button
                            className="pay-now-btn"
                            style={{ fontSize: "12px", padding: "4px 10px" }}
                            disabled={connectingId === wallet.id}
                            onClick={() => handleCheckStripeStatus(wallet)}
                          >
                            {connectingId === wallet.id ? "Checking..." : "Check Status"}
                          </button>
                        </div>
                      )
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: "4px", alignItems: "flex-start" }}>
                        <span style={{ color: "#999" }}>Not Linked</span>
                        {wallet.role !== "Passenger" && (
                          <button
                            className="pay-now-btn"
                            style={{ fontSize: "12px", padding: "4px 10px" }}
                            disabled={connectingId === wallet.id}
                            onClick={() => handleConnectStripe(wallet)}
                          >
                            {connectingId === wallet.id ? "Connecting..." : "Connect Stripe"}
                          </button>
                        )}
                      </div>
                    )}
                  </td>
                  <td className="available-balance">
                    {formatCurrency(wallet.available, "PKR")}
                  </td>
                  <td className="pending-balance">
                    {formatCurrency(wallet.pending, "PKR")}
                  </td>
                  <td style={{ display: "flex", gap: "6px" }}>
                    <button
                      className="pay-now-btn"
                      style={{ backgroundColor: "#555" }}
                      onClick={() => handleOpenPayoutSheet(wallet)}
                    >
                      View
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <div className="payments-container animate-fade-in">
      {/* MAIN VIEW HEADER */}
      <div className="payments-header">
        <div>
          <h2>Payment Management</h2>
          <p>Manage Restaurant & Rider Payments</p>
        </div>
      </div>

      {/* DASHBOARD CARD ROW COMPONENTS */}
      <div className="payments-metrics-grid">
        <div className="payment-card">
          <h3>ADMIN STRIPE WALLET</h3>
          <h2>{formatCurrency(adminWallet.available, adminWallet.currency)}</h2>
          <p style={{ marginTop: "10px" }}>
            Pending : {formatCurrency(adminWallet.pending, adminWallet.currency)}
          </p>
        </div>

        {/* Module - restaurant + rider now each show BOTH balances
            (available in green, pending in orange), instead of only a
            single "Pending" number that hid the available side entirely. */}
        <div className="payment-card">
          <h3>RESTAURANT BALANCE</h3>
          <h2 style={{ color: "#2e7d32" }}>
            {formatCurrency(
              restaurantWallets.reduce((sum, w) => sum + Number(w.available || 0), 0),
              "PKR"
            )}
          </h2>
          <p style={{ marginTop: "10px", color: "#e07b00", fontWeight: "bold" }}>
            Pending : {formatCurrency(restaurantPending, "PKR")}
          </p>
        </div>

        <div className="payment-card">
          <h3>RIDER BALANCE</h3>
          <h2 style={{ color: "#2e7d32" }}>
            {formatCurrency(
              riderWallets.reduce((sum, w) => sum + Number(w.available || 0), 0),
              "PKR"
            )}
          </h2>
          <p style={{ marginTop: "10px", color: "#e07b00", fontWeight: "bold" }}>
            Pending : {formatCurrency(riderPending, "PKR")}
          </p>
        </div>

        <div className="payment-card">
          <h3>TOTAL PAID</h3>
          <h2>{formatCurrency(totalPaid, "PKR")}</h2>
        </div>
      </div>

      {/* ACCOUNT BALANCES - ONE SECTION PER ROLE, EACH WITH ITS OWN TOTALS */}
      {renderWalletSection("Restaurant Wallets", restaurantWallets)}
      {renderWalletSection("Rider Wallets", riderWallets)}
      {renderWalletSection("Passenger Wallets", passengerWallets)}

      {/* HISTORICAL LEDGER ENTRIES */}
      <div className="payments-table-card">
        <h3 style={{ padding: "20px" }}>Payment History</h3>

        <table className="payments-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Role</th>
              <th>Amount</th>
              <th>Type</th>
              <th>Method</th>
              <th>Order</th>
              <th>Date</th>
            </tr>
          </thead>

          <tbody>
            {sortedHistory.length === 0 ? (
              <tr>
                <td colSpan="7">No History Found</td>
              </tr>
            ) : (
              sortedHistory.map((item, index) => (
                <tr key={index}>
                  <td>{item.name}</td>
                  <td>{item.role}</td>
                  <td>{formatCurrency(item.amount, "PKR")}</td>
                  <td>{item.type}</td>
                  <td>{item.method === "stripe" ? "Stripe (Real)" : "Manual"}</td>
                  <td>{item.orderId}</td>
                  <td>{item.date ? item.date.slice(0, 10) : "-"}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* DYNAMIC PAYOUT SHEET (MODAL POPUP VIEW) */}
      {isModalOpen && selectedWallet && (
        <div className="payout-modal-overlay">
          <div className="payout-modal-sheet">
            <div className="payout-modal-header">
              <h3>Execute Payout Gateway Transfer</h3>
              <button className="close-btn" onClick={() => setIsModalOpen(false)}>×</button>
            </div>

            <div className="payout-modal-body">

              <div className="modal-user-card">
                <div className="avatar">
                  {selectedWallet.name?.charAt(0)}
                </div>

                <div>
                  <h2>{selectedWallet.name}</h2>
                  <span className="role-tag">{selectedWallet.role}</span>
                </div>
              </div>

              <div className="info-grid">
                <div><b>Phone:</b> : {selectedWallet.phone}</div>
                <div><b>Email:</b> : {selectedWallet.email || "N/A"}</div>
                <div><b>City:</b> : {selectedWallet.city || "N/A"}</div>
                <div><b>Bank:</b> : {selectedWallet.bankName}</div>
                <div><b>Account Title:</b> : {selectedWallet.accountTitle}</div>
                <div><b>Account No:</b> : {selectedWallet.phone}</div>
              </div>

              {selectedWallet.stripeAccountId?.startsWith("acct_") ? (
                <div style={{ margin: "10px 0", color: "green", fontWeight: "bold" }}>
                  ✓ Stripe Connected Account Linked — Real sandbox transfer hogi
                </div>
              ) : (
                <div style={{ margin: "10px 0", color: "#a15c00", fontWeight: "bold" }}>
                  ⚠ Koi Stripe account linked nahi — sirf manual record hoga
                </div>
              )}

              <div className="balance-box">
                <span>AVAILABLE BALANCE</span>
                <h1>{formatCurrency(selectedWallet.available, "PKR")}</h1>
              </div>

              {payoutError && (
                <div style={{ color: "red", marginTop: "10px" }}>{payoutError}</div>
              )}

            </div>

            <div className="payout-modal-footer">
              <button className="cancel-action-btn" onClick={() => setIsModalOpen(false)} disabled={isProcessing}>
                Cancel
              </button>
              <button
                className="confirm-payout-btn"
                onClick={handleConfirmPayout}
                disabled={isProcessing || !selectedWallet || selectedWallet.available <= 0}
              >
                {isProcessing
                  ? "Processing Transfer..."
                  : (selectedWallet && selectedWallet.available <= 0)
                    ? "No Balance To Pay Out"
                    : "Confirm Payment"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* FOOTER METRICS SUMMARY */}
      <div style={{ marginTop: "20px", fontWeight: "bold", textAlign: "right" }}>
        Total Wallets : {wallets.length}
        <br />
        Restaurants : {totalRestaurants}
        <br />
        Riders : {totalRiders}
        <br />
        Total Balance : {formatCurrency(totalWalletBalance, "PKR")}
      </div>
    </div>
  );
};

export default Payments;
