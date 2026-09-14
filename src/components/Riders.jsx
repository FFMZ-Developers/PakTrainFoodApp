import { useEffect, useState } from "react";
import {
  collection,
  onSnapshot,
  query,
  doc,
  updateDoc,
  getDocs
} from "firebase/firestore";

import { db } from "../firebase/config";
import AccountActionsModal from "./AccountActionsModal";
import "./Riders.css";


const DEFAULT_IMAGE =
"https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600";


const Riders = () => {

const [riders,setRiders] = useState([]);
const [managingUser,setManagingUser] = useState(null);
const [profiles,setProfiles] = useState({});
const [activeTab,setActiveTab] = useState("Active Riders");
const [selectedRequest,setSelectedRequest] = useState(null);
const [search,setSearch] = useState("");
const [rejectingId, setRejectingId] = useState(null);
const [rejectReason, setRejectReason] = useState("");
const [lightboxImage, setLightboxImage] = useState(null);
const [reliabilityDetail, setReliabilityDetail] = useState(null);
const [strikesList, setStrikesList] = useState([]);
const [loadingStrikes, setLoadingStrikes] = useState(false);
const [newScoreInput, setNewScoreInput] = useState('');



useEffect(()=>{

const ref = collection(
db,
"Users",
"Delivery",
"VerifiedRegister"
);


const unsub = onSnapshot(query(ref),(snap)=>{

let arr=[];

snap.forEach(d=>{

arr.push({
id:d.id,
...d.data()
});

});


setRiders(arr);

});


return ()=>unsub();


},[]);





useEffect(()=>{


const loadProfile = async()=>{


const snap = await getDocs(
collection(
db,
"Users",
"Delivery",
"VerifiedRegister"
)
);


let img={};


snap.forEach(d=>{

img[d.id]=d.data().profileImageUrl || d.data().selfieUrl;

});


setProfiles(img);


};


loadProfile();


},[]);







// Module: clears the auto-pause set by reliabilityHelper.js's recordStrike()
// once the admin has reviewed the strikes and is satisfied the rider can
// operate again. Deliberately does NOT touch reliabilityScore or the
// strikes history - only lifts the pause itself, so the score still
// reflects real history and can keep recovering normally via completed
// deliveries (recordCompletion's +2 bonus per order).
const handleReactivate = async (id, name) => {

  if (!window.confirm(`Reactivate ${name || 'this rider'}? They will be able to accept orders again immediately.`)) {
    return;
  }

  await updateDoc(

  doc(
  db,
  "Users",
  "Delivery",
  "VerifiedRegister",
  id
  ),

  {
  isPaused:false,
  pausedReason:null
  }

  );

};

// Module: opens the strikes-history panel for one rider. Actual fetch
// happens in the useEffect below (keyed on reliabilityDetail).
const openReliabilityDetail = (rider) => {
  setReliabilityDetail(rider);
  setNewScoreInput('');
};

useEffect(() => {
  if (!reliabilityDetail) {
    setStrikesList([]);
    return;
  }

  setLoadingStrikes(true);

  getDocs(collection(db, "Users", "Delivery", "VerifiedRegister", reliabilityDetail.id, "strikes"))
    .then((snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => (b.at || 0) - (a.at || 0));
      setStrikesList(list);
    })
    .catch((err) => {
      console.error("Could not load strikes: ", err);
    })
    .finally(() => setLoadingStrikes(false));
}, [reliabilityDetail]);

// Module: lets the admin manually correct a score - e.g. the strikes were
// for a genuine one-off issue that's since been resolved, and the rider
// shouldn't have to wait for +2-per-delivery recovery to earn their way
// back. Does not touch the strikes history itself (audit trail stays
// intact) or isPaused (use Reactivate for that) - purely the number.
const handleAdjustScore = async () => {
  const value = Number(newScoreInput);

  if (Number.isNaN(value) || value < 0 || value > 100) {
    alert("Enter a score between 0 and 100.");
    return;
  }

  if (!window.confirm(`Set ${reliabilityDetail.name || "this rider"}'s reliability score to ${value}?`)) {
    return;
  }

  try {
    await updateDoc(doc(db, "Users", "Delivery", "VerifiedRegister", reliabilityDetail.id), {
      reliabilityScore: value
    });
    setReliabilityDetail((prev) => ({ ...prev, reliabilityScore: value }));
    setNewScoreInput('');
  } catch (err) {
    console.error("Score adjust error: ", err);
    alert("Could not update score.");
  }
};

const approve = async(id)=>{

if (!window.confirm("Are you sure you want to approve this rider? Their account will be activated immediately.")) {
  return;
}


await updateDoc(

doc(
db,
"Users",
"Delivery",
"VerifiedRegister",
id
),

{
status:"Approved",
isVerified:true,
isLive:true,
verificationStatus:"verified",
rejectionReason:null
}

);


setSelectedRequest(null);

};

const startReject = (id) => {
  setRejectingId(id);
  setRejectReason("");
};

const confirmReject = async () => {

  if (!rejectingId) return;
  if (!rejectReason.trim()) return;

  await updateDoc(

  doc(
  db,
  "Users",
  "Delivery",
  "VerifiedRegister",
  rejectingId
  ),

  {
  status:"Rejected",
  isVerified:false,
  isLive:false,
  verificationStatus:"rejected",
  rejectionReason:rejectReason.trim()
  }

  );

  setSelectedRequest(null);
  setRejectingId(null);
  setRejectReason("");

};






const goOffline = async(id)=>{


await updateDoc(

doc(
db,
"Users",
"Delivery",
"VerifiedRegister",
id
),

{
status:"Pending",
isVerified:false,
isLive:false
}

);


};






const activeRiders = riders.filter(r=>

r.status==="Approved" &&
r.isVerified===true

);

let totalRating = 0;
let ratedCount = 0;
activeRiders.forEach(r => {
  if (r.averageRating) {
    totalRating += Number(r.averageRating);
    ratedCount++;
  }
});
const avgRiderRating = ratedCount > 0 ? (totalRating / ratedCount).toFixed(1) : '0.0';





const pendingRiders = riders.filter(r=>

r.status!=="Approved" &&
r.status!=="Rejected"

);





const rejectedRiders = riders.filter(r=>

r.status==="Rejected"

);





const data = activeTab==="Active Riders"
?
activeRiders
:
activeTab==="Verification Requests"
?
pendingRiders
:
rejectedRiders;





const filtered=data.filter(r=>{


let s=search.toLowerCase();


return (

(r.name||"")
.toLowerCase()
.includes(s)

||

(r.email||"")
.toLowerCase()
.includes(s)

||

(r.city||"")
.toLowerCase()
.includes(s)

);


});







return (

<div className="restaurant-container">





<div className="tab-buttons-group">


<button

className={
activeTab==="Active Riders"
?
"btn-tab btn-tab-active"
:
"btn-tab"
}

onClick={()=>setActiveTab("Active Riders")}

>

🛵 Active Riders

</button>





<button

className={
activeTab==="Verification Requests"
?
"btn-tab btn-tab-active"
:
"btn-tab"
}

onClick={()=>setActiveTab("Verification Requests")}

>

📋 Verification Requests

<span className="tab-badge-count">

{pendingRiders.length}

</span>

</button>


<button

className={
activeTab==="Rejected"
?
"btn-tab btn-tab-active"
:
"btn-tab"
}

onClick={()=>setActiveTab("Rejected")}

>

✕ Rejected

<span className="tab-badge-count">

{rejectedRiders.length}

</span>

</button>


</div>






<div className="restaurant-page-header">

<h2>Delivery Riders</h2>

<p>
Manage your rider network and verification requests.
</p>

</div>








<div className="metrics-grid">


<div className="rest-metric-card">

<div className="card-header-row">

<p className="metric-title">
TOTAL RIDERS
</p>

<span className="metric-icon-box">
🛵
</span>

</div>

<h4 className="metric-value">

{activeRiders.length}

</h4>

<span className="metric-sub-badge badge-approved">
Approved
</span>

</div>







<div className="rest-metric-card">

<div className="card-header-row">

<p className="metric-title">
ONLINE NOW
</p>

<span className="metric-icon-box">
🟢
</span>

</div>


<h4 className="metric-value">

{
activeRiders.filter(r=>r.isLive).length
}

</h4>


<span className="metric-sub-badge badge-live">
Live
</span>

</div>








<div className="rest-metric-card">

<div className="card-header-row">

<p className="metric-title">
AVG RATING
</p>

<span className="metric-icon-box">
⭐
</span>

</div>

<h4 className="metric-value">
{avgRiderRating}
</h4>

<span className="metric-sub-badge badge-rating">
Rating Score
</span>


</div>







<div className="rest-metric-card">

<div className="card-header-row">

<p className="metric-title">
PENDING REVIEW
</p>

<span className="metric-icon-box">
⏳
</span>

</div>

<h4 className="metric-value">

{pendingRiders.length}

</h4>


<span className="metric-sub-badge badge-pending">
Needs Action
</span>


</div>


</div>







<div className="table-card">


<div className="directory-control-header">


<h3 className="directory-title">

{
activeTab==="Active Riders"
?
"Rider Directory"
:
activeTab==="Verification Requests"
?
"Verification Queue"
:
"Rejected Requests"
}

</h3>


<input

className="search-input"

placeholder="Search riders..."

value={search}

onChange={e=>setSearch(e.target.value)}

/>


</div>







<table className="rest-table">


<thead>

<tr>

<th>RIDER</th>
<th>CONTACT</th>
<th>LOCATION</th>
<th>STATUS</th>
<th>RELIABILITY</th>
<th>ACTION</th>

</tr>

</thead>




<tbody>


{

filtered.map(r=>(



<tr key={r.id}>


<td>


<div className="flex-cell">


<img

src={
profiles[r.uid] ||
profiles[r.id] ||
DEFAULT_IMAGE
}

className="table-row-avatar"

alt="rider"

/>



<div>

<p className="primary-text">

{r.name||"Unknown"}

</p>


<p className="secondary-text">

{r.email}

</p>


</div>


</div>


</td>





<td>

{r.phone||"N/A"}

</td>




<td>

{r.city||"N/A"}

<br/>

{r.address||""}

</td>





<td>

<span className="status-badge status-active">

<span className="dot"></span>

{r.isLive?"Online":"Offline"}

</span>

</td>


<td>

<span
  onClick={() => openReliabilityDetail(r)}
  style={{
    fontWeight: 'bold',
    cursor: 'pointer',
    textDecoration: 'underline',
    color: (r.reliabilityScore ?? 100) >= 85 ? '#2e7d32'
         : (r.reliabilityScore ?? 100) >= 50 ? '#f9a825'
         : '#c62828'
  }}
  title="Click to view strikes and adjust score"
>
  {(r.reliabilityScore ?? 100).toFixed(0)}
</span>
{r.isPaused && (
  <>
    <span className="status-badge" style={{ background: '#ffebee', color: '#c62828', marginLeft: '6px', fontSize: '11px', padding: '2px 6px', borderRadius: '4px' }}>
      PAUSED
    </span>
    <button
      className="btn-action-outline btn-green-outline"
      style={{ marginLeft: '6px', fontSize: '11px', padding: '2px 8px' }}
      onClick={() => handleReactivate(r.id, r.name)}
    >
      Reactivate
    </button>
  </>
)}

</td>


<td>


<div className="action-buttons">



{

activeTab==="Active Riders"

?

<>

<button

className="btn-action-outline btn-green-outline"

onClick={()=>goOffline(r.id)}

>

Go Offline

</button>

<button

className="btn-action-outline"

onClick={()=>setManagingUser(r)}

>

Manage

</button>

</>


:

activeTab==="Verification Requests"

?

<>


<button

className="btn-action-text text-green"

onClick={()=>approve(r.id)}

>

Approve

</button>



<button

className="btn-action-text text-red"

onClick={()=>startReject(r.id)}

>

Reject

</button>




<button

className="btn-action-solid-purple"

onClick={()=>setSelectedRequest(r)}

>

Details

</button>


</>

:

<button

className="btn-action-solid-purple"

onClick={()=>setSelectedRequest(r)}

>

Details

</button>

}



</div>


</td>



</tr>


))

}



</tbody>


</table>


</div>








{

selectedRequest &&


<div className="fullscreen-modal-overlay">


<div className="fullscreen-modal-container">



<div className="fullscreen-modal-header">


<h3>

{selectedRequest.name}

</h3>


<button

className="fullscreen-close-btn"

onClick={()=>setSelectedRequest(null)}

>

✕

</button>


</div>





<div className="fullscreen-modal-body">



<div className="fullscreen-detail-card">


<h5>
Rider Details
</h5>


<p>Name: {selectedRequest.name}</p>

<p>Email: {selectedRequest.email}</p>

<p>Phone: {selectedRequest.phone}</p>

<p>City: {selectedRequest.city}</p>

<p>Address: {selectedRequest.address}</p>

<p>Driving License Number: {selectedRequest.drivingLicenseNumber || 'N/A'}</p>

<p>Bike Registration Number: {selectedRequest.vehicleRegistrationNumber || 'N/A'}</p>


</div>







<div className="fullscreen-detail-card">


<h5>
Documents
</h5>



<img

src={
selectedRequest.cnicFrontUrl ||
selectedRequest.ownerCnicUrlfront ||
DEFAULT_IMAGE
}

className="fullscreen-image-wrapper"

alt="cnic front"

onClick={()=>setLightboxImage(selectedRequest.cnicFrontUrl || selectedRequest.ownerCnicUrlfront)}

/>




<img

src={
selectedRequest.cnicBackUrl ||
selectedRequest.ownerCnicUrlback ||
DEFAULT_IMAGE
}

className="fullscreen-image-wrapper"

alt="cnic back"

onClick={()=>setLightboxImage(selectedRequest.cnicBackUrl || selectedRequest.ownerCnicUrlback)}

/>



<img

src={
selectedRequest.selfieUrl ||
DEFAULT_IMAGE
}

className="fullscreen-image-wrapper"

alt="selfie"

onClick={()=>setLightboxImage(selectedRequest.selfieUrl)}

/>



<img

src={
selectedRequest.drivingLicenseImageUrl ||
DEFAULT_IMAGE
}

className="fullscreen-image-wrapper"

alt="driving license"

onClick={()=>setLightboxImage(selectedRequest.drivingLicenseImageUrl)}

/>



<img

src={
selectedRequest.bikeImageUrl ||
DEFAULT_IMAGE
}

className="fullscreen-image-wrapper"

alt="bike"

onClick={()=>setLightboxImage(selectedRequest.bikeImageUrl)}

/>


</div>




</div>






<div className="fullscreen-modal-footer">


<button

className="btn-reject"

onClick={()=>startReject(selectedRequest.id)}

>

Reject

</button>




<button

className="btn-approve"

onClick={()=>approve(selectedRequest.id)}

>

Approve

</button>


</div>




</div>


</div>


}



{rejectingId && (
<div className="modal-overlay" onClick={() => setRejectingId(null)}>
  <div className="reject-reason-modal" onClick={(e) => e.stopPropagation()}>
    <h3>Reason for rejection</h3>
    <p>This is sent to the applicant so they know exactly what to fix before resubmitting.</p>
    <textarea
      rows={4}
      value={rejectReason}
      onChange={(e) => setRejectReason(e.target.value)}
      placeholder="e.g. Vehicle registration photo is unreadable - please re-upload."
    />
    <div className="reject-reason-actions">
      <button className="btn-secondary" onClick={() => setRejectingId(null)}>Cancel</button>
      <button className="btn-reject" onClick={confirmReject}>Confirm Reject</button>
    </div>
  </div>
</div>
)}

{reliabilityDetail && (
<div className="modal-overlay" onClick={() => setReliabilityDetail(null)}>
  <div className="reject-reason-modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '480px' }}>
    <h3>{reliabilityDetail.name || 'Rider'} - Reliability</h3>

    <p>
      Current score: <b>{(reliabilityDetail.reliabilityScore ?? 100).toFixed(0)} / 100</b>
      {reliabilityDetail.isPaused && <span style={{ color: '#c62828', fontWeight: 'bold' }}> (Paused)</span>}
    </p>

    <h4 style={{ marginTop: '14px', marginBottom: '6px' }}>Strike History</h4>

    {loadingStrikes ? (
      <p>Loading...</p>
    ) : strikesList.length === 0 ? (
      <p style={{ color: '#888' }}>No strikes recorded.</p>
    ) : (
      <div style={{ maxHeight: '180px', overflowY: 'auto', border: '1px solid #eee', borderRadius: '6px', padding: '8px' }}>
        {strikesList.map((s) => (
          <div key={s.id} style={{ padding: '6px 0', borderBottom: '1px solid #f0f0f0' }}>
            <p style={{ margin: 0, fontWeight: 'bold' }}>
              {s.reason === 'order_rejected' ? 'Order Rejected'
                : s.reason === 'missed_prep_deadline' ? 'Missed Prep Deadline'
                : s.reason === 'rider_search_exhausted' ? 'No Response To Dispatch'
                : (s.reason || 'Strike')}
            </p>
            <p style={{ margin: 0, fontSize: '12px', color: '#888' }}>
              Order: {s.orderId || 'N/A'} &middot; {s.at ? new Date(s.at).toLocaleString() : 'Unknown date'}
            </p>
          </div>
        ))}
      </div>
    )}

    <h4 style={{ marginTop: '14px', marginBottom: '6px' }}>Adjust Score Manually</h4>
    <p style={{ fontSize: '12px', color: '#888', marginTop: 0 }}>
      Use this if the issue behind these strikes has been resolved and the rider
      shouldn't have to wait for it to recover on its own.
    </p>
    <div style={{ display: 'flex', gap: '8px' }}>
      <input
        type="number"
        min="0"
        max="100"
        value={newScoreInput}
        onChange={(e) => setNewScoreInput(e.target.value)}
        placeholder="0-100"
        style={{ flex: 1, padding: '6px', borderRadius: '6px', border: '1px solid #ccc' }}
      />
      <button className="btn-action-text text-green" onClick={handleAdjustScore}>Set Score</button>
    </div>

    <div className="reject-reason-actions" style={{ marginTop: '16px' }}>
      {reliabilityDetail.isPaused && (
        <button
          className="btn-action-text text-green"
          onClick={() => { handleReactivate(reliabilityDetail.id, reliabilityDetail.name); setReliabilityDetail(null); }}
        >
          Reactivate Account
        </button>
      )}
      <button className="btn-secondary" onClick={() => setReliabilityDetail(null)}>Close</button>
    </div>
  </div>
</div>
)}

{lightboxImage && (
<div className="modal-overlay" onClick={() => setLightboxImage(null)}>
  <img src={lightboxImage} alt="Enlarged document" className="lightbox-image" onClick={(e) => e.stopPropagation()} />
  <button className="lightbox-close-btn" onClick={() => setLightboxImage(null)}>✕</button>
</div>
)}

{managingUser && (
<AccountActionsModal
user={managingUser}
role="Delivery"
onClose={() => setManagingUser(null)}
/>
)}

</div>

);

};


export default Riders;