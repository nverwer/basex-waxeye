package org.greenmercury.basex.xquery.functions.peg;

/**
 * A super-minimal logger interface, which should be easy to implement in any specific environment.
 */
public interface Logger
{
  public void info(String message);
  public void warning(String message);
  public void error(String message);
}
