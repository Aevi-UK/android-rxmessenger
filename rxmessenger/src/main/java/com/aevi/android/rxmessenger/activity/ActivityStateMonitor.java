package com.aevi.android.rxmessenger.activity;


import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static androidx.lifecycle.Lifecycle.Event.*;


/**
 * Helper class to monitor life cycle events of an activity or fragment.
 */
public class ActivityStateMonitor implements DefaultLifecycleObserver {

    private final ObservableActivityHelper<?> observableActivityHelper;
    private final PublishSubject<Lifecycle.Event> lifecycleEventSubject;
    private Lifecycle lifecycle;

    public ActivityStateMonitor(ObservableActivityHelper observableActivityHelper) {
        this.observableActivityHelper = observableActivityHelper;
        lifecycleEventSubject = PublishSubject.create();
    }

    public void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
        lifecycle.addObserver(this);
    }

    public Observable<Lifecycle.Event> getLifecycleEvents() {
        return lifecycleEventSubject;
    }

    public Lifecycle.State getCurrentState() {
        return lifecycle != null ? lifecycle.getCurrentState() : null;
    }

    private void onActivityStateChange(Lifecycle.Event event) {
        sendLifecycleEvent(event);
    }

    private void sendLifecycleEvent(Lifecycle.Event event) {
        lifecycleEventSubject.onNext(event);
        if (event == ON_DESTROY) {
            lifecycleEventSubject.onComplete();
        }
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        onActivityStateChange(ON_CREATE);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        onActivityStateChange(ON_START);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        onActivityStateChange(ON_RESUME);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        onActivityStateChange(ON_PAUSE);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        onActivityStateChange(ON_STOP);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        onActivityStateChange(ON_DESTROY);
        owner.getLifecycle().removeObserver(this);
        boolean confChange = false;
        if (owner instanceof Activity) {
            Activity activity = (Activity) owner;
            confChange = activity.isChangingConfigurations();
        }
        if (!confChange) {
            observableActivityHelper.removeFromMap();
        }
    }
}
