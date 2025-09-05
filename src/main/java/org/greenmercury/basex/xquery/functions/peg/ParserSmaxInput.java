package org.greenmercury.basex.xquery.functions.peg;

import org.greenmercury.smax.SmaxElement;
import org.waxeye.input.IParserInput;

/**
 * A class to represent the buffer to hold the input string, with support for extended data.
 *
 * @author Orlando Hill
 * @author Nico Verwer
 */
public class ParserSmaxInput implements IParserInput<SmaxElement>
{
  /** The internal buffer. */
  private final char[] input;

  /** The current position in the buffer. */
  private int position;

  /** The extended data associated with the input is the last visited element.
   * It is {@code null} when no element has been visited yet, and {@code SmaxElement} when there are no more elements to visit.
   */
  private SmaxElement extendedData;

  /** The size of the buffer. */
  private final int inputSize;

  /** A SmaxElement that indicates that there is no next element. It has itself as its next element.
   */
  public final SmaxElement endElement = new SmaxElement("END_ELEMENT") {
      @Override
      public SmaxElement getNextElement() {
          return this; // Points to itself.
      }
      @Override
      public String toString() {
          return "END_ELEMENT";
      }
  };

  /**
   * Creates a new ParserSmaxInput for the given char[]. The position starts at index 0, the extendedData at null.
   *
   * @param input The char[] to use for our buffer.
   */
  public ParserSmaxInput(final char[] input)
  {
      System.out.println("-- Creating new ParserSmaxInput");
      this.input = input;
      this.position = 0;
      this.inputSize = input.length;
      this.extendedData = null;
      assert invariants();
  }

  /**
   * Checks the invariants of the object.
   *
   * @return <code>true</code>.
   */
  private boolean invariants()
  {
      assert input != null;
      assert position >= 0 && position <= inputSize;
      return true;
  }

  /** {@inheritDoc} */
  @Override
  public int consume()
  {
      if (position < inputSize)
      {
          System.out.println("-- Reset extended data to null in consume()");
          this.extendedData = null; // Reset the markup position.
          return input[position++];
      }
      return EOF;
  }

  /** {@inheritDoc} */
  @Override
  public int peek()
  {
      if (position < inputSize)
      {
          return input[position];
      }
      return EOF;
  }

  /** {@inheritDoc} */
  @Override
  public int getPosition()
  {
      return position;
  }

  /**
   * Returns the inputSize.
   *
   * @return Returns the inputSize.
   */
  public int getInputSize()
  {
      return inputSize;
  }

  /**
   * Sets the position of the input buffer to the given value. If the value
   * given is less than 0 then the position is set to 0.
   *
   * @param position The position to set.
   */
  @Override
  public void setPosition(final int position)
  {
      if (position == this.position) {
          System.out.println("-- No change in setPosition("+position+")");
          return; // No change.
      }
      System.out.println("-- Reset extended data to null in setPosition("+position+")");
      this.extendedData = null; // Reset the markup position.
      if (position < 0)
      {
          this.position = 0;
      }
      else
      {
          this.position = position;
      }
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(final Object object)
  {
      if (this == object)
      {
          return true;
      }
      if (object != null && object.getClass() == this.getClass())
      {
          final ParserSmaxInput b = (ParserSmaxInput) object;
          if (input != b.input || position != b.position)
          {
              return false;
          }
          return true;
      }
      return false;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
      final int start = 17;
      final int mult = 37;
      int result = start;
      if (input != null)
      {
          for (char c : input)
          {
              result = mult * (result + c);
          }
      }
      result = mult * result + position + extendedData.hashCode();
      return Math.abs(result);
  }

  @Override
  public SmaxElement getExtendedData()
  {
    return extendedData;
  }

  @Override
  public void setExtendedData(SmaxElement extendedData)
  {
      System.out.println("-- setExtendedData: " + (extendedData == null ? "null" : extendedData.toString()));
      this.extendedData = extendedData;
  }

  public SmaxElement getNextElement(SmaxElement element) {
    if (element == null) {
        return null;
    }
    SmaxElement nextElement = element.getNextElement();
    if (nextElement == null) {
        return endElement;
    }
    return nextElement;
  }

}
