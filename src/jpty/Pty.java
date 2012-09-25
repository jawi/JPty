/*
 * JPty - A small PTY interface for Java.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package jpty;

import static jtermios.JTermios.FIONREAD;
import static jtermios.JTermios.F_GETFL;
import static jtermios.JTermios.F_SETFL;
import static jtermios.JTermios.O_NONBLOCK;
import static jtermios.JTermios.fcntl;
import static jtermios.JTermios.ioctl;
import static jtermios.JTermios.tcdrain;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jtermios.JTermios;

/**
 * Denotes a pseudoterminal.
 */
public final class Pty implements Closeable {
    
    // VARIABLES

    int m_childPid;
    int m_fdMaster;

    private volatile InputStream m_masterIS;
    private volatile OutputStream m_masterOS;

    // CONSTRUCTORS
    
    /**
     * Creates a new {@link Pty} instance.
     * 
     * @param fdMaster the file descriptor of the master process;
     * @param childPid the PID of the child process.
     */
    Pty(int fdMaster, int childPid) {
        m_fdMaster = fdMaster;
        m_childPid = childPid;
    }

    // METHODS
    
    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close() throws IOException {
        int fd = m_fdMaster;
        if (fd != -1) {
            m_fdMaster = -1;
            // Make the FD non-blocking so we can close it properly...
            int flags = fcntl(fd, F_GETFL, 0);
            flags |= O_NONBLOCK;
            int fcres = fcntl(fd, F_SETFL, flags);
            if (fcres == 0) {
                int err = JTermios.close(fd);
                if (err != 0) {
                    throw new IOException("Failed to close pseudoterminal!");
                }

                m_masterIS = closeSilently(m_masterIS);
                m_masterOS = closeSilently(m_masterOS);
            }
        }
    }

    /**
     * Returns the input stream to the pseudoterminal.
     * 
     * @return an input stream to the pseudoterminal, never <code>null</code>.
     */
    public InputStream getInputStream() {
        checkState();

        if (m_masterIS != null) {
            return m_masterIS;
        }

        // Inspired by PureJavaComm...
        return m_masterIS = new InputStream() {
            @Override
            public int available() throws IOException {
                checkState();
                int[] available = { 0 };
                if (ioctl(m_fdMaster, FIONREAD, available) < 0) {
                    close();
                    throw new EOFException();
                }
                return available[0];
            }

            @Override
            public void close() throws IOException {
                checkState();
                Pty.this.close();
            }

            @Override
            public int read() throws IOException {
                checkState();
                byte[] b = new byte[1];
                return read(b, 0, 1);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                checkState();
                return JTermios.read(m_fdMaster, b, len);
            }
        };
    }

    /**
     * Returns the output stream to the pseudoterminal.
     * 
     * @return an output stream to the pseudoterminal, never <code>null</code>.
     */
    public OutputStream getOutputStream() {
        checkState();

        if (m_masterOS != null) {
            return m_masterOS;
        }

        // Inspired by PureJavaComm...
        return m_masterOS = new OutputStream() {
            private final byte[] m_buffer = new byte[2048];
            
            @Override
            public void close() throws IOException {
                checkState();
                Pty.this.close();
            }

            @Override
            public void flush() throws IOException {
                checkState();
                if (tcdrain(m_fdMaster) < 0) {
                    close();
                    throw new EOFException();
                }
            }

            @Override
            public void write(byte[] b) throws IOException {
                write(b, 0, b.length);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                checkState();

                while (len > 0) {
                    int n = Math.min(len, Math.min(m_buffer.length, b.length - off));
                    if (off > 0) {
                        System.arraycopy(b, off, m_buffer, 0, n);
                        n = JTermios.write(m_fdMaster, m_buffer, n);
                    }
                    else {
                        n = JTermios.write(m_fdMaster, b, n);
                    }

                    if (n < 0) {
                        Pty.this.close();
                        throw new EOFException();
                    }

                    len -= n;
                    off += n;
                }
            }

            @Override
            public void write(int b) throws IOException {
                write(new byte[] { (byte) b }, 0, 1);
            }
        };
    }
    
    /**
     * Returns the current window size of this PTY.
     * 
     * @return a {@link WinSize} instance with information about the master side of the PTY, never <code>null</code>.
     * @throws IOException in case obtaining the window size failed.
     */
    public WinSize getWinSize() throws IOException {
        WinSize result = new WinSize();
        if (JPty.getWinSize(m_fdMaster, result) < 0) {
            throw new IOException("Failed to get window size: " + JPty.errno());
        }
        return result;
    }
    
    /**
     * Sets the current window size of this PTY.
     * 
     * @param winSize the {@link WinSize} instance to set on the master side of the PTY, cannot be <code>null</code>.
     * @throws IllegalArgumentException in case the given argument was <code>null</code>.
     * @throws IOException in case obtaining the window size failed.
     */
    public void setWinSize(WinSize winSize) throws IOException {
        if (winSize == null) {
            throw new IllegalArgumentException("WinSize cannot be null!");
        }
        if (JPty.setWinSize(m_fdMaster, winSize) < 0) {
            throw new IOException("Failed to set window size: " + JPty.errno());
        }
    }

    /**
     * Causes the current thread to wait, if necessary, until the process represented by this PTY object has terminated. This method returns immediately if the subprocess has already terminated. If the subprocess has not yet terminated, the calling thread
     * will be blocked until the subprocess exits.
     * 
     * @return the exit value of the process. By convention, <tt>0</tt> indicates normal termination.
     * @exception InterruptedException if the current thread is interrupted by another thread while it is waiting, then the wait is ended and an {@link InterruptedException} is thrown.
     */
    public int waitFor() throws InterruptedException {
        if (m_childPid < 0) {
            return -1;
        }

        int[] stat = new int[] { -1 };
        // Blocks until the child PID is terminated!!!
        JPty.waitpid(m_childPid, stat, 0);
        
        return stat[0];
    }

    private void checkState() {
        if (m_fdMaster < 0) {
            throw new IllegalStateException("Invalid file descriptor; PTY already closed?!");
        }
    }
    
    private <T extends Closeable> T closeSilently(T resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        }
        catch (IllegalStateException e ) {
            // Ignore...
        }
        catch (IOException e) {
            // Ignore...
        }
        return null;
    }
}
