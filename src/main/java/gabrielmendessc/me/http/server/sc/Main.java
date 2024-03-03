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
import java.util.Set;
import java.util.StringTokenizer;

public class Main {

    public static void main(String[] args) throws IOException {

        final Selector selector = Selector.open();

        final ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(8080));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {

            selector.select();
            Iterator<SelectionKey> selectionKeyIterator = selector.selectedKeys().iterator();
            while (selectionKeyIterator.hasNext()) {

                final SelectionKey selectionKey = selectionKeyIterator.next();
                selectionKeyIterator.remove();

                try {

                    if (selectionKey.isValid()) {

                        if (selectionKey.isAcceptable()) {

                            accept(selectionKey);

                        } else if (selectionKey.isWritable()) {

                            write(selectionKey);

                        } else if (selectionKey.isReadable()) {

                            read(selectionKey);

                        }

                    }

                } catch (Exception e) {
                    System.out.printf("Error while handling channel '%s'.\nException: %s", selectionKey.channel(), e);
                    System.out.println();
                    e.printStackTrace();
                    selectionKey.cancel();
                    selectionKey.channel().close();
                }

            }

        }

    }

    private static void accept(final SelectionKey selectionKey) throws IOException {

        SocketChannel socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selectionKey.selector(), SelectionKey.OP_READ);

        System.out.println("Got new connection " + socketChannel);

    }

    private static void read(final SelectionKey selectionKey) throws IOException {

        final SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
        System.out.println("Reading from socket " + socketChannel);

        int data = socketChannel.read(byteBuffer);
        if (data == -1) {
            socketChannel.configureBlocking(false);
            socketChannel.close();
            return;
        }

        byteBuffer.flip();
        String requestRaw = new String(byteBuffer.array());
        StringTokenizer requestTokenizer = new StringTokenizer(requestRaw);
        HttpRequest httpRequest = new HttpRequest(requestTokenizer.nextToken().toUpperCase(), requestTokenizer.nextToken(), requestTokenizer.nextToken());

        socketChannel.configureBlocking(false);

        selectionKey.attach(httpRequest);
        selectionKey.interestOps(SelectionKey.OP_WRITE);

        /*BufferedReader buffReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        StringBuilder requestBuilder = new StringBuilder();

        String line;
        while (!(line = buffReader.readLine()).isBlank()) {

            requestBuilder.append(line).append("\r\n");

        }

        String request = requestBuilder.toString();
        String[] requestLines = request.split("\r\n");
        String[] requestHead = requestLines[0].split(" ");
        String method = requestHead[0];
        String path = requestHead[1];
        String version = requestHead[2];
        String host = requestLines[1].split(" ")[1];

        List<String> headerList = new ArrayList<>();
        for (int i = 2; i < requestLines.length; i++) {

            headerList.add(requestLines[i]);

        }

        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {

            sendResponse(client, "200 OK", Files.probeContentType(filePath), Files.readAllBytes(filePath));

            return;

        }

        sendResponse(client, "404 Not Found", "text/html", "<h1>Not found:(</h1>".getBytes());*/

    }

    private static void write(final SelectionKey selectionKey) throws IOException {

        final SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        System.out.println("Writing for " + socketChannel);

        if (selectionKey.attachment() == null) {
            return;
        }

        HttpRequest httpRequest = (HttpRequest) selectionKey.attachment();
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
            status = "200 - OK";

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

}
