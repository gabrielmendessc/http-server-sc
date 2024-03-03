package gabrielmendessc.me.http.server.sc;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {

        NIOHttpServer NIOHttpServer = new NIOHttpServer(8080);
        NIOHttpServer.start();

    }

}
