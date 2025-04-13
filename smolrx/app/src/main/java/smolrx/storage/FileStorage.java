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

    public FileStorage(String baseDirPath) {
        super();
        this.baseDirectory = new File(baseDirPath);
        assert(this.baseDirectory.isDirectory());
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
        if (!jobDirectory.exists()) jobDirectory.mkdirs();
        var file = File.createTempFile("smrx_", null, jobDirectory);
        var fos = new FileOutputStream(file);
        var oos = new ObjectOutputStream(fos);
        oos.writeObject(pResult.getResultObject());
        oos.close();
    }
}
