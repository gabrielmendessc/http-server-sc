package gabrielmendessc.me.http.server.sc;

import java.nio.ByteBuffer;

public class BufferUtils {

    public static byte[] get(ByteBuffer byteBuffer) {

        byteBuffer.flip();
        byte[] bytes = new byte[byteBuffer.limit()];
        byteBuffer.get(bytes);

        return bytes;

    }

}
