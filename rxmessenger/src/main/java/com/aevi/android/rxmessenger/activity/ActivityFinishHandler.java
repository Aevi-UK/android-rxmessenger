/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aevi.android.rxmessenger.activity;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.util.Log;

import java.lang.ref.WeakReference;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

/**
 * Helper class to encapsulate the logic required to finish an activity upon request and manage the lifecycle accordingly to avoid memory leaks
 * or old {@link ObservableActivityHelper} instances around.
 */
public class ActivityFinishHandler implements LifecycleObserver {

    private static final String TAG = ActivityFinishHandler.class.getSimpleName();

    private final ObservableActivityHelper<?> observableActivityHelper;
    private final WeakReference<Activity> activityRef;
    private final String activityClassName;
    private Disposable disposable;

    public ActivityFinishHandler(Activity activity, ObservableActivityHelper observableActivityHelper) {
        this.activityRef = new WeakReference<>(activity);
        this.observableActivityHelper = observableActivityHelper;
        this.activityClassName = activity.getClass().getName();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    void onActivityCreated() {
        Log.d(TAG, "Activity created (" + activityClassName + ")");
        disposable = observableActivityHelper.onFinishActivity().subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean b) throws Exception {
                if (activityRef.get() != null) {
                    Activity activity = activityRef.get();
                    if (!activity.isDestroyed() && !activity.isFinishing()) {
                        Log.d(TAG, "Finishing activity (" + activityClassName + ")");
                        activity.finish();
                    }
                    activityRef.clear();
                }
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    void onActivityDestroyed() {
        Log.d(TAG, "Activity destroyed (" + activityClassName + ")");
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        if (activityRef != null) {
            activityRef.clear();
        }
        observableActivityHelper.removeFromMap();
    }
}
