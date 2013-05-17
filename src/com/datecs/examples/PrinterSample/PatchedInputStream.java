package com.datecs.examples.PrinterSample;

import java.io.IOException;
import java.io.InputStream;

/**
 * Implement a new class which use only read() method from original InputStream class.
 * Purpose of it is to fix problem with incorrect values, returned from available()
 * method in some devices.   
 */
final class PatchedInputStream extends InputStream {   
    // The size of buffer used to store data
    private static final int BUFFER_SIZE = 2048;
    
    // Member variables
    private InputStream mBaseStream;    
    private byte[] mBuf;
    private int mBufLen;
    private int mBufOffs;
    
    // Flag that indicates whether the stream is ready to receive data. 
    private volatile boolean mRunning;
    
    // Save original IOException
    private IOException mIOException;
    
    // Runnable to handle data reading.
    private final Runnable mReadingRunnable = new Runnable() {        
        @Override
        public void run() {
            // Repeat while stream is not closed 
            while (mRunning) {                
                int nbr = mBuf.length - mBufLen;
                // Read data until fill all data into buffer
                if (nbr > 0) {                                      
                    try {
                        int status = mBaseStream.read(mBuf, mBufLen, nbr);
                        if (status > 0) {
                            mBufLen += status;
                        } else if (status < 0) {
                            throw new IOException("The end of the stream has been reached");
                        }
                    } catch (IOException e) {
                        // Saves original exception 
                        mIOException = e;
                        break;
                    }                   
                } else {
                    // Gives to OS some breath
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                
                // Move usable data to the begging of the buffer
                synchronized (mBaseStream) {
                    if (mBufOffs > 0) {
                        System.arraycopy(mBuf, mBufOffs, mBuf, 0, mBufLen - mBufOffs);
                        mBufLen -= mBufOffs;
                        mBufOffs = 0;
                    }
                }
            }                       
        }
    };
    /**
     * Constructs a new instance of this class.
     * @param in the base input stream.
     */
    public PatchedInputStream(InputStream in) {
        if (in == null) {
            throw new NullPointerException("The in is invalid.");
        }
        mBaseStream = in;

        mBuf = new byte[BUFFER_SIZE];
        mBufLen = 0;
        mBufOffs = 0;
                
        mRunning = true;
        mIOException = null;
        new Thread(mReadingRunnable).start();       
    }

    @Override
    public int available() throws IOException {
        // Check if stream is closed
        if (mRunning == false) {
            throw new IOException("The stream is closed");
        }
        // Check if exception occurs into original stream
        if (mIOException != null) {
            throw mIOException;
        }
        // Returns available data into buffer
        synchronized (mBaseStream) {
            return (mBufLen - mBufOffs);
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (mBaseStream) {
            if (mRunning) {
                mRunning = false;
                mBaseStream.close();
                mBaseStream = null;
            }
        }        
    }

    @Override
    public int read() throws IOException {
        // Due original implementation block until data is available or error occurs. 
        while (true) {
            synchronized (mBaseStream) {
                int status = available(); 
                if (status > 0) {
                    // Get one byte from buffer
                    return (int)mBuf[mBufOffs++] & 0xff;
                }                                        
            }            
            // Gives to OS some breath
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } 
    }        
}
