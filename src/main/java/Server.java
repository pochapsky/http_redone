import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final String NotFoundText = "Not Found";
    private final String NotFoundCode = "404";
    private final int PoolThreads = 64;
    private final int PORT;
    List<String> validPathsList = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private ExecutorService threadPool;
    private ConcurrentHashMap<String, Map<String, Handler>> handlersStorageMap;

    public Server(int port) {
        PORT = port;
        threadPool = Executors.newFixedThreadPool(PoolThreads);
        handlersStorageMap = new ConcurrentHashMap<>();
    }
    public void start() {
        try (final var serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server strted on port: " + PORT);
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connection on: " + clientSocket.getInetAddress().getHostAddress());
                threadPool.execute(() -> handleConnection(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }
    private void handleConnection(Socket clientSocket) {
        try (final var in = new BufferedInputStream(clientSocket.getInputStream());
             final var out = new BufferedOutputStream(clientSocket.getOutputStream())
        ) {
            Request request = Request.createRequest(in);
            if (request == null || !handlersStorageMap.containsKey(request.getMethod())) {
                outContentResponse(out, NotFoundCode, "Error Request");
                return;
            }
            Map<String, Handler> handlerMap = handlersStorageMap.get(request.getMethod());
            String requestPath = request.getPath().split("\\?")[0];
            if (handlerMap.containsKey(requestPath)) {
                Handler handler = handlerMap.get(requestPath);
                handler.handle(request, out);
            } else {
                if (!validPathsList.contains(requestPath)) {
                    outContentResponse(out, NotFoundCode, NotFoundText);
                } else {
                    defaultHandler(out, requestPath);
                }
            }

        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    void addHandler(String method, String path, Handler handler) {
        if (!handlersStorageMap.containsKey(method)) {
            handlersStorageMap.put(method, new HashMap<>());
        }
        handlersStorageMap.get(method).put(path, handler);
    }

    public void outContentResponse(BufferedOutputStream out, String code, String status) throws IOException {
        out.write((
                "HTTP/1.1 " + code + " " + status + "\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    void defaultHandler(BufferedOutputStream out, String path) throws IOException {
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);
        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
            return;
        }
        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }
}