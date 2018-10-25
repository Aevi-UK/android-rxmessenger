package com.aevi.android.rxmessenger.sample;

import java.util.ArrayList;
import java.util.List;

public class ActivityFinishRequestListener {

    private static ActivityFinishRequestListener INSTANCE;

    public static ActivityFinishRequestListener getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ActivityFinishRequestListener();
        }
        return INSTANCE;
    }

    private final List<OnFinishListener> listeners;

    private ActivityFinishRequestListener() {
        listeners = new ArrayList<>();
    }

    public void addOnFinishListener(OnFinishListener listener) {
        listeners.add(listener);
    }

    public void removeOnFinishListener(OnFinishListener listener) {
        listeners.remove(listener);
    }

    public void finishActivities() {
        for (OnFinishListener onFinishListener : listeners) {
            onFinishListener.finish();
        }
        listeners.clear();
    }

    interface OnFinishListener {
        void finish();
    }
}
