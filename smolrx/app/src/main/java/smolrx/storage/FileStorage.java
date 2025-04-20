package smolrx.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import smolrx.msg.InspectResult;
import smolrx.msg.PushResult;

/**
 * Implement Object storage by storing them in a 2-level structure organized by JobID and UID.
 */
public class FileStorage extends ObjectStorage {

    /**
     * Base directory for storing files.
     */
    File baseDirectory;

    // Changed to private constructor to enforce use of static factory method.
    // Prevents partial initialization.
    private FileStorage(String baseDirPath) {
        this.baseDirectory = new File(baseDirPath);
    }

    /**
     * Create a new FileStorage object with the specified base directory.
     * If the directory does not exist, it will be created.
     * @param baseDirPath The base directory path for storing files.
     * @return A new FileStorage object.
     * @throws IllegalArgumentException If the base directory is not a directory or cannot be created.
     */
    public static FileStorage create(String baseDirPath) {
        var baseDirectory = new File(baseDirPath);
        if (!baseDirectory.exists()) {
            if (!baseDirectory.mkdirs()) {
                throw new IllegalArgumentException("Failed to create base directory: " + baseDirPath);
            }
        } else if (!baseDirectory.isDirectory()) {
            throw new IllegalArgumentException("Base directory is not a directory: " + baseDirPath);
        }
        return new FileStorage(baseDirPath);
    }

    @Override
    public Object[] getResults(InspectResult iResult) throws IOException, ClassNotFoundException {
        var jobDirectory = new File(this.baseDirectory, "J" + iResult.getJobId() + File.pathSeparator);
        if (!jobDirectory.exists()) return null;
        var files = jobDirectory.listFiles();
        var len = Integer.min(files.length, iResult.getLimit());
        var ret = new Object[len];
        for (int i = 0; i < len; i++) {
            var tfis = new FileInputStream(files[i]);
            var ois = new ObjectInputStream(tfis);
            ret[i] = ois.readObject();
            ois.close();
        }
        return ret;
    }

    @Override
    public void putResult(PushResult pResult) throws IOException {
        var jobDirectory = new File(this.baseDirectory, "J" + pResult.getJobId() + File.pathSeparator);
        if (!jobDirectory.exists()) if (!jobDirectory.mkdirs()) {
            throw new IOException("Failed to create job directory: " + jobDirectory.getAbsolutePath());
        }
        var file = File.createTempFile("smrx_", null, jobDirectory);
        var fos = new FileOutputStream(file);
        var oos = new ObjectOutputStream(fos);
        oos.writeObject(pResult.getResultObject());
        oos.close();
    }
}
