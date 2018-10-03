package com.aevi.android.rxmessenger.activity;


import android.app.Activity;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.support.annotation.NonNull;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

/**
 * Helper class to monitor life cycle events of an activity or fragment.
 */
public class ActivityStateMonitor implements DefaultLifecycleObserver {

    private final ObservableActivityHelper<?> observableActivityHelper;
    private PublishSubject<String> eventSubject = PublishSubject.create();

    public ActivityStateMonitor(Lifecycle lifecycle, ObservableActivityHelper observableActivityHelper) {
        this.observableActivityHelper = observableActivityHelper;
        lifecycle.addObserver(this);
    }

    public Observable<String> getEventObservable() {
        return eventSubject;
    }

    public void sendEvent(String event) {
        eventSubject.onNext(event);
    }

    private void onActivityStateChange(LifecycleOwner source, Lifecycle.Event event) {
        observableActivityHelper.sendLifecycleEvent(event);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        onActivityStateChange(owner, Lifecycle.Event.ON_CREATE);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        onActivityStateChange(owner, Lifecycle.Event.ON_START);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        onActivityStateChange(owner, Lifecycle.Event.ON_RESUME);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        onActivityStateChange(owner, Lifecycle.Event.ON_PAUSE);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        onActivityStateChange(owner, Lifecycle.Event.ON_STOP);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        onActivityStateChange(owner, Lifecycle.Event.ON_DESTROY);
        owner.getLifecycle().removeObserver(this);
        boolean confChange = false;
        if (owner instanceof Activity) {
            Activity activity = (Activity) owner;
            confChange = activity.isChangingConfigurations();
        }
        if (!confChange) {
            observableActivityHelper.removeFromMap();
            eventSubject.onComplete();
        }
    }
}
