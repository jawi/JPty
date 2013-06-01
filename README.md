# JPty - A small PTY interface for Java.

This library provides a simply interface for using pseudo terminals (PTYs) in
Java, allowing one to communicate with processes as if they are running in a
"real" terminal.

## Dependencies

This library depends on JTermios, part of the PureJavacomm library found at
<https://github.com/nyholku/purejavacomm>. A binary release of this library,
along with its dependency JNA, is made part of this repository, and can be 
found in the lib-directory.

## Usage

Using this library is relatively easy:

    // The command to run in a PTY...
    String[] cmd = { "/bin/sh", "-l" };
    // The initial environment to pass to the PTY child process...
    String[] env = { "TERM=xterm" };

    Pty pty = JPty.execInPTY( cmd[0], cmd, env );

    OutputStream os = pty.getOutputStream();
    InputStream is = pty.getInputStream();
    
    // ... work with the streams ...
    
    // wait until the PTY child process terminates...
    int result = pty.waitFor();
    
    // free up resources.
    pty.close();

The operating systems currently supported by JPty are: FreeBSD, Linux, OSX and
Solaris.  
**Note that this library is not yet fully tested on all platforms.**

## Changes

    0.0.5 | 15-02-2013 | Initial version.
    0.1.0 | 01-06-2013 | Fixed an issue with prematurely closing a PTY while
          |            | the child process is still running. Thanks to traff
          |            | for reporting this.

## Author

This library is written by J.W. Janssen, <j.w.janssen@lxtreme.nl>.

## License

The code in this library is licensed under Apache Software License, version 
2.0 and can be found online at: <http://www.apache.org/licenses/LICENSE-2.0>.

