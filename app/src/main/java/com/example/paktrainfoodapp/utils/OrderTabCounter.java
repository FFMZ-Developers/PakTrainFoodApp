package com.example.paktrainfoodapp.utils;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Live "how many orders are sitting in this tab" counts, shown next to the
 * tab label - e.g. "Active (3)".
 *
 * The count is a live Firestore listener, so it goes up when an order
 * arrives and back down the moment that order moves on to the next stage,
 * without the user having to reopen anything.
 *
 * Completed tabs deliberately get NO count: that list only ever grows, so
 * a number there would climb forever and mean nothing. Counts are for
 * work still waiting to be done.
 */
public class OrderTabCounter {

    private final List<ListenerRegistration> registrations = new ArrayList<>();

    /**
     * @param tabIndex   which tab to label
     * @param baseLabel  the tab's text without any count
     * @param query      the same query that tab's list uses
     */
    public void attach(TabLayout tabLayout, int tabIndex, String baseLabel, Query query) {

        ListenerRegistration reg = query.addSnapshotListener((snap, e) -> {

            TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);

            if (tab == null) return;

            if (e != null || snap == null || snap.isEmpty()) {
                tab.setText(baseLabel);
                return;
            }

            tab.setText(baseLabel + " (" + snap.size() + ")");
        });

        registrations.add(reg);
    }

    /** Convenience for the common "these statuses, for this owner" shape. */
    public void attachStatuses(TabLayout tabLayout, int tabIndex, String baseLabel,
                               String ownerField, String ownerId, String... statuses) {

        if (ownerId == null || statuses.length == 0) return;

        Query q = FirebaseFirestore.getInstance().collection("Orders")
                .whereEqualTo(ownerField, ownerId)
                .whereIn("orderStatus", Arrays.asList(statuses));

        attach(tabLayout, tabIndex, baseLabel, q);
    }

    /** Must be called from the host fragment's onDestroyView. */
    public void detachAll() {
        for (ListenerRegistration r : registrations) {
            if (r != null) r.remove();
        }
        registrations.clear();
    }
}
