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
package jpty.solaris;


import static jtermios.JTermios.O_RDWR;
import static jtermios.JTermios.TCSANOW;
import static jtermios.JTermios.close;
import static jtermios.JTermios.open;
import static jtermios.JTermios.tcsetattr;
import jpty.JPty;
import jpty.JPty.JPtyInterface;
import jpty.WinSize;

import com.sun.jna.Native;
import com.sun.jna.StringArray;
import com.sun.jna.Structure;


/**
 * @author jawi
 */
public class JPtyImpl implements JPtyInterface
{
  // INNER TYPES

  public static class winsize extends Structure
  {
    public short ws_row;
    public short ws_col;
    public short ws_xpixel;
    public short ws_ypixel;

    public winsize()
    {
    }

    public winsize( WinSize ws )
    {
      ws_row = ws.ws_row;
      ws_col = ws.ws_col;
      ws_xpixel = ws.ws_xpixel;
      ws_ypixel = ws.ws_ypixel;
    }

    public void update( WinSize winSize )
    {
      winSize.ws_col = ws_col;
      winSize.ws_row = ws_row;
      winSize.ws_xpixel = ws_xpixel;
      winSize.ws_ypixel = ws_ypixel;
    }
  }

  public interface Solaris_C_lib extends com.sun.jna.Library
  {
    public int dup2( int filedes, int filedes2 );

    public int execve( String command, StringArray argv, StringArray env );

    public int fork();

    public int grantpt( int fd );

    public int ioctl( int fd, int cmd, String arg );

    public int ioctl( int fd, int cmd, winsize arg );

    public int openpt( int flags );

    public String ptsname( int fd );

    public int unlockpt( int fd );

    public int waitpid( int pid, int[] stat, int options );
  }

  // CONSTANTS

  // stropts.h
  private static final int _STR = ( 'S' << 8 );
  private static final int I_PUSH = ( _STR | 2 );

  // unistd.h
  private static final int STDIN_FILENO = 0;
  private static final int STDOUT_FILENO = 1;
  private static final int STDERR_FILENO = 2;

  // termios.h
  private static final int _TIOC = ( 'T' << 8 );
  private static final int TIOCGWINSZ = ( _TIOC | 104 );
  private static final int TIOCSWINSZ = ( _TIOC | 103 );


  private static final String DEV_PTMX = "/dev/ptmx";

  // VARIABLES

  private static Solaris_C_lib m_Clib = ( Solaris_C_lib )Native.loadLibrary( "c", Solaris_C_lib.class );
  
  // CONSTRUCTORS
  
  /**
   * Creates a new {@link JPtyImpl} instance. 
   */
  public JPtyImpl()
  {
    JPty.ONLCR = 0x04;

    JPty.VINTR = 0;
    JPty.VQUIT = 1;
    JPty.VERASE = 2;
    JPty.VKILL = 3;
    JPty.VSUSP = 10;
    JPty.VREPRINT = 12;
    JPty.VWERASE = 14;

    JPty.ECHOCTL = 0x1000;
    JPty.ECHOKE = 0x4000;
  }

  // METHODS

  @Override
  public int execve( String command, String[] argv, String[] env )
  {
    StringArray argvp = ( argv == null ) ? new StringArray( new String[] { command } ) : new StringArray( argv );
    StringArray envp = ( env == null ) ? new StringArray( new String[0] ) : new StringArray( env );
    return m_Clib.execve( command, argvp, envp );
  }

  /**
   * Implementation based on <http://bugs.mysql.com/bug.php?id=22429>.
   */
  @Override
  public int forkpty( int[] amaster, byte[] name, jtermios.Termios term, WinSize win )
  {
    int fdMaster = open( DEV_PTMX, O_RDWR );
    if ( fdMaster < 0 )
    {
      return -1;
    }

    if ( grantpt( fdMaster ) < 0 )
    {
      close( fdMaster );
      return -1;
    }

    if ( unlockpt( fdMaster ) < 0 )
    {
      close( fdMaster );
      return -1;
    }

    String slaveName = ptsname( fdMaster );
    if ( slaveName == null )
    {
      close( fdMaster );
      return -1;
    }

    int fdSlave = open( slaveName, O_RDWR );
    if ( fdSlave < 0 )
    {
      close( fdMaster );
      return -1;
    }

    if ( ioctl( fdSlave, I_PUSH, "ptem" ) < 0 || // pseudo-terminal hardware
                                                 // emulation module,
        ioctl( fdSlave, I_PUSH, "ldterm" ) < 0 || // standard terminal line
                                                  // discipline, and
        ioctl( fdSlave, I_PUSH, "ttcompat" ) < 0 )
    { // not sure but both xterm and DtTerm do it.
      close( fdSlave );
      close( fdMaster );
      return -1;
    }

    if ( amaster != null )
    {
      amaster[0] = fdMaster;
    }

    if ( name != null )
    {
      byte[] b = slaveName.getBytes();
      System.arraycopy( b, 0, name, 0, Math.min( b.length, name.length ) );
    }

    int pid = fork();
    switch ( pid )
    {
      case -1:
        /* Error */
        return -1;
      case 0:
        /* Child */
        tcsetattr( fdSlave, TCSANOW, term );
        setWinSize( fdSlave, win );
        close( fdMaster );
        dup2( fdSlave, STDIN_FILENO );
        dup2( fdSlave, STDOUT_FILENO );
        dup2( fdSlave, STDERR_FILENO );
        return 0;
      default:
        /* Parent */
        close( fdSlave );
        return pid;
    }
  }

  @Override
  public int getWinSize( int fd, WinSize winSize )
  {
    int r;

    winsize ws = new winsize();
    if ( ( r = m_Clib.ioctl( fd, TIOCGWINSZ, ws ) ) < 0 )
    {
      return r;
    }
    ws.update( winSize );

    return r;
  }

  @Override
  public int setWinSize( int fd, WinSize winSize )
  {
    winsize ws = new winsize( winSize );
    return m_Clib.ioctl( fd, TIOCSWINSZ, ws );
  }

  @Override
  public int waitpid( int pid, int[] stat, int options )
  {
    return m_Clib.waitpid( pid, stat, options );
  }

  private int dup2( int filedes, int filedes2 )
  {
    return m_Clib.dup2( filedes, filedes2 );
  }

  private int fork()
  {
    return m_Clib.fork();
  }

  private int grantpt( int fd )
  {
    return m_Clib.grantpt( fd );
  }

  private int ioctl( int fd, int cmd, String arg )
  {
    return m_Clib.ioctl( fd, cmd, arg );
  }

  private String ptsname( int fd )
  {
    return m_Clib.ptsname( fd );
  }

  private int unlockpt( int fd )
  {
    return m_Clib.unlockpt( fd );
  }
}
