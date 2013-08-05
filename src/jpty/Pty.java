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


import static jpty.JPty.SIGKILL;
import static jpty.JPty.isProcessAlive;
import static jpty.JPty.signal;
import static jpty.JPty.waitpid;
import static jtermios.JTermios.FIONREAD;
import static jtermios.JTermios.F_GETFL;
import static jtermios.JTermios.F_SETFL;
import static jtermios.JTermios.O_NONBLOCK;
import static jtermios.JTermios.errno;
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
public final class Pty implements Closeable
{

  // VARIABLES

  private volatile int m_childPid;
  private volatile int m_fdMaster;

  private volatile InputStream m_masterIS;
  private volatile OutputStream m_masterOS;

  // CONSTRUCTORS

  /**
   * Creates a new {@link Pty} instance.
   * 
   * @param fdMaster
   *          the file descriptor of the master process;
   * @param childPid
   *          the PID of the child process.
   */
  Pty( int fdMaster, int childPid )
  {
    m_fdMaster = fdMaster;
    m_childPid = childPid;
  }

  // METHODS

  /**
   * Closes this {@link Pty} instance, and terminates the child process, in case
   * it is still running.
   * 
   * @throws IOException
   *           in case closing of this {@link Pty} failed.
   */
  @Override
  public void close() throws IOException
  {
    close( true /* terminateChild */);
  }

  /**
   * Closes this {@link Pty} instance, possibly by forcefully terminating the
   * child process.
   * <p>
   * Note that if the child is not yet terminated, and this method is called
   * with <tt>false</tt> as argument, this method will <em>block</em> until the
   * child process terminates naturally, which can be indefinitely.
   * </p>
   * 
   * @param terminateChild
   *          <code>true</code> to force a termination of the child process,
   *          <code>false</code> to let the child process terminate naturally.
   * @throws IOException
   *           in case closing of this {@link Pty} failed.
   */
  public void close( boolean terminateChild ) throws IOException
  {
    final int fd = m_fdMaster;
    final int childPid = m_childPid;

    m_fdMaster = -1;
    m_childPid = -1;

    if ( fd != -1 )
    {
      // Make the FD non-blocking so we can close it properly...
      int flags = fcntl( fd, F_GETFL, 0 );
      flags |= O_NONBLOCK;
      fcntl( fd, F_SETFL, flags );

      // Request the child to terminate, if desired; see issue #1...
      if ( terminateChild && isProcessAlive( childPid ) )
      {
        signal( childPid, SIGKILL );
      }

      int err = JTermios.close( fd );
      if ( err != 0 )
      {
        throw new IOException( "Failed to close pseudoterminal!" );
      }

      m_masterIS = closeSilently( m_masterIS );
      m_masterOS = closeSilently( m_masterOS );
    }
  }

  /**
   * Returns the input stream to the pseudoterminal.
   * 
   * @return an input stream to the pseudoterminal, never <code>null</code>.
   */
  public InputStream getInputStream()
  {
    checkState();

    if ( m_masterIS != null )
    {
      return m_masterIS;
    }

    // Inspired by PureJavaComm...
    return m_masterIS = new InputStream()
    {
      @Override
      public int available() throws IOException
      {
        checkState();

        int[] available = { 0 };
        if ( ioctl( m_fdMaster, FIONREAD, available ) < 0 )
        {
          close();
          throw new EOFException();
        }

        return available[0];
      }

      @Override
      public void close() throws IOException
      {
        checkState();

        super.close();
      }

      @Override
      public int read() throws IOException
      {
        checkState();

        byte[] b = new byte[1];
        int read = read( b, 0, 1 );

        return ( read == 1 ) ? ( b[0] & 0xFF ) : -1;
      }

      @Override
      public int read( byte[] b, int off, int len ) throws IOException
      {
        checkState();

        int read = JTermios.read( m_fdMaster, b, len );
        // see read(2), section about possible return values; thanks to ceharris
        // for pointing this out...
        if ( read == 0 )
        {
          return -1; // EOF
        }
        else if ( read < 0 )
        {
          closeWithException( "I/O read failed with errno #" + errno() + "!" );
        }
        return read;
      }
    };
  }

  /**
   * Returns the output stream to the pseudoterminal.
   * 
   * @return an output stream to the pseudoterminal, never <code>null</code>.
   */
  public OutputStream getOutputStream()
  {
    checkState();

    if ( m_masterOS != null )
    {
      return m_masterOS;
    }

    // Inspired by PureJavaComm...
    return m_masterOS = new OutputStream()
    {
      private final byte[] m_buffer = new byte[2048];

      @Override
      public void close() throws IOException
      {
        checkState();

        super.close();
      }

      @Override
      public void flush() throws IOException
      {
        checkState();

        if ( tcdrain( m_fdMaster ) < 0 )
        {
          closeWithException( "I/O flush failed with errno #" + errno() + "!" );
        }
      }

      @Override
      public void write( byte[] b ) throws IOException
      {
        write( b, 0, b.length );
      }

      @Override
      public void write( byte[] b, int off, int len ) throws IOException
      {
        checkState();

        while ( len > 0 )
        {
          int n = Math.min( len, Math.min( m_buffer.length, b.length - off ) );
          if ( off > 0 )
          {
            System.arraycopy( b, off, m_buffer, 0, n );
            n = JTermios.write( m_fdMaster, m_buffer, n );
          }
          else
          {
            n = JTermios.write( m_fdMaster, b, n );
          }

          if ( n < 0 )
          {
            closeWithException( "I/O write failed with errno #" + errno() + "!" );
          }

          len -= n;
          off += n;
        }
      }

      @Override
      public void write( int b ) throws IOException
      {
        write( new byte[] { ( byte )b }, 0, 1 );
      }
    };
  }

  /**
   * Returns the current window size of this PTY.
   * 
   * @return a {@link WinSize} instance with information about the master side
   *         of the PTY, never <code>null</code>.
   * @throws IOException
   *           in case obtaining the window size failed.
   */
  public WinSize getWinSize() throws IOException
  {
    WinSize result = new WinSize();
    if ( JPty.getWinSize( m_fdMaster, result ) < 0 )
    {
      throw new IOException( "Failed to get window size: " + JPty.errno() );
    }
    return result;
  }

  /**
   * Tests whether the child-process is still alive or already terminated.
   * 
   * @return <code>true</code> if the child process is still alive,
   *         <code>false</code> if it is terminated.
   */
  public boolean isChildAlive()
  {
    return JPty.isProcessAlive( m_childPid );
  }

  /**
   * Sets the current window size of this PTY.
   * 
   * @param winSize
   *          the {@link WinSize} instance to set on the master side of the PTY,
   *          cannot be <code>null</code>.
   * @throws IllegalArgumentException
   *           in case the given argument was <code>null</code>.
   * @throws IOException
   *           in case obtaining the window size failed.
   */
  public void setWinSize( WinSize winSize ) throws IOException
  {
    if ( winSize == null )
    {
      throw new IllegalArgumentException( "WinSize cannot be null!" );
    }
    if ( JPty.setWinSize( m_fdMaster, winSize ) < 0 )
    {
      throw new IOException( "Failed to set window size: " + JPty.errno() );
    }
  }

  /**
   * Causes the current thread to wait, if necessary, until the process
   * represented by this PTY object has terminated. This method returns
   * immediately if the subprocess has already terminated. If the subprocess has
   * not yet terminated, the calling thread will be blocked until the subprocess
   * exits.
   * 
   * @return the exit value of the process. By convention, <tt>0</tt> indicates
   *         normal termination.
   * @exception InterruptedException
   *              if the current thread is interrupted by another thread while
   *              it is waiting, then the wait is ended and an
   *              {@link InterruptedException} is thrown.
   */
  public int waitFor() throws InterruptedException
  {
    if ( m_childPid < 0 )
    {
      return -1;
    }

    int[] stat = new int[] { -1 };
    // Blocks until the child PID is terminated!!!
    waitpid( m_childPid, stat, 0 );

    return stat[0];
  }

  void checkState()
  {
    if ( m_fdMaster < 0 )
    {
      throw new IllegalStateException( "Invalid file descriptor; PTY already closed?!" );
    }
  }

  void closeWithException( String msg ) throws IOException
  {
    IOException cause = null;
    try
    {
      close();
    }
    catch ( IOException suppressed )
    {
      cause = suppressed;
    }
    throw new IOException( msg, cause );
  }

  static <T extends Closeable> T closeSilently( T resource )
  {
    try
    {
      if ( resource != null )
      {
        resource.close();
      }
    }
    catch ( Exception e )
    {
      // Ignore...
    }
    return null;
  }
}
