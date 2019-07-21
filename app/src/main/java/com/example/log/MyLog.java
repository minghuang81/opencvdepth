package com.example.log;


import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyLog {
    private static final String DBGFILENAME="debug.txt";
    private static final int MAX_LOG_SZ=5000000;
    private static final String TAG = "THETADEBUG";
    private static File dbgFileDir = null;
    private static File dbgFile=null;
    private static FileOutputStream dbgFOS=null;
    static String extStorageDirectory = Environment.getExternalStorageDirectory().toString();
    static String basepath = extStorageDirectory + "/DCIM/";

    public static void Log(String msg) {
        try {
            // output to debug console first, then duplicate the message in the log file
            Log.d(TAG,msg);

            if (dbgFile == null || !dbgFile.exists()) { // log file has never existed
                dbgFileDir = new File(basepath);
                // Create the debug directory if it does not exist
                if (! dbgFileDir.exists()){
                    if (! dbgFileDir.mkdirs()) { //mkdirs compared to mkdir creates missing parents
                        Log.d(TAG, "MyLog: failed to create directory: "+Environment.getExternalStorageDirectory().toString());
                        return;
                    } else {
                        Log.d(TAG, "MyLog: created directory "+dbgFileDir.getPath());
                    }
                }
                dbgFile = new File(dbgFileDir.getPath()+File.separator+DBGFILENAME);
                Log.d(TAG, "MyLog new dbgFile : " + dbgFile.getPath());
                dbgFOS= new FileOutputStream(dbgFile, true); // append to
            }
            // if file reaches over its size limit, truncate
            if (dbgFile.length()>MAX_LOG_SZ) {
                RandomAccessFile randfile = new RandomAccessFile(dbgFile,"rw");
                randfile.setLength(0);
                randfile.close();
            }

            String timeStamp = new SimpleDateFormat("dd/MM_HH:mm:ss ").format(new Date());
            String output=timeStamp+msg+"\n";
            // output to log file
            dbgFOS.write(output.getBytes());
            dbgFOS.flush();
        } catch (Exception ex) {
            Log.d(TAG, "MyLog crash: "+ex.toString()+"; reset dbgFile");
            // recreate dbgFile
            dbgFile = null;
            if (dbgFOS != null) {
                try {
                    dbgFOS.close();
                } catch (IOException e) {
                    Log.d(TAG, "MyLog crash2: "+e.toString());
                }
            }
        }
    }
}




