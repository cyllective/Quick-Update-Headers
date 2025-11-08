# Quick Update Headers Burp Extension

This Burp extension lets you quickly update the headers of a request inside a Repeater or Intruder tab to newer headers from the same host using the context menu. 

The main scenario for this is replacing the cookies inside an old Repeater tab with the latest cookies. The replacement headers will be selected based on the newest request with the same "Host" header value as the selected request. Headers that should be available in the context menu for replacement can be specified in the Burp settings under "Settings/Extensions/Quick Update Headers".

## Build

```sh
make
# or 
docker run --rm -u $(id -u):$(id -g) -v $(pwd):/home/gradle gradle:8.7.0-jdk17-alpine gradle build
mv ./build/libs/*.jar .
```

## Bambda

```java
String[] headersToReplace = {"Authorization","Cookie"};

HttpRequest selectedRequest= requestResponse.request();
List<HttpHeader> currentHeaders = selectedRequest.headers();
if (currentHeaders == null || currentHeaders.isEmpty()) {
    return;
}

String hostValue = selectedRequest.headerValue("Host");
if (hostValue == null) {
    return;
}

// It is faster (in big projects) to go through the whole history
// rather than to filter the history first by host header
List<ProxyHttpRequestResponse> history = api.proxy().history();
if (history.isEmpty()) {
    return;
}

ListIterator<ProxyHttpRequestResponse> historyIterator =
        history.listIterator(history.size());

HttpRequest newRequest = selectedRequest;

while (historyIterator.hasPrevious()) {
    // Go through the list in reverse as we want the newest entries first
    ProxyHttpRequestResponse historyEntry = historyIterator.previous();

    HttpHeader hostHeader = historyEntry.request().header("Host");
    if (hostHeader == null) {
        continue;
    }

    if (hostHeader.value().equals(hostValue)) {
        // We are on the same host
        for (String headerToReplace: headersToReplace) {
	        HttpHeader headerToInsert = historyEntry.request().header(headerToReplace);
        	if (headerToInsert != null) {
	            // Update the request with the newest header
                newRequest = newRequest.withHeader(headerToInsert);
	        }
	     }
        
        break; // Only update with the first (most recent) match
    }
}

// Apply new request
httpEditor.requestPane().set(newRequest);
```