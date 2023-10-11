import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Request {
    private final List<String> headersList;
    private final String method;
    private final String path;
    public final static String GET = "GET";
    public final static String POST = "POST";
    private List<NameValuePair> paramsList;
    public Request(String method, String path, List<String> headersList, List<NameValuePair> paramsList) {
        this.method = method;
        this.path = path;
        this.headersList = headersList;
        this.paramsList = paramsList;
    }
    public Request(String method, String path) {
        this.method = method;
        this.path = path;
        headersList = null;
    }
    public String getMethod() {
        return method;
    }
    public String getPath() {
        return path;
    }
    static Request createRequest(BufferedInputStream in) throws IOException, URISyntaxException {
        final List<String> methodsList = List.of(GET, POST);
        final var limit = 4096;
        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            return null;
        }
         final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            return null;
        }
        final var method = requestLine[0];
        if (!methodsList.contains(method)) {
            return null;
        }
        final var path = requestLine[1];
        if (!path.startsWith("/")) {
            return null;
        }
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            return null;
        }
        in.reset();
         in.skip(headersStart);
        final var headerBytes = in.readNBytes(headersEnd - headersStart);
        List<String> headers = Arrays.asList(new String(headerBytes).split("\r\n"));
        List<NameValuePair> params = URLEncodedUtils.parse(new URI(path), StandardCharsets.UTF_8);
        return new Request(method, path, headers, params);
    }
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public NameValuePair getQueryParam(String name) {
        return getQueryParams().stream()
                .filter(param -> param.getName().equalsIgnoreCase(name))
                .findFirst().orElse(new NameValuePair() {
                    @Override
                    public String getName() {
                        return name;
                    }
                    @Override
                    public String getValue() {
                        return "";
                    }
                });
    }
    public List<NameValuePair> getQueryParams() {
        return paramsList;
    }
    public List<String> getHeaders() {
        return headersList;
    }
}
