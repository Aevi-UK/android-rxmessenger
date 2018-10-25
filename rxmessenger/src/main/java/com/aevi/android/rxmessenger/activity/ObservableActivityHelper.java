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

import android.annotation.SuppressLint;
import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aevi.android.rxmessenger.MessageException;
import com.aevi.android.rxmessenger.service.AbstractChannelService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.annotations.NonNull;
import io.reactivex.subjects.PublishSubject;

/**
 * Helper class that allows for a request/response style communication between some class and an Android Activity.
 *
 * A class that wants to start an activity to interact with a user and generate some form of response, can create a new instance with {@link #createInstance(Context, Intent)} followed by a call to {@link #startObservableActivity()} in order to
 * retrieve an Observable to subscribe to.
 *
 * The activity that is started can then use {@link #getInstance(Intent)} with the Intent passed to the Activity to get hold of the instance, and call {@link #publishResponse(Object)} to pass back a response.
 *
 * The activity should also call {@link #registerForEvents(Lifecycle)} to allow the service or client to send relevant events to it.
 */
public class ObservableActivityHelper<T> {

    private static final String TAG = ObservableActivityHelper.class.getSimpleName();
    public static final String INTENT_ID = "ObservableActivityHelper.ID";

    private static final Map<String, ObservableActivityHelper> INSTANCES_MAP = new HashMap<>();

    private final String id;
    private final Context context;
    private final Intent intent;

    private ObservableEmitter<T> emitter;
    private ActivityStateMonitor activityStateMonitor;
    private PublishSubject<Lifecycle.Event> lifecycleEventSubject;

    private ObservableActivityHelper(String id, Context context, Intent intent) {
        this.id = id;
        this.context = context;
        this.intent = intent;
        lifecycleEventSubject = PublishSubject.create();
    }

    /**
     * Create a new instance in order to start an Activity.
     *
     * If an instance with the same clientId already exists, a new one will be created and overwrite the old one.
     *
     * Note - this should generally *only* be called from a service. From the activity, {@link #getInstance(String)} should be used.
     *
     * @param context The Android context
     * @param intent  The intent to start the activity
     * @return An instance of {@link ObservableActivityHelper}
     */
    @NonNull
    public static <T> ObservableActivityHelper<T> createInstance(Context context, Intent intent) {
        String uuid = intent.hasExtra(INTENT_ID) ? intent.getStringExtra(INTENT_ID) : UUID.randomUUID().toString();
        intent.putExtra(INTENT_ID, uuid);

        if (INSTANCES_MAP.containsKey(uuid)) {
            Log.w(ObservableActivityHelper.class.getSimpleName(), "An instance with id: " + uuid + " already exists.");
        }

        ObservableActivityHelper<T> instance = new ObservableActivityHelper<>(uuid, context, intent);
        Log.d(TAG, "Created new instance with id: " + uuid);
        INSTANCES_MAP.put(uuid, instance);
        return instance;
    }

    /**
     * Get an already existing {@link ObservableActivityHelper} instance.
     *
     * @param intent The intent passed in to the Activity
     * @return An instance, or null if not available
     * @throws NoSuchInstanceException Thrown when this helper was not used to launch activity, or the service has been shutdown since
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> ObservableActivityHelper<T> getInstance(Intent intent) throws NoSuchInstanceException {
        String id = intent.getStringExtra(INTENT_ID);
        if (id == null) {
            Log.e(TAG, "No id set in intent");
            throw new NoSuchInstanceException();
        }
        return getInstance(id);
    }

    /**
     * Get an already existing {@link ObservableActivityHelper} instance.
     *
     * @param id The id used when creating the instance
     * @return An instance, or null if not available
     * @throws NoSuchInstanceException Thrown when this helper was not used to launch activity, or the service has been shutdown since
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> ObservableActivityHelper<T> getInstance(String id) throws NoSuchInstanceException {
        ObservableActivityHelper<T> helper = INSTANCES_MAP.get(id);
        if (helper == null) {
            Log.e(TAG, "Tried to retrieve client with id: " + id + ", could not be found");
            throw new NoSuchInstanceException();
        }
        return helper;
    }

    /**
     * Register your activity (or possibly fragment) for events from the messenger service.
     *
     * NOTE! If you use this - ensure that you include ""android.arch.lifecycle:runtime" as a dependency. In order to avoid conflicts with
     * the support library in the destination apps, this dependency is compile time only (aka provided) in this project.
     * See https://developer.android.com/topic/libraries/architecture/adding-components.html for details
     *
     * These events may come from the remote client of the {@link AbstractChannelService}, or locally
     * in reaction to the lifecycle events of your activity or fragment.
     *
     * It is up to the system/framework that makes use of this library to define what events might be sent.
     *
     * Make sure you dispose of any subscription to this if your activity/fragment is destroyed.
     *
     * @param lifecycle The Lifecycle of your (support) activity/fragment (via getLifecycle())
     * @return A stream of events that your activity/fragment needs to handle appropriately
     */
    @SuppressLint("RestrictedApi")
    @NonNull
    public Observable<String> registerForEvents(Lifecycle lifecycle) {
        Log.d(TAG, "Activity registering for events in helper with id: " + id);
        activityStateMonitor = new ActivityStateMonitor(lifecycle, this);
        return activityStateMonitor.getEventObservable();
    }

    /*
     * For internal use.
     */
    void sendLifecycleEvent(Lifecycle.Event event) {
        lifecycleEventSubject.onNext(event);
        if (event == Lifecycle.Event.ON_DESTROY) {
            Log.d(TAG, "Activity destroyed - shutting down helper with id: " + id);
            lifecycleEventSubject.onComplete();
        }
    }

    /**
     * This can be called by the {@link AbstractChannelService} subclass to listen to activity lifecycle
     * events, provided that the activity/fragment called {@link #registerForEvents(Lifecycle)}.
     *
     * @return A stream of lifecycle events
     */
    @NonNull
    public Observable<Lifecycle.Event> onLifecycleEvent() {
        return lifecycleEventSubject;
    }

    /**
     * Send an event to the activity.
     *
     * Note that this is only successful if the activity has called {@link #registerForEvents(Lifecycle)} previously.
     *
     * @param event The event to send to the activity
     */
    public void sendEventToActivity(String event) {
        if (activityStateMonitor != null) {
            activityStateMonitor.sendEvent(event);
        }
    }

    /**
     * Publish a response back to the calling class.
     *
     * @param response The response
     */
    public void publishResponse(T response) {
        Log.d(TAG, "publishResponse");
        emitter.onNext(response);
        emitter.onComplete();
        removeFromMap();
    }

    /**
     * Return an error back to the calling class.
     *
     * @param me The exception
     */
    public void returnError(MessageException me) {
        emitter.onError(me);
    }

    /**
     * Starts the activity and returns an Observable to subscribe to.
     *
     * @return The Observable that the activity will publish responses to
     */
    @NonNull
    public Observable<T> startObservableActivity() {
        return Observable.create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<T> emitter) throws Exception {
                Log.d(TAG, "Starting activity: " + intent.toString() + ", clientId: " + id);
                ObservableActivityHelper.this.emitter = emitter;
                context.startActivity(intent);
            }
        });
    }

    /**
     * Remove the instance from the map once it's finished.
     */
    void removeFromMap() {
        INSTANCES_MAP.remove(id);
    }

}
