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
package jpty.windows;


import jpty.JPty.JPtyInterface;
import jpty.WinSize;
import jtermios.Termios;


/**
 *
 */
public class JPtyImpl implements JPtyInterface
{

  @Override
  public int execve( String command, String[] argv, String[] env )
  {
    // TODO Auto-generated method stub
    return -1;
  }

  @Override
  public int forkpty( int[] amaster, byte[] name, Termios termp, WinSize winp )
  {
    // TODO Auto-generated method stub
    return -1;
  }

  @Override
  public int getWinSize( int fd, WinSize ws )
  {
    // TODO Auto-generated method stub
    return -1;
  }

  @Override
  public int setWinSize( int fd, WinSize ws )
  {
    // TODO Auto-generated method stub
    return -1;
  }

  @Override
  public int waitpid( int pid, int[] stat, int options )
  {
    // TODO Auto-generated method stub
    return -1;
  }

  @Override
  public int kill( int pid, int sig )
  {
    // TODO Auto-generated method stub
    return 0;
  }
}
