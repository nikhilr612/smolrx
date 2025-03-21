package smolrx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.InflaterOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implement protocol-details pertaining to establishing a secure channel between client and servlet.
 */
public class SecureChannel implements Closeable {

    private static final int KEYSIZE_B = 256;
    private static final String ALGORITHM = "RSA"; // Ideally "X25519". RSA for now.
    private static final String SYM_ALGORITHM = "AES";
    private static final String ALGORITHM_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String SYM_ALGORIHTM_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final int BUFFER_SIZE = 1024; // < 32767

    private Cipher symCipher; // TODO Compare re-initialization vs two ciphers.
    private Socket conn;
    private SecretKey secretKey;

    private SecureChannel(Cipher symCipher, Socket conn, SecretKey secretKey) throws IOException {
        this.conn = conn;
        this.symCipher = symCipher;
        this.secretKey = secretKey;
    }

    /**
     * Send an object across the channel by serializing, compressing and encrypting.
     * @param o The object to be sent.
     * @throws IOException If any errors occur while writing to the socket.
     * @throws InvalidKeyException If Cipher re-initialization fails.
     * @throws IllegalBlockSizeException If Cipher operation fails.
     * @throws BadPaddingException If Cipher operation fails.
     */
    public void sendObject(Object o) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        var baos = new ByteArrayOutputStream();
        var defalteos = new DeflaterOutputStream(baos);
        var oos = new ObjectOutputStream(defalteos);
        oos.writeObject(o);

        oos.close();
        defalteos.close();
        
        this.symCipher.init(Cipher.ENCRYPT_MODE, this.secretKey);
        var encData = this.symCipher.doFinal(baos.toByteArray());

        conn.getOutputStream().write(ByteBuffer.allocate(4).putInt(encData.length).array());
        conn.getOutputStream().write(encData);
        conn.getOutputStream().flush();
    }

    /**
     * Read an object from the channel after decrypting, de-compresing and de-serializing.
     * @return The object read.
     * @throws IOException If any errros occur while reading from socket.
     * @throws ClassNotFoundException If the object fails to be de-serialized due to it's class not existing in the ClassLoader / ClassPath.
     * @throws InvalidKeyException If Cipher re-initialization fails.
     * @throws IllegalBlockSizeException If Cipher operaiton fails.
     * @throws BadPaddingException If Cipher operation fails.
     */
    public Object readObject() throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        var intBytes = new byte[4];
        conn.getInputStream().read(intBytes);
        var length = ByteBuffer.wrap(intBytes).getInt();

        var encDataBuf = new byte[length];
        conn.getInputStream().read(encDataBuf);

        this.symCipher.init(Cipher.DECRYPT_MODE, this.secretKey);
        var dataBuf = this.symCipher.doFinal(encDataBuf);

        var bais = new ByteArrayInputStream(dataBuf);
        var inflateris = new InflaterInputStream(bais);
        var ois = new ObjectInputStream(inflateris);

        Object ret = ois.readObject();
        ois.close();
    
        return ret;
    }

    /**
     * Send data from the input stream over the channel using chunks of BUFFER_SIZE.
     * @param inputStream The input stream to read data from and send.
     * @throws IOException If writing to underlying socket failed.
     * @throws InvalidKeyException If Cipher re-initialization failed.
     * @throws IllegalBlockSizeException If Cipher operation failed.
     * @throws BadPaddingException If Cipher operation failed.
     */
    public void sendStream(InputStream inputStream) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        DeflaterInputStream deflateis = new DeflaterInputStream(inputStream);
        var buffer = new byte[SecureChannel.BUFFER_SIZE];
        var lenBuffer = ByteBuffer.allocate(2);
        int len = 0;

        this.symCipher.init(Cipher.ENCRYPT_MODE, this.secretKey);
        
        while ((len = deflateis.read(buffer)) != 0) {
            var encData = this.symCipher.doFinal(buffer, 0, len);

            lenBuffer.clear();
            lenBuffer.putShort((short) encData.length);
            this.conn.getOutputStream().write(lenBuffer.array());
            this.conn.getOutputStream().write(encData);
        }

        // Send FIN 0x00_00.
        lenBuffer.clear(); lenBuffer.putShort((short)0);
        this.conn.getOutputStream().write(lenBuffer.array());

    }

    /**
     * Read a stream sent over the channel and write to the provided output stream.
     * @param outputStream The output stream to write data being read to.
     * @throws IOException If any errors occur while reading from the socket.
     * @throws InvalidKeyException If Cipher re-initialization failed.
     * @throws IllegalBlockSizeException If Cipher operation failed.
     * @throws BadPaddingException If Cipher operation failed.
     */
    public void readStream(OutputStream outputStream) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        InflaterOutputStream inflateos = new InflaterOutputStream(outputStream);
        var encDataBuf = new byte[SecureChannel.BUFFER_SIZE * 2]; // Over-allocate to avoid dealing with padding shenanigans.
        var lenBuffer = ByteBuffer.allocate(2);
        this.conn.getInputStream().read(lenBuffer.array());
        int encLen = lenBuffer.getShort();

        this.symCipher.init(Cipher.DECRYPT_MODE, this.secretKey);
            
        while (encLen != 0) {
            this.conn.getInputStream().read(encDataBuf, 0, encLen);
            var buffer = this.symCipher.doFinal(encDataBuf, 0, encLen);
            inflateos.write(buffer);

            this.conn.getInputStream().read(lenBuffer.array());
            encLen = lenBuffer.getShort();
        }
    }
    
    /**
     * Open a secure channel to the client from the server.
     * @param conn Socket connected to client.
     * @return a Secure channel ready for communication.
     * @throws NoSuchAlgorithmException If Cryptography provider does not support SecureChannel.ALGORITHM or SecureChannel.SYM_ALGORITHM
     * @throws IOException If any errors occur while writing / reading from socket.
     * @throws NoSuchPaddingException If Cipher initialization failed.
     * @throws InvalidKeyException If Cipher and and generated key are incompatible.
     * @throws IllegalBlockSizeException If client encrypted data is invalid.
     * @throws BadPaddingException If client encrypted data is incorrectly padded.
     */
    public static SecureChannel openClientChannel(Socket conn) throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        var generator = KeyPairGenerator.getInstance(SecureChannel.ALGORITHM);
        generator.initialize(KEYSIZE_B * 8, new SecureRandom());

        var keyPair = generator.genKeyPair();
        var publicKeyData = keyPair.getPublic().getEncoded();

        conn.getOutputStream().write(ByteBuffer.allocate(4).putInt(publicKeyData.length).array());
        conn.getOutputStream().write(publicKeyData);

        var lenBuf = new byte[4]; conn.getInputStream().read(lenBuf);
        int len = ByteBuffer.wrap(lenBuf).getInt();

        var clientRandomDataEnc = new byte[len];
        conn.getInputStream().read(clientRandomDataEnc);

        Cipher cipher = Cipher.getInstance(SecureChannel.ALGORITHM_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        var clientRandomData = cipher.doFinal(clientRandomDataEnc);

        SecretKeySpec secretKey = new SecretKeySpec(clientRandomData, SYM_ALGORITHM);
        Cipher symCipher = Cipher.getInstance(SecureChannel.SYM_ALGORIHTM_TRANSFORMATION);
        
        return new SecureChannel(symCipher, conn, secretKey);
    }

    /**
     * Open a secure channel to the server from the client.
     * @param conn Socket connected to the server.
     * @return a Secure Channel ready for communication.
     * @throws IOException If any errors occur while reading / writing socket.
     * @throws InvalidKeySpecException 
     * @throws NoSuchAlgorithmException If cryptography provider does not support SecureChannel.ALGORITHM or SecureChannel.SYM_ALGORITHM
     * @throws NoSuchPaddingException If cryptography provider does not support specified transformation padding
     * @throws InvalidKeyException If key received from server is invalid.
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException If data is incorrectly padded.
     */
    public static SecureChannel openServerChannel(Socket conn) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        var keyBuf = new byte[4];
        conn.getInputStream().read(keyBuf);
        var keyLen = ByteBuffer.wrap(keyBuf).getInt();
        var publicKeyData = new byte[keyLen];
        conn.getInputStream().read(publicKeyData);

        var publicKey = KeyFactory.getInstance(SecureChannel.ALGORITHM).generatePublic(new X509EncodedKeySpec(publicKeyData));
        var cipher = Cipher.getInstance(SecureChannel.ALGORITHM_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        Cipher symCipher = Cipher.getInstance(SecureChannel.SYM_ALGORIHTM_TRANSFORMATION);
        KeyGenerator kg = KeyGenerator.getInstance(SecureChannel.SYM_ALGORITHM);
        SecretKey secretKey = kg.generateKey();

        var randomDataEnc = cipher.doFinal(secretKey.getEncoded());
        
        conn.getOutputStream().write(ByteBuffer.allocate(4).putInt(randomDataEnc.length).array());
        conn.getOutputStream().write(randomDataEnc);

        return new SecureChannel(symCipher, conn, secretKey);
    }

    @Override
    public void close() throws IOException {
        this.conn.close();
    }
}
