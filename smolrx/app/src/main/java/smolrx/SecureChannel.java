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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
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
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implement protocol-details pertaining to establishing a secure channel between client and servlet.
 */
public class SecureChannel implements Closeable {

    // TODO: Re-factor all methods to use `ChannelException` and group channel-related errors into one. This will reduce try-catch gore.

    private static final int KEYSIZE_B = 256;
    private static final String ALGORITHM = "RSA"; // Ideally "X25519". RSA for now.
    private static final String SYM_ALGORITHM = "AES";
    private static final String ALGORITHM_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
    private static final String SYM_ALGORIHTM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int BUFFER_SIZE = 4096; // < 32767

    private static final int IV_SIZE = 12; // 96-bits; optimal for GCM.
    private static final int TAG_SIZE = 128; // 128-bits; optimal for GCM.

    /**
     * Generate a random IV for the cipher.
     * @param iv The byte array to be filled with the IV.
     * @return The algorithm parameter spec for the cipher.
     */
    private static AlgorithmParameterSpec generateSpec(byte[] iv) {
        new SecureRandom().nextBytes(iv); // randomize IV.
        return new GCMParameterSpec(TAG_SIZE, iv); // Use 128-bit tag.
    }

    /**
     * Recover the algorithm parameter spec from the provided IV.
     * @param iv The IV to be used for the spec..
     * @return The algorithm parameter spec for the cipher.
     */
    private static AlgorithmParameterSpec recoverSpec(byte[] iv) {
        return new GCMParameterSpec(TAG_SIZE, iv); // Use 128-bit tag.
    }

    private Cipher symCipher; // TODO: Compare re-initialization vs two ciphers.
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
     * @throws InvalidAlgorithmParameterException If Cipher re-initialization with nonce fails. 
     */
    public void sendObject(Object o) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        var baos = new ByteArrayOutputStream();
        var defalteos = new DeflaterOutputStream(baos);
        var oos = new ObjectOutputStream(defalteos);
        oos.writeObject(o);

        oos.close();
        defalteos.close();
        
        var iv = new byte[SecureChannel.IV_SIZE];
        this.symCipher.init(Cipher.ENCRYPT_MODE, this.secretKey, SecureChannel.generateSpec(iv));

        var encData = this.symCipher.doFinal(baos.toByteArray());

        conn.getOutputStream().write(iv); // Send IV first.
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
     * @throws InvalidAlgorithmParameterException If Cipher initialization with nonce fails.
     */
    public Object readObject() throws IOException, ClassNotFoundException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException{
        var iv = new byte[SecureChannel.IV_SIZE]; // Read IV first.
        if (conn.getInputStream().read(iv) != SecureChannel.IV_SIZE) {
            throw new IOException("Failed to read IV.");
        }
        
        var intBytes = new byte[4];
        if (conn.getInputStream().read(intBytes) != 4) {
            throw new IOException("Failed to read length of incoming data.");
        }
        var length = ByteBuffer.wrap(intBytes).getInt();

        var encDataBuf = new byte[length];
        if (conn.getInputStream().read(encDataBuf) != length) {
            throw new IOException("Failed to read object data.");
        }

        this.symCipher.init(Cipher.DECRYPT_MODE, this.secretKey, SecureChannel.recoverSpec(iv));
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
     * @throws InvalidAlgorithmParameterException If Cipher re-initialization with nonce failed. 
     */
    public void sendStream(InputStream inputStream) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        DeflaterInputStream deflateis = new DeflaterInputStream(inputStream);
        var buffer = new byte[SecureChannel.BUFFER_SIZE];
        var lenBuffer = ByteBuffer.allocate(2);
        int len = 0;

        var iv = new byte[SecureChannel.IV_SIZE];
        
        while ((len = deflateis.read(buffer)) != -1) {
            // Cipher needs re-initialization for each chunk of data. Cannot use single IV for whole transmission.
            this.symCipher.init(Cipher.ENCRYPT_MODE, this.secretKey, SecureChannel.generateSpec(iv));

            var encData = this.symCipher.doFinal(buffer, 0, len);

            lenBuffer.clear();
            lenBuffer.putShort((short) encData.length);
            this.conn.getOutputStream().write(lenBuffer.array());

            this.conn.getOutputStream().write(iv);
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
     * @throws InvalidAlgorithmParameterException If Cipher re-initialization with nonce failed. 
     */
    public void readStream(OutputStream outputStream) throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        InflaterOutputStream inflateos = new InflaterOutputStream(outputStream);
        var encDataBuf = new byte[SecureChannel.BUFFER_SIZE * 2]; // Over-allocate to avoid dealing with padding shenanigans.
        var lenBuffer = ByteBuffer.allocate(2);
        
        if (this.conn.getInputStream().read(lenBuffer.array()) != 2) {
            throw new IOException("Failed to read length of incoming data.");
        }

        int encLen = lenBuffer.getShort();
            
        while (encLen != 0) {
            var iv = new byte[SecureChannel.IV_SIZE]; // Read IV first.
            if (this.conn.getInputStream().read(iv) != SecureChannel.IV_SIZE) {
                throw new IOException("Failed to read IV.");
            }

            this.symCipher.init(Cipher.DECRYPT_MODE, this.secretKey, SecureChannel.recoverSpec(iv));

            if (this.conn.getInputStream().read(encDataBuf, 0, encLen) != encLen) {
                throw new IOException("Failed to read stream data.");
            }

            var buffer = this.symCipher.doFinal(encDataBuf, 0, encLen);
            inflateos.write(buffer);

            lenBuffer.clear();
            if (this.conn.getInputStream().read(lenBuffer.array()) != 2) {
                throw new IOException("Failed to read length of incoming data.");
            }

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

        var lenBuf = new byte[4]; 
        if(conn.getInputStream().read(lenBuf) != 4) 
            throw new IOException("Failed to read length of incoming data. Failed to negotiate channel.");
        int len = ByteBuffer.wrap(lenBuf).getInt();

        var clientRandomDataEnc = new byte[len];
        if (conn.getInputStream().read(clientRandomDataEnc) != len) {
            throw new IOException("Failed to negotiate channel security.");
        }

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
        if (conn.getInputStream().read(keyBuf) != 4) throw 
            new IOException("Failed to negotiate channel security, could not read key length.");

        var keyLen = ByteBuffer.wrap(keyBuf).getInt();

        var publicKeyData = new byte[keyLen];
        if (conn.getInputStream().read(publicKeyData) != keyLen) throw 
            new IOException("Failed to negotiate channel security, could not read key.");

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

    @Override
    public String toString() {
        return "SecureChannel[" + this.conn.toString() + "]";
    }
}