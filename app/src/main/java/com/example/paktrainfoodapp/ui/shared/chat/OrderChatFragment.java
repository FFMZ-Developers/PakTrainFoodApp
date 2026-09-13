package com.example.paktrainfoodapp.ui.shared.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-order chat between the rider and one counterparty.
 *
 * There are two separate conversations on any order, and which one you're
 * in is decided by CHAT_TYPE:
 *
 *   "restaurant" - rider <-> restaurant, live from the moment the rider
 *                  accepts until pickup (after pickup the restaurant's
 *                  part is done, so that thread closes)
 *   "passenger"  - rider <-> passenger, live from pickup until the order
 *                  completes
 *
 * Messages live at Orders/{orderId}/chats_{type}/messages, so a
 * conversation is inherently scoped to a single order - it disappears
 * from both users' screens when the order finishes (nothing is deleted
 * though: the admin panel can still read every thread for dispute review).
 */
public class OrderChatFragment extends Fragment {

    private static final String ARG_ORDER_ID = "orderId";
    private static final String ARG_CHAT_TYPE = "chatType";
    private static final String ARG_TITLE = "title";
    private static final String ARG_PHONE = "phone";

    public static final String TYPE_RESTAURANT = "restaurant";
    public static final String TYPE_PASSENGER = "passenger";

    public static OrderChatFragment newInstance(String orderId, String chatType,
                                                String title, String phone) {

        OrderChatFragment f = new OrderChatFragment();

        Bundle b = new Bundle();
        b.putString(ARG_ORDER_ID, orderId);
        b.putString(ARG_CHAT_TYPE, chatType);
        b.putString(ARG_TITLE, title);
        b.putString(ARG_PHONE, phone);
        f.setArguments(b);

        return f;
    }

    private String orderId, chatType, title, phone, myUid;

    private RecyclerView recycler;
    private EditText editMessage;
    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();

    private FirebaseFirestore db;
    private ListenerRegistration messagesRegistration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_order_chat, container, false);

        if (getArguments() != null) {
            orderId = getArguments().getString(ARG_ORDER_ID);
            chatType = getArguments().getString(ARG_CHAT_TYPE);
            title = getArguments().getString(ARG_TITLE);
            phone = getArguments().getString(ARG_PHONE);
        }

        db = FirebaseFirestore.getInstance();

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        TextView txtTitle = v.findViewById(R.id.txtChatTitle);
        String myRole = new com.example.paktrainfoodapp.utils.PrefManager(requireContext())
                .getUserRole();

        txtTitle.setText(headerTitleFor(myRole, chatType, title));

        TextView txtSubtitle = v.findViewById(R.id.txtChatSubtitle);
        // Shows the same sequential number the order cards show, rather
        // than the raw document id.
        txtSubtitle.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils
                .format(null, orderId));

        if (orderId != null) {
            db.collection("Orders").document(orderId).get()
                    .addOnSuccessListener(doc -> {
                        if (!isAdded() || !doc.exists()) return;
                        txtSubtitle.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils
                                .format(doc.getLong("orderNumber"), orderId));
                    });
        }

        ImageView btnBack = v.findViewById(R.id.btnChatBack);
        btnBack.setOnClickListener(view -> {
            if (isAdded()) requireActivity().getSupportFragmentManager().popBackStack();
        });

        ImageView btnCall = v.findViewById(R.id.btnChatCall);
        btnCall.setOnClickListener(view -> dialPhone());

        recycler = v.findViewById(R.id.recyclerChat);
        editMessage = v.findViewById(R.id.editChatMessage);

        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        lm.setStackFromEnd(true); // newest at the bottom, like any chat app
        recycler.setLayoutManager(lm);

        adapter = new ChatAdapter(messages, myUid);
        recycler.setAdapter(adapter);

        View btnSend = v.findViewById(R.id.btnChatSend);
        btnSend.setOnClickListener(view -> sendMessage());

        // Stays muted until there's actually something to send - a bright
        // always-on button implies it does something even when empty.
        btnSend.setAlpha(0.4f);
        btnSend.setEnabled(false);

        editMessage.addTextChangedListener(new android.text.TextWatcher() {

            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}

            @Override
            public void afterTextChanged(android.text.Editable e) {

                boolean hasText = e != null && e.toString().trim().length() > 0;

                btnSend.setEnabled(hasText);
                btnSend.setAlpha(hasText ? 1f : 0.4f);
            }
        });

        listenForMessages();

        return v;
    }

    /**
     * Opens the phone dialer with the number pre-filled. Deliberately
     * ACTION_DIAL rather than ACTION_CALL - the user taps the green button
     * themselves, so the app never needs the CALL_PHONE permission and can
     * never place a call without an explicit action.
     */
    private void dialPhone() {

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(getContext(), "No phone number on file", Toast.LENGTH_SHORT).show();
            return;
        }

        try {

            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_DIAL,
                    android.net.Uri.parse("tel:" + phone.trim()));

            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(getContext(), "Couldn't open dialer", Toast.LENGTH_SHORT).show();
        }
    }

    private void listenForMessages() {

        if (orderId == null || chatType == null) return;

        messagesRegistration = db.collection("Orders").document(orderId)
                .collection("chats_" + chatType)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {

                    if (!isAdded() || e != null || snap == null) return;

                    messages.clear();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {

                        ChatMessage m = new ChatMessage();
                        m.senderId = doc.getString("senderId");
                        m.senderName = doc.getString("senderName");
                        m.text = doc.getString("text");

                        Long ts = doc.getLong("timestamp");
                        m.timestamp = ts != null ? ts : 0L;

                        messages.add(m);
                    }

                    adapter.notifyDataSetChanged();

                    if (!messages.isEmpty()) {
                        recycler.scrollToPosition(messages.size() - 1);
                    }
                });
    }

    private void sendMessage() {

        String text = editMessage.getText() != null
                ? editMessage.getText().toString().trim() : "";

        if (TextUtils.isEmpty(text) || orderId == null || myUid == null) return;

        editMessage.setText("");

        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId", myUid);
        msg.put("senderName", myDisplayName());
        msg.put("text", text);
        msg.put("timestamp", System.currentTimeMillis());

        db.collection("Orders").document(orderId)
                .collection("chats_" + chatType)
                .add(msg)
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    // Put the text back so a failed send doesn't silently
                    // lose what they typed.
                    editMessage.setText(text);

                    Toast.makeText(getContext(),
                            "Couldn't send: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * The label the OTHER side sees on each message.
     *
     * ✅ FIX: this used to read a "myLabel" argument that newInstance()
     * never actually set - so every message from anyone was labelled
     * "PakTrain Rider", including the restaurant's and passenger's own.
     * Now it's derived from the signed-in user's real role.
     *
     * Riders are deliberately shown as "PakTrain Rider" rather than their
     * personal name, matching how delivery apps present couriers.
     */
    private String myDisplayName() {

        String role = new com.example.paktrainfoodapp.utils.PrefManager(requireContext())
                .getUserRole();

        // Branded labels, not personal names - the same convention delivery
        // apps use. Whoever you're talking to sees the ROLE they're dealing
        // with, which is what actually matters mid-order.
        if ("DELIVERY".equalsIgnoreCase(role)) return "PakTrain Rider";
        if ("RESTAURANT".equalsIgnoreCase(role)) return "PakTrain Restaurant";

        return "Passenger";
    }

    /**
     * Header title: shows the counterparty's ROLE, so a restaurant sees
     * "PakTrain Rider" at the top and a rider sees "PakTrain Restaurant" -
     * rather than both sides seeing the restaurant's business name.
     */
    private String headerTitleFor(String role, String chatType, String fallback) {

        if ("DELIVERY".equalsIgnoreCase(role)) {
            return TYPE_RESTAURANT.equals(chatType) ? "PakTrain Restaurant" : "Passenger";
        }

        if ("RESTAURANT".equalsIgnoreCase(role)) return "PakTrain Rider";

        // Passenger's side always talks to the rider.
        return "PakTrain Rider";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesRegistration != null) messagesRegistration.remove();
    }

    static class ChatMessage {
        String senderId, senderName, text;
        long timestamp;
    }

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

        private static final int TYPE_MINE = 1;
        private static final int TYPE_THEIRS = 2;

        private final List<ChatMessage> items;
        private final String myUid;

        ChatAdapter(List<ChatMessage> items, String myUid) {
            this.items = items;
            this.myUid = myUid;
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage m = items.get(position);
            return m.senderId != null && m.senderId.equals(myUid) ? TYPE_MINE : TYPE_THEIRS;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            int layout = viewType == TYPE_MINE
                    ? R.layout.item_chat_mine
                    : R.layout.item_chat_theirs;

            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {

            ChatMessage m = items.get(position);

            h.txtMessage.setText(m.text);

            h.txtTime.setText(new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    .format(new java.util.Date(m.timestamp)));

            if (h.txtSender != null) {
                h.txtSender.setText(m.senderName != null ? m.senderName : "");
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {

            TextView txtMessage, txtTime, txtSender;

            VH(@NonNull View itemView) {
                super(itemView);
                txtMessage = itemView.findViewById(R.id.txtChatMessage);
                txtTime = itemView.findViewById(R.id.txtChatTime);
                txtSender = itemView.findViewById(R.id.txtChatSender);
            }
        }
    }
}
