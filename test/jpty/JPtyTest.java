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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Platform;

import junit.framework.TestCase;

/**
 * Test cases for {@link JPty}.
 */
public class JPtyTest extends TestCase {
    
    private String m_command;
    private String[] m_args;

    /**
     * Tests that we can execute a process in a PTY.
     */
    public void testExecInPTY() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        // Start the process in a PTY...
        final Pty pty = JPty.execInPTY(m_command, m_args);
        final int[] result = { -1 };

        // Asynchronously wait for the process to end...
        Thread t = new Thread() {
            public void run() {
                try {
                    result[0] = pty.waitFor();
                    latch.countDown();
                }
                catch (InterruptedException e) {
                    // Simply stop the thread...
                }
            }
        };
        t.start();
        t.join();

        latch.await(10, TimeUnit.SECONDS);
        
        assertEquals("Unexpected process result!", 0, result[0]);
    }

    /**
     * Tests that getting and setting the window size for a file descriptor works.
     */
    public void testGetAndSetWinSize() throws Exception {
        Pty pty = JPty.execInPTY(m_command, m_args);
        
        WinSize ws = new WinSize();
        ws.ws_col = 120;
        ws.ws_row = 30;
        pty.setWinSize(ws);

        WinSize ws1 = pty.getWinSize();
        assertNotNull(ws1);

        pty.waitFor();
    }

    protected void setUp() throws Exception {
        if (Platform.isWindows()) {
            m_command = "ping";
            m_args = new String[] { "-n", "2", "127.0.0.1" };
        }
        else if (Platform.isSolaris()) {
            m_command = "/usr/sbin/ping";
            m_args = new String[] { "-s", "127.0.0.1", "64", "2" };
        }
        else if (Platform.isMac() || Platform.isLinux()) {
            m_command = "/sbin/ping";
            m_args = new String[] { "-c", "2", "127.0.0.1" };
        }
    }
}
