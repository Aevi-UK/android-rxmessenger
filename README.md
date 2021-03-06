# Rx Messenger Service

A utility library that can be used on Android to create a messenger service/client application. The library
can be used to send message to an Android Service and receive responses as a reactive stream of Observables.
The library can use any communication channel capable of exchanging data as a string. Two implementations are
currently provided for communication via Android Messenger and via a Websocket.

Both of these implementations make use of reactive extensions (RxJava/RxAndroid).

All data sent between client and server are simple strings, implementations making use of this library are free
to choose whatever serialisation mechanism they want to serialise the data (we recommend JSON).

## Adding rxmessenger dependency

As of RxMessenger 5.0.3, artifacts are published to Github Packages instead of `jcenter`. This is due to JFrog [shutting down jcenter](https://jfrog.com/blog/into-the-sunset-bintray-jcenter-gocenter-and-chartcenter/).

Add the below entry to your `repositories` closure in the root `build.gradle` file. Note that unfortunately Github requires authentication for programmatic access of public packages (see [this thread](https://github.community/t/download-from-github-package-registry-without-authentication/14407/85) for details on this issue). This means that you must authenticate with a Github username and [generate a PAT (personal access token)](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) in order to access the dependencies as per below. If you or your organisation don't already have a Github account, you can sign up for one [here](https://github.com/join).

```
maven {
        name = "AEVI-UK"
        url = uri("https://maven.pkg.github.com/aevi-uk/*")
        credentials {
            username = <your Github username>
            password = <your Github personal access token with `read:packages` enabled>
        }
    }
```

Artifacts version 5.0.2 and earlier will still be available via `jcenter`.

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
public class DemoMessengerService extends AbstractChannelService implements ChannelServer.ClientListener {

   @Override
   protected void onNewClient(ChannelServer channelServer, String callingPackageName) {

        Toast.makeText(this, "New client connected", Toast.LENGTH_SHORT).show();

        channelServer.addClientListener(this);
        channelServer.subscribeToMessages().subscribe(message -> {
          // Do something with the incoming message(s) here
        });

        // Setup response message here and send reply to client
        ResponseObject response = new ResponseObject();

        // Send a message to client (can be multiple)
        channelServer.send(response.toJson());

        // Let the client know and end the stream here
        channelServer.sendEndStream();
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

> NOTE: In order to use the lifecycle events and messaging described above, the client app must add dependencies on the following components,
> - android.arch.lifecycle:runtime
> - android.arch.lifecycle:common-java8

The library defines these as compile time dependencies only to avoid forcing these transitively upon a client.
They are only required if the above events and messaging is used.

See the sample app for further details.

## Setting up the client

The client applications should use the `Channels` class to obtain a channel to communicate with the server and then send a message to it.

To obtain an instance usoing Android Messenger as the primary channel use:
```java
   ChannelClient messengerClient = Channels.messenger(this, SERVICE);
```

To obtain an instance using Websokets as the primary channel use:
```java
   ChannelClient messengerClient = Channels.webSocket(this, SERVICE);
```

> NOTE: Currently websocket communication is experimental and if you are using it in your application should be thoroughly tested before any release
into production.

> NOTE: If the websocket channel is chosen the initial communication will still be via Android Messenger. This is so that hostname and port
details can be shared between client and server in order to setup the initial websocket.

> NOTE: Currently websocket communication will take place over SSL the server certificate and private key is generated automatically on first use by the server application
and stored in the Android key store. Each application using rx-messenger over websockets will generate its own self signed certificate and 2048 bit private key. 
The common name (CN) of the certificate will be equal to the package name of the application. Client connections automatically verify that the certificate received has the 
correct package name. Note that websocket SSL is used only to ensure the data is encrypted in transit, full verification and identification of server application via a CA 
certficiate chain is not implemented at this time (the server certificate is self-signed). 

If using a websocket for communication your application(s) must make use of the android permissions shown below to allow networking access.

```xml
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
```


The client is constructed with the component name of the service it should connect to. On the first call to `sendMessage()`, the client will bind to the service.
You can also connect explicitly via a call to `connect()` and check connection status via `isConnected()`.
The connection is then kept active until `closeConnection()` is called, or the remote service sends an end of stream command.


```java

    private ChannelClient client;

    protected void setup() {
        ComponentName serviceComponentName = new ComponentName("com.server.package", "com.server.package.DemoMessengerService");
        client = Channels.messenger(this, serviceComponentName);
    }

    public void sendMessage() {
        RequestObject request = new RequestObject();

        client.send(request.toJson())
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
