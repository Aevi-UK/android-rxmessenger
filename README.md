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

The client is constructed with the component name of the service it should connect to. On the first call to `sendMessage()`, the client will bind to the service.
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