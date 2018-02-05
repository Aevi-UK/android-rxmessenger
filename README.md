# Rx Messenger Service

A utility library that can be used on Android to create a messenger service/client application. The library
can be used to send message to an Android Service and receive responses as a reactive stream of Observables.

These classes provide an implementation of the messenger service making use of reactive extensions (RxJava/RxAndroid).
All data sent between client and server are simple strings, implementations making use of this library are free
to choose whatever serialisation mechanism they want to serialise the data (we recommend JSON).

## Adding rxmessenger dependency

The rxmessenger library is published to bintray. Add the below to your top-level build.gradle repositories DSL,
```
maven {
            url "http://dl.bintray.com/aevi/aevi-uk"
        }
```

Then, in your application build.gradle,

```
implementation "com.aevi.android:rxmessenger:<version>"
```

## Sample application

Please see the `SampleApp` module for some examples of how to use the various features of this library.

Note that in this case the client and "server" is in the same application for simplicity, but in most real world scenarios these
would live in separate applications.

## Setting up the server

```java
public class DemoMessengerService extends AbstractMessengerService {

    @Override
    protected void handleRequest(String clientId, String requestData, String packageName) {
        // It is important to send messages back to the correct clientId
        // so make sure you store/keep this id somewhere

        // Setup response message here and send reply to client
        ResponseObject response = new ResponseObject();

        // Send a message to client (can be multiple)
        sendMessageToClient(clientId, response.toJson());

        // Let the client know and end the stream here
        sendEndStreamMessageToClient(clientId);
    }
}
```

Once you have defined your service class it must also be registered in the AndroidManifest.xml of your
application. e.g.

```xml
    <service android:name=".DemoMessengerService"
             android:exported="true">

    </service>
```

### Starting an activity to interact with user

In many cases, interaction with a user is necessary to handle the client request.

In order to make this as simple as possible, the library provides `ObservableActivityHelper` which allows you to launch an activity
and then subscribe to responses.

Creating the helper
```
Intent intent = new Intent(this, SampleActivity.class);
ObservableActivityHelper<String> activityHelper = ObservableActivityHelper.createInstance(this, intent);
```

Subscribing for responses
```
activityHelper.startObservableActivity().subscribe(responseFromActivity -> {
        Log.d(TAG, "Response from activity: " + responseFromActivity);
        SampleMessage sampleMessage = new SampleMessage(MessageTypes.RESPONSE, responseFromActivity);
        sendMessageToClient(clientId, gson.toJson(sampleMessage));
    });
```

This helper also provides support for the service to listen to lifecycle events from the activity it has started.
This is typically useful to react accordingly to the activity being stopped or destroyed before a response has been sent to the client.

```
// Registering to this allows the service to react to lifecycle events from the activity it has started
activityHelper.onLifecycleEvent().subscribe(event -> {
        // All we do here is log it - but in a real implementation you may want to react to onStop, onDestroy, etc
        Log.d(TAG, "Received activity lifecycle event: " + event);
    });
```

In addition, the helper allows the service to send messages to the activity.
The lifecycle events and sending messages is only possible if the activity also registers itself for this.

```
try {
    ObservableActivityHelper<String> activityHelper = ObservableActivityHelper.getInstance(getIntent());
    activityHelper.registerForEvents(getLifecycle()).subscribe(eventFromService -> {
        Log.d(TAG, "Received event from service: " + eventFromService);
    });
} catch (NoSuchInstanceException e) {
    // This can happen if the activity wasn't started via the ObservableActivityHelper, or the service has shut down / crashed
    Log.e(TAG, "No activity helper available - finishing");
    finish();
}
```

The `ObservableActivityHelper` will automatically finish up when the activity
- Sends a response
- Is destroyed (only if the activity has registered its lifecycle)
- An error occurs

After that point, any call to `ObservableActivityHelper.getInstance()` will throw `NoSuchInstanceException`.

**NOTE**

In order to use the lifecycle events and messaging described above, the client app must add dependencies on the following components,
- android.arch.lifecycle:runtime
- android.arch.lifecycle:common-java8

The library defines these as compile time dependencies only to avoid forcing these transitively upon a client.
They are only required if the above events and messaging is used.

See the sample app for further details.

## Setting up the client

The client application should use the `ObservableMessengerClient` class to send a message to the
service.

The client is constructed with the component name of the service it should connect to. On the first call to `sendMessage()`, the client will bind to the service.
You can also connect explicitly via a call to `connect()` and check connection status via `isConnected()`.
The connection is then kept active until `closeConnection()` is called, or the remote service sends an end of stream command.

The `clientId` passed to the service `handleRequest` method is guaranteed to remain the same for as long as the connection is open. If closed and a new connection is opened, a new id will be generated.

Optionally, you can use a different constructor than below by passing in a `OnHandleMessageCallback` instance, allowing you to deal with the response before passing it on to the subscriber.

```java

    private ObservableMessengerClient client;

    protected void setup() {
        ComponentName serviceComponentName = new ComponentName("com.server.package", "com.server.package.DemoMessengerService");
        client = new ObservableMessengerClient(this, serviceComponentName);
    }

    public void sendMessage() {
        RequestObject request = new RequestObject();

        client.sendMessage(request.toJson())
                .subscribe(new Consumer<String>() {
            @Override
            public void accept(@NonNull String response) throws Exception {
                // probably convert back from JSON here depending on what
                // the service at the other end sends
                Log.d(TAG, "Got message from server: " + response);
            }
        });
    }

    public void finish() {
        client.closeConnection();
    }

```
