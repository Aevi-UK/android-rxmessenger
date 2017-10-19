# Rx Messenger Service

A utility library that can be used on Android to create a messenger service/client application. The library
can be used to send message to an Android Service and receive responses as a reactive stream of Observables.

These classes provide an implementation of the messenger service making use of reactive extensions (RxJava/RxAndroid).
All data sent between client and server are simple strings, implementations making use of this library are free
to choose whatever serialisation mechanism they want to serialise the data (we recommend JSON).

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
    <service android:name=".DemoMessengerService">

    </service>
```


## Setting up the client

The client application should use the `ObservableMessengerClient` class to send a message to the
service.

```java

    private ObservableMessengerClient client;

    protected void setup() {
        client = new ObservableMessengerClient(this);
    }

    private Intent getServerIntent() {
        Intent intent = new Intent();
        intent.setClassName("com.server.package", "com.server.package.DemoMessengerService");
        return intent;
    }

    public void sendMessage() {
        RequestObject request = new RequestObject();

        client.createObservableForServiceIntent(getServerIntent(), request.toJson())
                .subscribe(new Consumer<String>() {
            @Override
            public void accept(@NonNull String response) throws Exception {
                // probably convert back from JSON here depending on what
                // the service at the other end sends
                Log.d(TAG, "Got message from server: " + response);
            }
        });
    }

```