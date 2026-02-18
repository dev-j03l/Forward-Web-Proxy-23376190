import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ClientHandler implements Runnable {
    // clientSocket number
    private final Socket clientSocket;

    // Constructor
    public ClientHandler(Socket clientSocket){
        this.clientSocket = clientSocket;
    }

    // As we implement runnable we need to override the default interface method.
    @Override
    public void run(){
        String threadName = Thread.currentThread().getName();
        
        try (
            Socket socket = this.clientSocket;
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.ISO_8859_1))
        ) {

            // For first line of header: (e.g "GET http://example.com/ HTTP/1.1")
            String requestLine = reader.readLine();

            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 3){
                System.out.println("Invalid Request Line");
                return;
            }

            String method = parts[0];
            String target = parts[1];
            String httpVersion = parts[2];

            String line;
            HashMap<String, String> headers = new HashMap<>();

            while((line = reader.readLine()) != null) {
                if(line.isEmpty()) break;

                int colonIndex = line.indexOf(":");
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    headers.put(key.toLowerCase(), value);
                } 
            }


            // Extracting the host and port from the requestLine target.
            // There are two cases. 
            // 1) HTTP - GET we are provided HOST, PORT (default = 80) and Path
            // 2) HTTPS - CONNECT example.com:443 only HOST and PORT
            String host = "";
            int port = -1;
            String path = "";

            // CONNECT: target is usually "host:port"
            if(method.equalsIgnoreCase("CONNECT")) {
                String[] hp = target.split(":", 2);
                host = hp[0];
                port = (hp.length == 2) ? Integer.parseInt(hp[1]) : 443;
                path = ""; // Not used for CONNECT requests
            } else {
                if(target.startsWith("http://")) {
                    String url = target.substring(7); // We strip the http:// from the target

                    int slashIndex = url.indexOf("/");
                    String hostPart = url.substring(0, slashIndex);
                    path = (slashIndex != -1) ? url.substring(slashIndex) : "/"; 

                    String[] hp = hostPart.split(":", 2);
                    host = hp[0];
                    port = (hp.length == 2) ? Integer.parseInt(hp[1]) : 80;
                } else if (target.startsWith("/")) {
                    // origin-form: host comes from host header
                    path = target;
                    String hostHeader = headers.get("host");
                    if(hostHeader == null){
                        System.out.println("No Host Header Present; cannot route request");
                        return;
                    }

                    String[] hp = hostHeader.split(":", 2);
                    host = hp[0];
                    port = (hp.length == 2) ? Integer.parseInt(hp[1]) : 80;
                } else {
                    // Some unrecognizable form
                    System.out.println("Unrecognized target form: " + target);
                    return;
                }
            }

            System.out.println("ROUTE => " + method + " " + host + ":" + port + " " + path);

            System.out.println("["+ threadName +"] --- Incoming Request ---");

            String body = "Proxy is running. \n (Forwarding not implemented yet.)\n";
            byte[] bodyBytes = body.getBytes(StandardCharsets.ISO_8859_1);

            String response = 
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    "Content-Length: " + bodyBytes.length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
            
            rawOut.write(response.getBytes(StandardCharsets.ISO_8859_1));
            rawOut.write(bodyBytes);
            rawOut.flush();
        } catch (IOException e){
            System.err.println("["+ threadName + "] ClientHandler Error: "+ e.getMessage());
        }
    }
}
