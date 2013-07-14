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


import static jtermios.JTermios.F_SETFL;
import static jtermios.JTermios.fcntl;

import java.util.Arrays;

import jtermios.Termios;

import com.sun.jna.Native;
import com.sun.jna.Platform;


/**
 * Provides access to the pseudoterminal functionality on POSIX(-like) systems,
 * emulating such system calls on non POSIX systems.
 */
public class JPty
{
  // INNER TYPES

  /**
   * Provides a OS-specific interface to the JPty methods.
   */
  public static interface JPtyInterface
  {
    /**
     * Transforms the calling process into a new process.
     * 
     * @param command
     *          the command to execute;
     * @param argv
     *          the arguments, by convention begins with the command to execute;
     * @param env
     *          the (optional) environment options.
     * @return 0 upon success, -1 upon failure (see {@link JPty#errno()} for
     *         details).
     */
    int execve( String command, String[] argv, String[] env );

    /**
     * Forks a slave process in a pseudoterminal and prepares this process for
     * executing a process.
     * 
     * @param amaster
     *          the array in which the FD of the master will be stored;
     * @param name
     *          the array in which (optional) name of the slave PTY will be
     *          stored;
     * @param termp
     *          the initial termios options to use for the slave;
     * @param winp
     *          the initial winsize options to use for the slave.
     * @return 0 upon success, -1 upon failure (see {@link JPty#errno()} for
     *         details).
     */
    int forkpty( int[] amaster, byte[] name, Termios termp, WinSize winp );

    /**
     * Returns the window size information for the process with the given FD and
     * stores the results in the given {@link WinSize} structure.
     * 
     * @param fd
     *          the FD of the process to query;
     * @param ws
     *          the WinSize structure to store the results into.
     * @return 0 upon success, -1 upon failure (see {@link JPty#errno()} for
     *         details).
     */
    int getWinSize( int fd, WinSize ws );

    /**
     * Terminates or signals the process with the given PID.
     * 
     * @param pid
     *          the process ID to terminate or signal;
     * @param signal
     *          the signal number to send, for example, 9 to terminate the
     *          process.
     * @return a value of <tt>0</tt> upon success, or a non-zero value in case
     *         of an error (see {@link JPty#errno()} for details).
     */
    int kill( int pid, int sig );

    /**
     * Sets the window size information for the process with the given FD using
     * the given {@link WinSize} structure.
     * 
     * @param fd
     *          the FD of the process to set the window size for;
     * @param ws
     *          the WinSize structure with information about the window size.
     * @return 0 upon success, -1 upon failure (see {@link JPty#errno()} for
     *         details).
     */
    int setWinSize( int fd, WinSize ws );

    /**
     * Waits until the process with the given PID is stopped.
     * 
     * @param pid
     *          the PID of the process to wait for;
     * @param stat
     *          the array in which the result code of the process will be
     *          stored;
     * @param options
     *          the options for waitpid (not used at the moment).
     * @return 0 upon success, -1 upon failure (see {@link JPty#errno()} for
     *         details).
     */
    int waitpid( int pid, int[] stat, int options );
  }

  // CONSTANTS

  public static int ONLCR = 0x04;

  public static int VINTR = 0;
  public static int VQUIT = 1;
  public static int VERASE = 2;
  public static int VKILL = 3;
  public static int VSUSP = 10;
  public static int VREPRINT = 12;
  public static int VWERASE = 14;

  public static int ECHOCTL = 0x1000;
  public static int ECHOKE = 0x4000;

  public static int SIGHUP = 1;
  public static int SIGINT = 2;
  public static int SIGQUIT = 3;
  public static int SIGILL = 4;
  public static int SIGABORT = 6;
  public static int SIGFPE = 8;
  public static int SIGKILL = 9;
  public static int SIGSEGV = 11;
  public static int SIGPIPE = 13;
  public static int SIGALRM = 14;
  public static int SIGTERM = 15;

  public static int WNOHANG = 1;
  public static int WUNTRACED = 2;

  // VARIABLES

  private static JPtyInterface m_jpty;

  // METHODS

  static
  {
    if ( Platform.isMac() )
    {
      m_jpty = new jpty.macosx.JPtyImpl();
    }
    else if ( Platform.isLinux() )
    {
      m_jpty = new jpty.linux.JPtyImpl();
    }
    else if ( Platform.isSolaris() )
    {
      m_jpty = new jpty.solaris.JPtyImpl();
    }
    else if ( Platform.isFreeBSD() )
    {
      m_jpty = new jpty.freebsd.JPtyImpl();
    }
    else
    {
      throw new RuntimeException( "JPty has no support for OS " + System.getProperty( "os.name" ) );
    }
  }

  /**
   * Opens a pseudoterminal pair, grants and unlocks access to it, and runs the
   * given command on the slave pseudoterminal.
   * 
   * @param command
   *          the command to execute in the pseudoterminal, cannot be
   *          <code>null</code>;
   * @param arguments
   *          the optional command line arguments of the command, may be
   *          <code>null</code>.
   * @return a {@link Pty} instance, never <code>null</code>.
   * @throws JPtyException
   *           in case opening the pseudoterminal failed.
   */
  public static Pty execInPTY( String command, String[] arguments ) throws JPtyException
  {
    return execInPTY( command, arguments, null );
  }

  /**
   * Opens a pseudoterminal pair, grants and unlocks access to it, and runs the
   * given command on the slave pseudoterminal.
   * 
   * @param command
   *          the command to execute in the pseudoterminal, cannot be
   *          <code>null</code>;
   * @param arguments
   *          the optional command line arguments of the command, may be
   *          <code>null</code>;
   * @param environment
   *          the environment for the command, may be <code>null</code>.
   * @return a {@link Pty} instance, never <code>null</code>.
   * @throws JPtyException
   *           in case opening the pseudoterminal failed.
   */
  public static Pty execInPTY( String command, String[] arguments, String[] environment ) throws JPtyException
  {
    return execInPTY( command, arguments, environment, null, null );
  }

  /**
   * Opens a pseudoterminal pair, grants and unlocks access to it, and runs the
   * given command on the slave pseudoterminal.
   * 
   * @param command
   *          the command to execute in the pseudoterminal, cannot be
   *          <code>null</code>;
   * @param arguments
   *          the optional command line arguments of the command, may be
   *          <code>null</code>;
   * @param environment
   *          the environment for the command, may be <code>null</code>;
   * @param termios
   *          the initial termios structure, may be <code>null</code>;
   * @param winsize
   *          the initial winsize structure, may be <code>null</code>;
   * @return a {@link Pty} instance, never <code>null</code>.
   * @throws IllegalArgumentException
   *           in case the given command was <code>null</code>;
   * @throws JPtyException
   *           in case opening the pseudoterminal failed.
   */
  public static Pty execInPTY( String command, String[] arguments, String[] environment, Termios termios,
      WinSize winsize ) throws JPtyException
  {
    if ( command == null )
    {
      throw new IllegalArgumentException( "Invalid command line!" );
    }

    int[] master = new int[] { -1 };
    byte[] name = new byte[128];
    int pid;

    if ( ( pid = m_jpty.forkpty( master, name, termios, winsize ) ) < 0 )
    {
      throw new JPtyException( "Failed to open PTY!", errno() );
    }

    if ( pid == 0 )
    {
      // Child...
      final String[] argv = processArgv( command, arguments );

      if ( winsize != null )
      {
        if ( setWinSize( 0 /* stdin */, winsize ) < 0 )
        {
          throw new JPtyException( "Failed to set window size!", errno() );
        }
      }

      // Replaces this child process with the given command(line)...
      execve( command, argv, environment );

      // Actually, we should never come here if the command execution went OK...
      throw new JPtyException( "Child failed to replace process!", errno() );
    }

    int masterFD = master[0];
    // Just a safety measure...
    if ( masterFD < 0 )
    {
      throw new JPtyException( "Failed to fork PTY!", -1 );
    }

    if ( fcntl( masterFD, F_SETFL, 0 ) < 0 )
    {
      throw new JPtyException( "Failed to set flags for master PTY!", errno() );
    }

    return new Pty( masterFD, pid );
  }

  /**
   * Reports the window size for the given file descriptor.
   * 
   * @param fd
   *          the file descriptor to report the window size for;
   * @param ws
   *          the window size to place the results in.
   * @return 0 upon success, or -1 upon failure.
   */
  public static int getWinSize( int fd, WinSize ws )
  {
    return m_jpty.getWinSize( fd, ws );
  }

  /**
   * Tests whether the process with the given process ID is alive or terminated.
   * 
   * @param pid
   *          the process-ID to test.
   * @return <code>true</code> if the process with the given process ID is
   *         alive, <code>false</code> if it is terminated.
   */
  public static boolean isProcessAlive( int pid )
  {
    int[] stat = { -1 };
    int result = JPty.waitpid( pid, stat, WNOHANG );
    return ( result == 0 ) && ( stat[0] < 0 );
  }

  /**
   * Sets the window size for the given file descriptor.
   * 
   * @param fd
   *          the file descriptor to set the window size for;
   * @param ws
   *          the new window size to set.
   * @return 0 upon success, or -1 upon failure.
   */
  public static int setWinSize( int fd, WinSize ws )
  {
    return m_jpty.setWinSize( fd, ws );
  }

  /**
   * Terminates or signals the process with the given PID.
   * 
   * @param pid
   *          the process ID to terminate or signal;
   * @param signal
   *          the signal number to send, for example, 9 to terminate the
   *          process.
   * @return a value of <tt>0</tt> upon success, or a non-zero value in case of
   *         an error.
   */
  public static int signal( int pid, int signal )
  {
    return m_jpty.kill( pid, signal );
  }

  /**
   * Blocks and waits until the given PID either terminates, or receives a
   * signal.
   * 
   * @param pid
   *          the process ID to wait for;
   * @param stat
   *          an array of 1 integer in which the status of the process is
   *          stored;
   * @param options
   *          the bit mask with options.
   */
  public static int waitpid( int pid, int[] stat, int options )
  {
    return m_jpty.waitpid( pid, stat, options );
  }

  /**
   * Returns the last known error.
   * 
   * @return the last error number from the native system.
   */
  static int errno()
  {
    return Native.getLastError();
  }

  /**
   * Not public as this method <em>replaces</em> the current process and
   * therefore should be used with caution.
   * 
   * @param command
   *          the command to execute.
   */
  private static int execve( String command, String[] argv, String[] env )
  {
    return m_jpty.execve( command, argv, env );
  }

  /**
   * Processes the given command + arguments and crafts a valid array of
   * arguments as expected by {@link #execve(String, String[], String[])}.
   * 
   * @param command
   *          the command to run, cannot be <code>null</code>;
   * @param arguments
   *          the command line arguments, can be <code>null</code>.
   * @return a new arguments array, never <code>null</code>.
   */
  private static String[] processArgv( String command, String[] arguments )
  {
    final String[] argv;
    if ( arguments == null )
    {
      argv = new String[] { command };
    }
    else
    {
      if ( !command.equals( arguments[0] ) )
      {
        argv = new String[arguments.length + 1];
        argv[0] = command;
        System.arraycopy( arguments, 0, argv, 1, arguments.length );
      }
      else
      {
        argv = Arrays.copyOf( arguments, arguments.length );
      }
    }
    return argv;
  }
}
