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
package com.aevi.android.rxmessenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.SupportActivity;
import android.util.Log;

import java.util.Map;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.subjects.SingleSubject;

/**
 * Helper class that allows for a request/response style communication between some class and an Android Activity.
 *
 * A class that wants to start an activity to interact with a user and generate some form of response, can create a new instance with {@link #createInstance(Context, Intent)} followed by a call to {@link #startObservableActivity()} in order to
 * retrieve an Observable to subscrive to.
 *
 * The activity that is started can then use {@link #getInstance(Intent)} with the Intent passed to the Activity to get hold of the instance, and call {@link #publishResponse(Object)} to pass back a response.
 *
 * The activity should also call {@link #registerActivityForFinish(SupportActivity)} to allow the code that started it to finish the activity if a response is
 * no longer required (due to a timeout, cancellation, error, etc). The initial class can then call {@link #finishActivity()} to finish it.
 */
public class ObservableActivityHelper<T> {

    private static final String TAG = ObservableActivityHelper.class.getSimpleName();
    public static final String INTENT_ID = "ObservableActivityHelper.ID";

    private static Map<String, ObservableActivityHelper> INSTANCES_MAP = new java.util.HashMap<>();

    private ObservableEmitter<T> emitter;
    private SingleSubject<Boolean> finishSubject;
    private final String id;
    private final Context context;
    private final Intent intent;

    private ObservableActivityHelper(String id, Context context, Intent intent) {
        this.id = id;
        this.context = context;
        this.intent = intent;
        finishSubject = SingleSubject.create();
    }

    /**
     * Create a new instance in order to start an Activity.
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
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> ObservableActivityHelper<T> getInstance(Intent intent) {
        String id = intent.getStringExtra(INTENT_ID);
        if (id != null) {
            return INSTANCES_MAP.get(id);
        }
        return null;
    }

    /**
     * Get an already existing {@link ObservableActivityHelper} instance.
     *
     * @param id The id used when creating the instance
     * @return An instance, or null if not available
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> ObservableActivityHelper<T> getInstance(String id) {
        return INSTANCES_MAP.get(id);
    }

    /**
     * An activity (that subclasses a support library activity) can call this to register for finish requests from the class that started it.
     *
     * This uses the Lifecycle and LifecycleObserver classes from the Android Architecture Components to listen to activity events.
     *
     * Note that the activity passed in is stored as a weak reference and hence will not keep your activity alive after being destroyed.
     *
     * @param activity The support library based activity to finish on request
     */
    @SuppressLint("RestrictedApi")
    public void registerActivityForFinish(SupportActivity activity) {
        Log.d(TAG, "Registering activity for finish: " + activity.getClass().getName());
        ActivityFinishHandler activityFinishHandler = new ActivityFinishHandler(activity, this);
        activity.getLifecycle().addObserver(activityFinishHandler);
    }

    /**
     * If the mechanism used via {@link #registerActivityForFinish(SupportActivity)} is not suitable, an Activity can subscribe to this directly in order
     * to receive finish requests. Note that the activity itself must ensure that it disposes of the subscription properly, etc.
     *
     * Note that this should *NOT* be called if you are using the {@link #registerActivityForFinish(SupportActivity)} above.
     *
     * @return A Single to subscribe to
     */
    public Single<Boolean> onFinishActivity() {
        return finishSubject;
    }

    /**
     * This can be called to request an activity to finish itself, provided that the activity supports this from either calling {@link #registerActivityForFinish(SupportActivity)} or {@link #onFinishActivity()}.
     */
    public void finishActivity() {
        Log.d(TAG, "finishActivity");
        finishSubject.onSuccess(true);
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
    public Observable<T> startObservableActivity() {
        return Observable.create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<T> emitter) throws Exception {
                Log.d(ObservableActivityHelper.class.getSimpleName(), "Starting activity: " + intent.toString());
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
