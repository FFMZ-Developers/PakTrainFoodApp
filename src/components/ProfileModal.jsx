import { useState } from "react";
import { auth, db, storage } from "../firebase/config";
import { doc, setDoc } from "firebase/firestore";
import { ref, uploadBytesResumable, getDownloadURL } from "firebase/storage";
import {
  EmailAuthProvider,
  reauthenticateWithCredential,
  updatePassword,
  updateProfile,
} from "firebase/auth";
import "./ProfileModal.css";

// Backs the "My Account" popup opened by clicking the profile area in the
// topbar. Two independent things happen here:
//   1. Profile picture -> resized client-side (phone camera photos can be
//      5-10MB, which is what actually made uploads feel stuck), then
//      uploaded to Firebase Storage with progress tracking. The
//      resulting URL is saved on admins/{uid}.photoURL (Dashboard.jsx is
//      subscribed to that doc, so the header avatar updates instantly).
//   2. Change password -> Firebase requires re-entering the current
//      password (reauthenticateWithCredential) right before
//      updatePassword, otherwise it rejects the request as
//      "requires-recent-login".

/**
 * Shrinks an image to at most `maxDim` on its longest side and re-encodes
 * it as a JPEG at the given quality. A typical phone photo (4000x3000,
 * 6-8MB) comes out well under 300KB after this - the actual fix for slow
 * uploads, since almost none of that original resolution is needed for a
 * 36px header avatar / 64px modal preview.
 */
function resizeImage(file, maxDim = 500, quality = 0.8) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const objectUrl = URL.createObjectURL(file);

    img.onload = () => {
      URL.revokeObjectURL(objectUrl);

      let { width, height } = img;
      if (width > height && width > maxDim) {
        height = Math.round((height * maxDim) / width);
        width = maxDim;
      } else if (height > maxDim) {
        width = Math.round((width * maxDim) / height);
        height = maxDim;
      }

      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      canvas.getContext("2d").drawImage(img, 0, 0, width, height);

      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error("Could not process image."))),
        "image/jpeg",
        quality,
      );
    };

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("Could not read image file."));
    };

    img.src = objectUrl;
  });
}

/**
 * 4 built-in avatars (colored circle + simple icon), generated as inline
 * SVG data URIs - no upload, no Storage dependency, so these always work
 * even if Storage isn't set up yet or is misconfigured.
 */
const PRESET_AVATARS = [
  { id: "shield-blue", color: "#2563eb", path: "M16 2 L28 8 V16 C28 24 22 29 16 30 C10 29 4 24 4 16 V8 Z" },
  { id: "star-purple", color: "#7c3aed", path: "M16 2 L20 12 L30 12 L22 18 L25 28 L16 22 L7 28 L10 18 L2 12 L12 12 Z" },
  { id: "crown-amber", color: "#d97706", path: "M4 24 L4 12 L11 18 L16 8 L21 18 L28 12 L28 24 Z" },
  { id: "bolt-green", color: "#059669", path: "M18 2 L6 18 H14 L12 30 L26 12 H18 Z" },
];

function buildPresetAvatarUrl(preset) {
  const svg =
    `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'>` +
    `<circle cx='16' cy='16' r='16' fill='${preset.color}'/>` +
    `<path d='${preset.path}' fill='none' stroke='white' stroke-width='2' stroke-linejoin='round' stroke-linecap='round'/>` +
    `</svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}


const ProfileModal = ({ profile, onClose }) => {
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [photoError, setPhotoError] = useState("");
  const [photoSuccess, setPhotoSuccess] = useState("");

  const [passwordForm, setPasswordForm] = useState({ current: "", next: "", confirm: "" });
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState("");
  const [passwordSuccess, setPasswordSuccess] = useState("");

  const handlePictureChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setPhotoError("");
    setPhotoSuccess("");

    if (!file.type.startsWith("image/")) {
      setPhotoError("Please choose an image file.");
      return;
    }
    if (file.size > 15 * 1024 * 1024) {
      setPhotoError("Image must be under 15MB.");
      return;
    }

    const user = auth.currentUser;
    if (!user) return;

    setUploading(true);
    setUploadProgress(0);
    try {
      const resizedBlob = await resizeImage(file);

      // Always the same path per admin, so re-uploading just overwrites
      // the old picture instead of piling up files in Storage.
      const fileRef = ref(storage, `admin-avatars/${user.uid}`);
      const uploadTask = uploadBytesResumable(fileRef, resizedBlob, {
        contentType: "image/jpeg",
      });

      const url = await new Promise((resolve, reject) => {
        // If nothing has happened after 25s, the upload is almost
        // certainly not going to complete on its own - most commonly
        // because Firebase Storage was never "enabled" in the console,
        // or its security rules are silently rejecting the write.
        const timeoutId = setTimeout(() => {
          uploadTask.cancel();
          reject(new Error(
            "Upload is taking too long. This usually means Firebase Storage isn't enabled yet, or its rules are blocking the write. Check Firebase Console > Storage.",
          ));
        }, 25000);

        uploadTask.on(
          "state_changed",
          (snapshot) => {
            setUploadProgress(Math.round((snapshot.bytesTransferred / snapshot.totalBytes) * 100));
          },
          (err) => {
            clearTimeout(timeoutId);
            reject(err);
          },
          async () => {
            clearTimeout(timeoutId);
            try {
              resolve(await getDownloadURL(uploadTask.snapshot.ref));
            } catch (err) {
              reject(err);
            }
          },
        );
      });

      await setDoc(doc(db, "admins", user.uid), { photoURL: url }, { merge: true });
      await updateProfile(user, { photoURL: url }).catch(() => {
        // Non-fatal - the Firestore doc (which the app actually reads
        // from) is already updated above.
      });

      setPhotoSuccess("Profile picture updated.");
    } catch (err) {
      console.error("profile picture upload error:", err);
      setPhotoError(err.message || "Could not upload picture.");
    } finally {
      setUploading(false);
    }
  };

  const handleRemovePicture = async () => {
    const user = auth.currentUser;
    if (!user) return;

    setPhotoError("");
    setPhotoSuccess("");
    try {
      await setDoc(doc(db, "admins", user.uid), { photoURL: "" }, { merge: true });
      await updateProfile(user, { photoURL: "" }).catch(() => {});
      setPhotoSuccess("Profile picture removed.");
    } catch (err) {
      console.error("remove picture error:", err);
      setPhotoError(err.message || "Could not remove picture.");
    }
  };

  const handleSelectPreset = async (preset) => {
    const user = auth.currentUser;
    if (!user) return;

    setPhotoError("");
    setPhotoSuccess("");
    try {
      const url = buildPresetAvatarUrl(preset);
      await setDoc(doc(db, "admins", user.uid), { photoURL: url }, { merge: true });
      await updateProfile(user, { photoURL: url }).catch(() => {});
      setPhotoSuccess("Avatar updated.");
    } catch (err) {
      console.error("preset avatar error:", err);
      setPhotoError(err.message || "Could not set avatar.");
    }
  };

  const handlePasswordSubmit = async (e) => {
    e.preventDefault();
    setPasswordError("");
    setPasswordSuccess("");

    if (!passwordForm.current || !passwordForm.next || !passwordForm.confirm) {
      setPasswordError("Please fill in all password fields.");
      return;
    }
    if (passwordForm.next.length < 6) {
      setPasswordError("New password must be at least 6 characters.");
      return;
    }
    if (passwordForm.next !== passwordForm.confirm) {
      setPasswordError("New password and confirmation don't match.");
      return;
    }

    const user = auth.currentUser;
    if (!user || !user.email) return;

    setChangingPassword(true);
    try {
      // Firebase blocks a password change unless the session is
      // "recent" - re-authenticating with the current password proves
      // it's really them before we let them set a new one.
      const credential = EmailAuthProvider.credential(user.email, passwordForm.current);
      await reauthenticateWithCredential(user, credential);
      await updatePassword(user, passwordForm.next);

      setPasswordSuccess("Password changed successfully.");
      setPasswordForm({ current: "", next: "", confirm: "" });
    } catch (err) {
      console.error("password change error:", err);
      if (err.code === "auth/wrong-password" || err.code === "auth/invalid-credential") {
        setPasswordError("Current password is incorrect.");
      } else {
        setPasswordError(err.message || "Could not change password.");
      }
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <div className="profile-modal-overlay" onClick={onClose}>
      <div className="profile-modal" onClick={(e) => e.stopPropagation()}>
        <div className="profile-modal-header">
          <h3>My Account</h3>
          <button className="profile-modal-close" onClick={onClose} aria-label="Close">&times;</button>
        </div>

        {/* Profile picture */}
        <div className="profile-modal-section">
          <h4>Profile picture</h4>
          <div className="profile-modal-avatar-row">
            {profile.photoURL ? (
              <img src={profile.photoURL} alt="Profile" className="profile-modal-avatar-img" />
            ) : (
              <div className="profile-modal-avatar-placeholder">
                {(profile.name || profile.email || "A").charAt(0).toUpperCase()}
              </div>
            )}
            <label className="profile-modal-upload-btn">
              {uploading ? `Uploading... ${uploadProgress}%` : "Change picture"}
              <input type="file" accept="image/*" hidden onChange={handlePictureChange} disabled={uploading} />
            </label>
            {profile.photoURL && (
              <button
                type="button"
                className="profile-modal-remove-btn"
                onClick={handleRemovePicture}
                disabled={uploading}
              >
                Remove
              </button>
            )}
          </div>

          <p className="profile-modal-or">Or pick an avatar</p>
          <div className="profile-modal-presets">
            {PRESET_AVATARS.map((preset) => (
              <button
                key={preset.id}
                type="button"
                className="profile-modal-preset-btn"
                onClick={() => handleSelectPreset(preset)}
                disabled={uploading}
                title="Use this avatar"
              >
                <img src={buildPresetAvatarUrl(preset)} alt="" />
              </button>
            ))}
          </div>

          {photoError && <div className="profile-modal-error">{photoError}</div>}
          {photoSuccess && <div className="profile-modal-success">{photoSuccess}</div>}
        </div>

        {/* Change password */}
        <div className="profile-modal-section">
          <h4>Change password</h4>
          <form onSubmit={handlePasswordSubmit} className="profile-modal-form">
            <input
              type="password"
              placeholder="Current password"
              value={passwordForm.current}
              onChange={(e) => setPasswordForm((p) => ({ ...p, current: e.target.value }))}
              autoComplete="current-password"
            />
            <input
              type="password"
              placeholder="New password"
              value={passwordForm.next}
              onChange={(e) => setPasswordForm((p) => ({ ...p, next: e.target.value }))}
              autoComplete="new-password"
            />
            <input
              type="password"
              placeholder="Confirm new password"
              value={passwordForm.confirm}
              onChange={(e) => setPasswordForm((p) => ({ ...p, confirm: e.target.value }))}
              autoComplete="new-password"
            />
            {passwordError && <div className="profile-modal-error">{passwordError}</div>}
            {passwordSuccess && <div className="profile-modal-success">{passwordSuccess}</div>}
            <button type="submit" className="profile-modal-submit" disabled={changingPassword}>
              {changingPassword ? "Changing..." : "Change Password"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default ProfileModal;
