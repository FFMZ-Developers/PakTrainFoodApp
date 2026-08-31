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
0.0
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