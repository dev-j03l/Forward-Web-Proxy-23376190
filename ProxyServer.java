import java.net.*;
import java.io.*;

public class ProxyServer {

    private final int port; // Our server should be accessable at the port/should stay constant.

    public ProxyServer(int port){
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ProxyServer listening on Port: " + port + "\n");

            while(true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from: " + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                Thread t = new Thread(handler);
                t.start();
            }
        }
    }

    public static void main(String[] args){
        int port = 8080;
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        }
        
        try{
            new ProxyServer(port).start();
        } catch (IOException e){
            System.err.println("Failed to start Proxy Server: "+  e.getMessage());
            e.printStackTrace();
        }
    }
}