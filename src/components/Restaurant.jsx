import { useEffect, useState } from 'react';
import {
  collection,
  onSnapshot,
  query,
  doc,
  updateDoc
} from 'firebase/firestore';
import { db } from '../firebase/config';
import AccountActionsModal from './AccountActionsModal';
import './Restaurant.css';

const DEFAULT_LOGO = 'https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=600&q=80';
const DEFAULT_DOC = 'https://images.unsplash.com/photo-1521791136064-7986c2920216?auto=format&fit=crop&w=600&q=80';

const Restaurant = () => {
  const [activeTab, setActiveTab] = useState('Active Partners'); 
  const [allRestaurants, setAllRestaurants] = useState([]);
  const [error, setError] = useState(null);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [rejectingId, setRejectingId] = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const [lightboxImage, setLightboxImage] = useState(null);
  const [managingUser, setManagingUser] = useState(null);

  useEffect(() => {
    const partnersRef = collection(db, 'Users', 'Restaurant', 'VerifiedRegister');
    const qPartners = query(partnersRef);
    
    const unsubscribe = onSnapshot(
      qPartners,
      (snapshot) => {
        const list = [];
        snapshot.forEach((docSnap) => {
          list.push({ id: docSnap.id, ...docSnap.data() });
        });
        setAllRestaurants(list);
      },
      (err) => {
        console.error("Error loading VerifiedRegister data: ", err);
        setError('Failed to load restaurant data.');
      }
    );

    return () => unsubscribe();
  }, []);

  const activePartners = allRestaurants.filter(r => 
    r.status?.toLowerCase() === 'approved' || r.status?.toLowerCase() === 'active' || r.isVerified === true
  );

  const verificationRequests = allRestaurants.filter(r => 
    r.status?.toLowerCase() === 'pending' || r.status?.toLowerCase() === 'requested' || (!r.status && !r.isVerified)
  );

  const rejectedPartners = allRestaurants.filter(r =>
    r.status?.toLowerCase() === 'rejected'
  );

  const pendingCount = verificationRequests.length;
  const totalPartners = activePartners.length;
  const activeNowCount = activePartners.filter(p => p.isLive === true).length;
  
  let totalRating = 0;
  let ratedCount = 0;
  activePartners.forEach(p => {
    if (p.rating) {
      totalRating += Number(p.rating);
      ratedCount++;
    }
  });
  const avgRating = ratedCount > 0 ? (totalRating / ratedCount).toFixed(1) : '0.0';

  const toggleLiveStatus = async (id) => {
  try {
    await updateDoc(doc(db, 'Users', 'Restaurant', 'VerifiedRegister', id), {
      isLive: false,
      status: 'Pending',
      isVerified: false,
      verified: false
    });
  } catch (err) {
    console.error("Live status switch error: ", err);
    setError('Could not change live status.');
  }
};

  const handleApprove = async (id) => {

    if (!window.confirm('Are you sure you want to approve this restaurant? It will go live immediately.')) {
      return;
    }

    try {
      await updateDoc(doc(db, 'Users', 'Restaurant', 'VerifiedRegister', id), {
        status: 'Approved',
        isVerified: true,
        verified: true,
        isLive: true,
        // Read by the app's login/dashboard gate and by the
        // onRestaurantVerificationChanged Cloud Function trigger, which
        // sends the "you're verified, you can log in" notification.
        verificationStatus: 'verified',
        rejectionReason: null,
      });
      setSelectedRequest(null);
    } catch (err) {
      console.error("Approve error: ", err);
      setError('Could not approve restaurant.');
    }
  };

  /**
   * Opens the reason prompt instead of rejecting immediately - a reason is
   * required so the applicant knows exactly what to fix and can resubmit
   * the same account, instead of guessing or creating a duplicate one.
   */
  const startReject = (id) => {
    setRejectingId(id);
    setRejectReason('');
  };

  const confirmReject = async () => {

    if (!rejectingId) return;

    if (!rejectReason.trim()) {
      setError('Please enter a reason before rejecting.');
      return;
    }

    try {
      await updateDoc(doc(db, 'Users', 'Restaurant', 'VerifiedRegister', rejectingId), {
        status: 'Rejected',
        isVerified: false,
        verified: false,
        isLive: false,
        verificationStatus: 'rejected',
        rejectionReason: rejectReason.trim(),
      });
      setSelectedRequest(null);
      setRejectingId(null);
      setRejectReason('');
    } catch (err) {
      console.error("Reject error: ", err);
      setError('Could not reject restaurant.');
    }
  };

  const filteredActive = activePartners.filter(p => {
    const term = searchQuery.toLowerCase();
    const nameToSearch = p.restaurantName || p.name || '';
    const idToSearch = p.id || '';
    const addressToSearch = p.address || p.location || p.city || '';
    return (
      nameToSearch.toLowerCase().includes(term) ||
      idToSearch.toLowerCase().includes(term) ||
      addressToSearch.toLowerCase().includes(term)
    );
  });

  const filteredPending = verificationRequests.filter(r => {
    const term = searchQuery.toLowerCase();
    const nameToSearch = r.restaurantName || r.name || '';
    const ownerToSearch = r.ownerName || '';
    return (
      nameToSearch.toLowerCase().includes(term) ||
      ownerToSearch.toLowerCase().includes(term)
    );
  });

  const filteredRejected = rejectedPartners.filter(r => {
    const term = searchQuery.toLowerCase();
    const nameToSearch = r.restaurantName || r.name || '';
    const ownerToSearch = r.ownerName || '';
    return (
      nameToSearch.toLowerCase().includes(term) ||
      ownerToSearch.toLowerCase().includes(term)
    );
  });

  const openImageInNewTab = (url) => {
    if (url) {
      setLightboxImage(url);
    }
  };

  const getCnicFrontImage = (req) => {
    return req.cnicFrontUrl || req.ownerCnicImageUrl || req.CNICImage || req.cnicImage || null;
  };

  const getCnicBackImage = (req) => {
    return req.cnicBackUrl || null;
  };

  const getLicenseImage = (req) => {
    return req.licenseImageUrl || req.tradeLicenseImage || req.licenseImage || req.license || req.licenseUrl || null;
  };

  const getSelfieImage = (req) => {
    return req.selfieUrl || null;
  };

  const getLogoImage = (req) => {
    // profileImageUrl is what the app's own profile-picture upload writes;
    // selfieUrl is the verification selfie, used as a fallback so new
    // requests that haven't set a profile picture yet still show a face
    // instead of a blank avatar.
    return req.profileImageUrl || req.selfieUrl || req.logoImage || req.restaurantLogo || req.logo || req.logo_url || null;
  };

  return (
    <div className="restaurant-container">
      
      {/* 1. TOP PILL TABS BAR */}
      <div className="tab-buttons-group">
        <button 
          className={`btn-tab ${activeTab === 'Active Partners' ? 'btn-tab-active' : ''}`}
          onClick={() => { setActiveTab('Active Partners'); setSearchQuery(''); }}
        >
          <span className="tab-prefix-icon">➔</span> Active Partners
        </button>
        <button 
          className={`btn-tab ${activeTab === 'Verification Requests' ? 'btn-tab-active' : ''}`}
          onClick={() => { setActiveTab('Verification Requests'); setSearchQuery(''); }}
        >
          <span className="tab-prefix-icon">📋</span> Verification Requests 
          <span className="tab-badge-count">{pendingCount}</span>
        </button>
        <button 
          className={`btn-tab ${activeTab === 'Rejected' ? 'btn-tab-active' : ''}`}
          onClick={() => { setActiveTab('Rejected'); setSearchQuery(''); }}
        >
          <span className="tab-prefix-icon">✕</span> Rejected
          <span className="tab-badge-count">{rejectedPartners.length}</span>
        </button>
      </div>

      {/* 2. PAGE HEADING SECTION */}
      <div className="restaurant-page-header">
        <h2>Restaurant Partners</h2>
        <p>Manage your food delivery network partners and monitor registration logs.</p>
      </div>

      {/* 3. METRICS GRID WITH TOP-RIGHT ICONS MATCH */}
      <div className="metrics-grid">
        <div className="rest-metric-card">
          <div className="card-header-row">
            <p className="metric-title">TOTAL PARTNERS</p>
            <span className="metric-icon-box blue-icon">🏪</span>
          </div>
          <h4 className="metric-value">{totalPartners}</h4>
          <span className="metric-sub-badge badge-approved">↑ 12% from last month</span>
        </div>

        <div className="rest-metric-card">
          <div className="card-header-row">
            <p className="metric-title">ONLINE NOW</p>
            <span className="metric-icon-box green-icon">🟢</span>
          </div>
          <h4 className="metric-value">{activeNowCount}</h4>
          <span className="metric-sub-badge badge-live">Currently live on track</span>
        </div>

        <div className="rest-metric-card">
          <div className="card-header-row">
            <p className="metric-title">AVG. RATING</p>
            <span className="metric-icon-box orange-icon">★</span>
          </div>
          <h4 className="metric-value">{avgRating}</h4>
          <span className="metric-sub-badge badge-rating">Rating Score</span>
        </div>

        <div className="rest-metric-card">
          <div className="card-header-row">
            <p className="metric-title">PENDING REVIEW</p>
            <span className="metric-icon-box red-icon">⏳</span>
          </div>
          <h4 className="metric-value">{pendingCount}</h4>
          <span className="metric-sub-badge badge-pending">Needs action</span>
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {/* 4. CONTENT MAIN DIRECTORY CARD */}
      <div className="table-card">
        
        {/* Directory Header with Search aligned like Template */}
        <div className="directory-control-header">
          <h3 className="directory-title">
            {activeTab === 'Active Partners' ? 'Partner Directory' : activeTab === 'Verification Requests' ? 'Verification Queue' : 'Rejected Requests'}
          </h3>
          <div className="search-box-container">
            <input 
              type="text" 
              placeholder="Search verified partners..."
              className="search-input"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>

        {/* Dynamic Data Content */}
        {activeTab === 'Active Partners' ? (
          <div className="table-responsive">
            <table className="rest-table">
              <thead>
                <tr>
                  <th>RESTAURANT NAME</th>
                  <th>LOCATION</th>
                  <th>STATUS</th>
                  <th>RATING</th>
                  <th className="text-right">ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                {filteredActive.map((partner) => (
                  <tr key={partner.id}>
                    <td>
                      <div className="flex-cell">
                        <img src={getLogoImage(partner) || DEFAULT_LOGO} alt="Logo" className="table-row-avatar" />
                        <div>
                          <p className="primary-text">{partner.restaurantName || 'Unnamed'}</p>
                          <p className="secondary-text">ID: {partner.id}</p>
                        </div>
                      </div>
                    </td>
                    <td>
                      <p className="primary-text">{partner.city || 'Pakistan'}</p>
                      <p className="secondary-text">{partner.address || 'N/A'}</p>
                    </td>
                    <td>
                      <span className={`status-badge ${partner.isLive ? 'status-active' : 'status-inactive'}`}>
                        <span className="dot"></span>
                        {partner.isLive ? 'Live Now' : 'Offline'}
                      </span>
                    </td>
                    <td>
                      <div className="rating-cell">
                        <span className="rating-score">{partner.rating || '0.0'}</span>
                        <span className="rating-count">({partner.ratingCount || 0})</span>
                      </div>
                    </td>
                    <td className="text-right">
                      <div className="action-buttons">
                        <button 
                          className={`btn-action-outline ${partner.isLive ? 'btn-red-outline' : 'btn-green-outline'}`}
                          onClick={() => toggleLiveStatus(partner.id, partner.isLive || false)}
                        >
                          {partner.isLive ? 'Go Offline' : 'Go Live'}
                        </button>
                        <button
                          className="btn-action-outline"
                          onClick={() => setManagingUser(partner)}
                        >
                          Manage
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredActive.length === 0 && (
                  <tr>
                    <td colSpan="5" className="empty-table-cell">No active partners found.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        ) : activeTab === 'Verification Requests' ? (
          <div className="table-responsive">
            <table className="rest-table">
              <thead>
                <tr>
                  <th>RESTAURANT NAME</th>
                  <th>OWNER DETAILS</th>
                  <th>CITY</th>
                  <th>STATUS</th>
                  <th className="text-right">ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                {filteredPending.map((request) => (
                  <tr key={request.id}>
                    <td>
                      <div className="flex-cell">
                        <img src={getLogoImage(request) || DEFAULT_LOGO} alt="Logo" className="table-row-logo" />
                        <div>
                          <p className="primary-text">{request.restaurantName || 'Unnamed Rest.'}</p>
                          <p className="secondary-text">{request.address || 'N/A'}</p>
                        </div>
                      </div>
                    </td>
                    <td>
                      <p className="primary-text">{request.ownerName || 'N/A'}</p>
                      <p className="secondary-text">{request.email || 'N/A'}</p>
                    </td>
                    <td>{request.city || 'N/A'}</td>
                    <td>
                      <span className="status-badge status-pending">
                        <span className="dot"></span>Pending
                      </span>
                    </td>
                    <td className="text-right">
                      <div className="action-buttons">
                        <button className="btn-action-text text-red" onClick={() => startReject(request.id)}>Reject</button>
                        <button className="btn-action-text text-green" onClick={() => handleApprove(request.id)}>Approve</button>
                        <button className="btn-action-solid-purple" onClick={() => setSelectedRequest(request)}>Details</button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredPending.length === 0 && (
                  <tr>
                    <td colSpan="5" className="empty-table-cell">No pending verification requests found.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="rest-table">
              <thead>
                <tr>
                  <th>RESTAURANT NAME</th>
                  <th>OWNER DETAILS</th>
                  <th>CITY</th>
                  <th>REASON</th>
                  <th className="text-right">ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                {filteredRejected.map((request) => (
                  <tr key={request.id}>
                    <td>
                      <div className="flex-cell">
                        <img src={getLogoImage(request) || DEFAULT_LOGO} alt="Logo" className="table-row-logo" />
                        <div>
                          <p className="primary-text">{request.restaurantName || 'Unnamed Rest.'}</p>
                          <p className="secondary-text">{request.address || 'N/A'}</p>
                        </div>
                      </div>
                    </td>
                    <td>
                      <p className="primary-text">{request.ownerName || 'N/A'}</p>
                      <p className="secondary-text">{request.email || 'N/A'}</p>
                    </td>
                    <td>{request.city || 'N/A'}</td>
                    <td>
                      <p className="secondary-text">{request.rejectionReason || 'No reason recorded'}</p>
                    </td>
                    <td className="text-right">
                      <div className="action-buttons">
                        <button className="btn-action-solid-purple" onClick={() => setSelectedRequest(request)}>Details</button>
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredRejected.length === 0 && (
                  <tr>
                    <td colSpan="5" className="empty-table-cell">No rejected requests.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 5. FULL SCREEN DETAILS MODAL PORTAL */}
      {selectedRequest && (
        <div className="fullscreen-modal-overlay">
          <div className="fullscreen-modal-container">
            <div className="fullscreen-modal-header">
              <div className="header-left-identity">
                <img 
                  src={getLogoImage(selectedRequest) || DEFAULT_LOGO} 
                  alt="Logo" 
                  className="fullscreen-modal-avatar" 
                  onClick={() => openImageInNewTab(getLogoImage(selectedRequest))}
                />
                <div>
                  <h3>{selectedRequest.restaurantName || 'Unnamed Restaurant'}</h3>
                  <p>{selectedRequest.city || 'Pakistan'}</p>
                </div>
              </div>
              <button className="fullscreen-close-btn" onClick={() => setSelectedRequest(null)}>✕ Close</button>
            </div>

            <div className="fullscreen-modal-body">
              <div className="fullscreen-left-column">
                <div className="fullscreen-detail-card">
                  <h5>Basic Restaurant Information</h5>
                  <div className="fullscreen-detail-item"><span>Restaurant Name:</span><p>{selectedRequest.restaurantName || 'N/A'}</p></div>
                  <div className="fullscreen-detail-item"><span>City / Region:</span><p>{selectedRequest.city || 'N/A'}</p></div>
                  <div className="fullscreen-detail-item"><span>License Number:</span><p>{selectedRequest.licenseNo || 'N/A'}</p></div>
                  <div className="fullscreen-detail-item"><span>Complete Address:</span><p>{selectedRequest.address || 'N/A'}</p></div>
                </div>

                <div className="fullscreen-detail-card">
                  <h5>Owner & Contact Information</h5>
                  <div className="fullscreen-detail-item"><span>Owner Full Name:</span><p>{selectedRequest.ownerName || 'N/A'}</p></div>
                  <div className="fullscreen-detail-item"><span>Phone Number:</span><p>{selectedRequest.phone || 'N/A'}</p></div>
                  <div className="fullscreen-detail-item"><span>Email Address:</span><p>{selectedRequest.email || 'N/A'}</p></div>
                </div>
              </div>

              <div className="fullscreen-right-column">
                <h4>Uploaded Verification Documents</h4>
                <div className="fullscreen-documents-grid">
                  <div className="fullscreen-doc-box">
                    <span className="fullscreen-doc-title">CNIC Front</span>
                    <div className="fullscreen-image-wrapper" onClick={() => openImageInNewTab(getCnicFrontImage(selectedRequest))}>
                      <img src={getCnicFrontImage(selectedRequest) || DEFAULT_DOC} alt="CNIC Front" onError={(e) => { e.currentTarget.src = DEFAULT_DOC; }} />
                    </div>
                  </div>
                  <div className="fullscreen-doc-box">
                    <span className="fullscreen-doc-title">CNIC Back</span>
                    <div className="fullscreen-image-wrapper" onClick={() => openImageInNewTab(getCnicBackImage(selectedRequest))}>
                      <img src={getCnicBackImage(selectedRequest) || DEFAULT_DOC} alt="CNIC Back" onError={(e) => { e.currentTarget.src = DEFAULT_DOC; }} />
                    </div>
                  </div>
                  <div className="fullscreen-doc-box">
                    <span className="fullscreen-doc-title">Official Food / Trade License</span>
                    <div className="fullscreen-image-wrapper" onClick={() => openImageInNewTab(getLicenseImage(selectedRequest))}>
                      <img src={getLicenseImage(selectedRequest) || DEFAULT_DOC} alt="License" onError={(e) => { e.currentTarget.src = DEFAULT_DOC; }} />
                    </div>
                  </div>
                  <div className="fullscreen-doc-box">
                    <span className="fullscreen-doc-title">Live Selfie</span>
                    <div className="fullscreen-image-wrapper" onClick={() => openImageInNewTab(getSelfieImage(selectedRequest))}>
                      <img src={getSelfieImage(selectedRequest) || DEFAULT_DOC} alt="Selfie" onError={(e) => { e.currentTarget.src = DEFAULT_DOC; }} />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="fullscreen-modal-footer">
              <button className="btn-reject" onClick={() => startReject(selectedRequest.id)}>Reject Partner Request</button>
              <button className="btn-approve" onClick={() => handleApprove(selectedRequest.id)}>Approve & Go Live</button>
            </div>
          </div>
        </div>
      )}

      {rejectingId && (
        <div className="modal-overlay" onClick={() => setRejectingId(null)}>
          <div className="reject-reason-modal" onClick={(e) => e.stopPropagation()}>
            <h3>Reason for rejection</h3>
            <p>This is sent to the applicant so they know exactly what to fix before resubmitting.</p>
            <textarea
              rows={4}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="e.g. License number could not be verified - please re-upload a clearer photo."
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
          role="Restaurant"
          onClose={() => setManagingUser(null)}
        />
      )}
    </div>
  );
};

export default Restaurant;