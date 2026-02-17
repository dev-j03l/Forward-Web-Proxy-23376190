import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket){
        this.clientSocket = clientSocket;
    }

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
            
            System.out.println("Raw Request Line: "+ requestLine);

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
                    headers.put(key, value);
                } 
            }

            System.out.println("Method: " + method);
            System.out.println("Target: "+ target);
            System.out.println("HTTP Version: "+ httpVersion);
            String hostHeader = headers.get("Host");
            System.out.println("Host Header: " + hostHeader);

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
