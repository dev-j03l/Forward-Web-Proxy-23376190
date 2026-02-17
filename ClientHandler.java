import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

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
            StringBuilder request = new StringBuilder();
            String line;

            // For first line of header: (e.g "GET http://example.com/ HTTP/1.1")
            String requestLine = line = reader.readLine();

            if (requestLine == null || requestLine.isEmpty()) return;
            
            System.out.println("Raw Request Line: "+ line);

            String[] parts = requestLine.split(" ");
            if (parts.length < 3){
                System.out.println("Invalid Request Line");
                return;
            }

            while((line = reader.readLine()) != null) {
                request.append(line).append("\r\n");
                if(line.isEmpty()) break;
            }

            System.out.println("["+ threadName +"] --- Incoming Request ---");
            System.out.println(request);

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
