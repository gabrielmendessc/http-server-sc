package gabrielmendessc.me.http.server.sc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NIOHttpServer {

    private static final Logger logger = Logger.getLogger(NIOHttpServer.class.getName());

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;

    private int port;

    public NIOHttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {

        selector = Selector.open();

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        new Thread(this::run).start();

    }

    private void run() {

        logger.log(Level.INFO, "Starting nio-http server at port: {0}", port);

        while (true) {

            try {

                listenSocket();

            } catch (Exception e) {

                logger.log(Level.SEVERE, e.toString(), e);

            }

        }

    }

    private void listenSocket() throws IOException {

        selector.select();

        Iterator<SelectionKey> selectionKeyIterator = selector.selectedKeys().iterator();
        while (selectionKeyIterator.hasNext()) {

            SelectionKey selectionKey = selectionKeyIterator.next();
            selectionKeyIterator.remove();

            try {

                handleSocket(selectionKey);

            } catch (IOException e) {

                logger.log(Level.SEVERE, "Error on handling socket", e);

                selectionKey.cancel();
                selectionKey.channel().close();

            }

        }

    }

    private void handleSocket(SelectionKey selectionKey) throws IOException {

        if (!selectionKey.isValid()) {

            return;

        }

        if (selectionKey.isAcceptable()) {

            accept(selectionKey);

        } else if (selectionKey.isReadable()) {
            
            read(selectionKey);
            
        } else if (selectionKey.isWritable()) {

            write(selectionKey);

        }

    }

    private void accept(final SelectionKey selectionKey) throws IOException {

        SocketChannel socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selectionKey.selector(), SelectionKey.OP_READ);

        logger.log(Level.INFO, "Accepted connection from {0}", socketChannel.getRemoteAddress());

    }

    private void read(final SelectionKey selectionKey) throws IOException {

        ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        socketChannel.configureBlocking(false);

        int dataLength = socketChannel.read(byteBuffer);
        if (dataLength == -1) {

            logger.log(Level.INFO, "Reading empty request from socket {0}. Closing connection.", socketChannel.getRemoteAddress());

            closeConnection(selectionKey);

            return;

        }

        String requestRaw = new String(BufferUtils.get(byteBuffer));
        String[] requestHead = requestRaw.split("\r\n")[0].split(" ");
        logger.log(Level.INFO, "Reading request {0} from connection {1}.", new Object[]{requestRaw, socketChannel.getRemoteAddress()});

        HttpRequest httpRequest = new HttpRequest(requestHead[0], requestHead[1], requestHead[2]);

        selectionKey.attach(httpRequest);
        selectionKey.interestOps(SelectionKey.OP_WRITE);

    }

    private void write(final SelectionKey selectionKey) throws IOException {

        final SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        logger.log(Level.INFO, "Writing response for connection {0}.", socketChannel.getRemoteAddress());

        if (selectionKey.attachment() == null || !(selectionKey.attachment() instanceof HttpRequest httpRequest)) {

            logger.log(Level.WARNING, "Connection {0} doesn't have a HttpRequest attached to it. Closing connection.", socketChannel.getRemoteAddress());

            closeConnection(selectionKey);

            return;

        }

        String resPathStr;
        if ("/".equals(httpRequest.getPath())) {

            resPathStr = "src/main/resources/index.html";

        } else {

            resPathStr = "src/main/resources".concat(httpRequest.getPath());

        }

        Path resPath = Paths.get(resPathStr);
        byte[] resContent;
        String conType;
        String status;
        if (!Files.exists(resPath)) {

            resContent = "<b>404 - Not found!</b>".getBytes();
            conType = "text/html";
            status = "404 Not Found";

        } else {

            resContent = Files.readAllBytes(resPath);
            conType = Files.probeContentType(resPath);
            status = "200 OK";

        }

        sendResponse(socketChannel, status, conType, resContent);

        selectionKey.cancel();

    }

    private static void sendResponse(SocketChannel socketChannel, String status, String conType, byte[] content) throws IOException {

        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("HTTP/1.1 ").append(status).append("\r\n");
        responseBuilder.append("Content-Type: ").append(conType).append("\r\n\r\n");
        if ("text/html".equals(conType)) {
            responseBuilder.append(new String(content)).append("\r\n\r\n\r\n");
        } else {
            responseBuilder.append(Arrays.toString(content));
        }

        ByteBuffer responseBuffer = ByteBuffer.wrap(responseBuilder.toString().getBytes());
        socketChannel.write(responseBuffer);

        socketChannel.close();

    }

    private void closeConnection(SelectionKey selectionKey) {

        try {

            selectionKey.cancel();
            selectionKey.channel().close();

        } catch (IOException e) {

            throw new RuntimeException(e);

        }

    }

}
