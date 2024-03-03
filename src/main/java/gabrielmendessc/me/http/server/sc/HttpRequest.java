package gabrielmendessc.me.http.server.sc;

public class HttpRequest {

    private String method;
    private String path;
    private String version;


    public HttpRequest(String method, String path, String version) {
        this.method = method;
        this.path = path;
        this.version = version;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

}
