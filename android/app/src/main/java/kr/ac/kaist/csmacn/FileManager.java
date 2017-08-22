package kr.ac.kaist.csmacn;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

/**
 * Created by jeffreychang on 2016. 5. 7..
 */
public class FileManager {

    private final File file;
    private final RandomAccessFile randomAccessFile;
    private int previousPacketOffset = 0;

    public FileManager(Uri filePath) throws URISyntaxException, FileNotFoundException {
        this.file = new File(new URI(filePath.toString()));
        this.randomAccessFile = new RandomAccessFile(this.file, "r");
    }

    public FileWriter getLogFileStream(){
        GregorianCalendar calendar = new GregorianCalendar();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if(!path.exists()) path.mkdirs();
            FileWriter fileWriter = new FileWriter(new File(path, "CSMA_CN" + sdf.format(calendar.getTime()) + ".csv"));
            return fileWriter;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void closeFileWriter(FileWriter fileWriter){
        try {
            fileWriter.flush();
            fileWriter.close();
            Log.i("FileManager", "successfully closed the file.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public long getFileSizeInBytes() {
        return file.length();
    }

    public byte[] getFileChunk(int bufferLen, int packetOffset) throws IOException {
        byte[] buffer = new byte[bufferLen];
        if(previousPacketOffset + 1 != packetOffset) randomAccessFile.seek(packetOffset * TransmissionManager.packetSize);

        previousPacketOffset = packetOffset;
        randomAccessFile.read(buffer);
        return buffer;
    }
}
