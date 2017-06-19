# Rx Messenger Service

A utility library that can be used on Android to create a messenger service/client application. The library
can be used to send message to an Android Service and receive responses as a reactive stream of Observables.

These classes provide an implementation of the messenger service making use of reactive extensions (RxJava/RxAndroid).
All classes sent between client and server are serialised as JSON strings.

## Setting up the server

```java
public class DemoMessengerService extends AbstractMessengerService<RequestObject, ResponseObject> {

    public DemoMessengerService() {
        super(RequestObject.class);
    }

    @Override
    protected void handleRequest(RequestObject request) {

        // Setup response message here and send reply to client
        ResponseObject response = new ResponseObject();

        // Send a message to client (can be multiple)
        sendMessageToClient(request.getId(), response);

        // Let the client know and end the stream here
        sendEndStreamMessageToClient(request.getId());
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

    private ObservableMessengerClient<RequestObject, ResponseObject> client;

    protected void setup() {
        client = new ObservableMessengerClient<>(this, getServerIntent(), ResponseObject.class);
    }

    private Intent getServerIntent() {
        Intent intent = new Intent();
        intent.setClassName("com.server.package", "com.server.package.DemoMessengerService");
        return intent;
    }

    public void sendMessage() {
        RequestObject request = new RequestObject();

        client.sendMessage(request)
                .subscribe(new Consumer<ResponseObject>() {
            @Override
            public void accept(@NonNull ResponseObject response) throws Exception {
                Log.d(TAG, "Got message from server: " + response.getServerMessage());
            }
        });
    }

```