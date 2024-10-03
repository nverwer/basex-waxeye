package org.greenmercury.basex.xquery.functions.peg;

import java.io.UnsupportedEncodingException;
import java.nio.CharBuffer;
import java.text.Normalizer;

public class StringUtils {

  // Private constructor, cannot be instantiated.
  private StringUtils() { }

  /* Accented characters tables, mapping Unicode diacritics to ASCII.
   * Note that \u04D0 - \u04D7 (breve) are Cyrillic characters, and their plain ASCII equivalents are not Cyrillic. Many Cyrillics are missing, please add when needed.
   */

  private static final String UNICODE_DIACRITICAL =
      "\u00C0\u00E0\u00C8\u00E8\u00CC\u00EC\u00D2\u00F2\u00D9\u00F9"                                                                                 // grave
    + "\u00C1\u00E1\u00C9\u00E9\u00CD\u00ED\u00D3\u00F3\u00DA\u00FA\u00DD\u00FD"                                                                     // acute
    + "\u00C2\u00E2\u00CA\u00EA\u00CE\u00EE\u00D4\u00F4\u00DB\u00FB\u0176\u0177"                                                                     // circumflex
    + "\u00C3\u00E3\u00D5\u00F5\u00D1\u00F1"                                                                                                         // tilde
    + "\u00C4\u00E4\u00CB\u00EB\u00CF\u00EF\u00D6\u00F6\u00DC\u00FC\u0178\u00FF"                                                                     // umlaut
    + "\u00C5\u00E5"                                                                                                                                 // ring
    + "\u00C7\u00E7\u0122\u0123\u0136\u0137\u013B\u013C\u0145\u0146\u0156\u0157\u015E\u015F\u0162\u0163\u0228\u0229\u1E10\u1E11\u1E28\u1E29"         // cedilla
    + "\u0150\u0151\u0170\u0171"                                                                                                                     // double acute
    + "\u0102\u0103\u0114\u0115\u011E\u011F\u012C\u012D\u014E\u014F\u016C\u016D\u04D0\u04D1\u04D6\u04D7\u1E1C\u1E1D\u1EB6\u1EB7"                     // breve
    + "\u00c5\u00e5\u00d8\u00f8"                                                                                                                     // Scandinavian
    + "\u008A\u008E\u009A\u009E\u009F"                                                                                                               // special
    ;

  private static final String PLAIN_ASCII_DIACRITICAL =
      "AaEeIiOoUu"             // grave
    + "AaEeIiOoUuYy"           // acute
    + "AaEeIiOoUuYy"           // circumflex
    + "AaOoNn"                 // tilde
    + "AaEeIiOoUuYy"           // umlaut
    + "Aa"                     // ring
    + "CcGgKkLlNnRrSsTtEeDdHh" // cedilla
    + "OoUu"                   // double acute
    + "AaEeGgIiOoUuAaIiEeAa"   // breve
    + "AaOo"                   // Scandinavian
    + "SZszY"                  // special
    ;

  /* Punctuation characters tables. */

  // The ` character is often used instead of ', and is therefore also normalized.
  private static final String UNICODE_PUNCTUATION =
      "\u0060\u0091\u0092\u2018\u2032\u00B4\u2019"              // '
    + "\u0093\u0094\u201C\u201D"                                // "
    + "\u0096\u0097\u2010\u2011\u2012\u2013\u2014\u2015\u2212"  // -
    + "\u00A0\u1680\u180E\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u200B\u202F\u205F\u3000\uFEFF" // spaces
    ;

  private static final String PLAIN_ASCII_PUNCTUATION =
      "'''''''"
    + "\"\"\"\""
    + "---------"
    + "                   "
    ;

  private static final String POST_NFKD_UNICODE =
      "\u2044";

  private static final String POST_NFKD_ASCII =
      "/";

  /* Ligatures characters table for ligatures that are not handled by NFKD normalization. Only convert these when the string length may change. */
  private static final String LIGATURES =
      "\u008C\u009C\u0152\u0153\u00C6\u00E6'";

  private static final String[] SPLIT_LIGATURES = new String[] {
      "OE", "oe", "OE", "oe", "AE", "ae"
  };


  /**
   * Check if a character is in the low (7-bit) ASCII range, and not a punctuation character.
   * @param c
   * @return if the character is in the low ASCII range
   */
  public static boolean isLowAsciiWithoutPunctuation(char c) {
    return (Character.isLetterOrDigit(c) || c == ' ') && c >= 0x20 && c < 0x7F;
  }

  /**
   * Convert all characters in a string to ASCII codes 0x20 - 0x7E and remove punctuation.
   * All white space will be normalized to normal space (0x20).
   * Only letters and digits and normal spaces are kept, other characters are removed.
   * Therefore, the length of the string may change.
   * @param s the inputSource string
   * @return a string derived from {@code s}, with only low ASCII characters.
   */
  public static CharSequence convertToLowAsciiWithoutPunctuation(CharSequence s) {
    if (s == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      int pos = UNICODE_DIACRITICAL.indexOf(c);
      if (Character.isWhitespace(c)) {
        sb.append(' ');
      } else if (pos >= 0) {
        sb.append(PLAIN_ASCII_DIACRITICAL.charAt(pos));
      } else if (isLowAsciiWithoutPunctuation(c)) {
        sb.append(c);
      }
    }
    return sb;
  }

  /**
   * Convert all characters in a string to ASCII codes 0x20 - 0x7E.
   * The number of characters in the string will not change. Therefore we cannot use unicode NFKD normalization.
   * All white space will be normalized to normal space (0x20).
   * Characters that have no low ASCII equivalent are replaced by defaultChar.
   * @param s the inputSource string
   * @param defaultChar
   * @return a string corresponding to {@code s}, with only low ASCII characters.
   */
  public static CharSequence convertToLowAsciiOneToOne(CharSequence s, char defaultChar) {
    if (s == null) {
      return null;
    }
    int n = s.length();
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      int pos;
      if (Character.isWhitespace(c)) {
        sb.append(' ');
      } else if ((pos = UNICODE_DIACRITICAL.indexOf(c)) >= 0) {
        sb.append(PLAIN_ASCII_DIACRITICAL.charAt(pos));
      } else if ((pos = UNICODE_PUNCTUATION.indexOf(c)) >= 0) {
        sb.append(PLAIN_ASCII_PUNCTUATION.charAt(pos));
      } else if ((pos = POST_NFKD_UNICODE.indexOf(c)) >= 0) {
        sb.append(POST_NFKD_ASCII.charAt(pos));
      } else if (c >= 0x20 && c < 0x80) {
        sb.append(c);
      } else {
        sb.append(defaultChar);
      }
    }
    return sb;
  }

  /**
   * Normalize a string and keep the number of characters the same.
   * For example, the ellipsis symbol is not expanded into '...'.
   * Converts all characters in a string to ASCII codes 0x20 - 0x7E.
   * All white space will be normalized to normal space (0x20).
   * This method uses the '\u0080' character as a substitute for characters that have no normalized equivalent.
   * When put into the trie, '\u0080' will be thrown away.
   * @param s the inputSource string
   * @return a normalized version of {@code s} with the same number of characters.
   */
  public static CharSequence normalizeOneToOne(CharSequence s) {
    return convertToLowAsciiOneToOne(s, '\u0080');
  }

  /**
   * Normalize a character to ASCII
   * @param c the inputSource character
   * @return the normalized character.
   */
  public static char normalizeOneToOne(char c) {
    return normalizeOneToOne(Character.toString(c)).charAt(0);
  }

  /**
   * Normalize spaces by trimming spaces at the beginning and end of {@code s},
   * and coalescing multiple spaces within {@code s}.
   * @param s
   * @return The content of {@code s} with normalized spaces.
   */
  public static CharSequence normalizeSpaces(CharSequence s) {
    if (s == null) {
      return null;
    }
    boolean inSpace = false;
    StringBuilder sb = new StringBuilder();
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        inSpace = true;
      } else {
        if (inSpace && sb.length() > 0) {
          sb.append(' ');
        }
        inSpace = false;
        sb.append(c);
      }
    }
    return sb;
  }

  /**
   * Normalize ligatures in the ASCII character set (0x20 - 0xFF) by expanding them into their constituent characters.
   * As a bonus, we also do some post-NFKD normalization (should be separate, but this is faster).
   * @param s
   * @return The content of {@code s} with expanded ligatures.
   */
  public static CharSequence expandASCIILigatures(CharSequence s) {
    if (s == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      int ligatureIndex = LIGATURES.indexOf(c);
      if (ligatureIndex >= 0) {
        sb.append(SPLIT_LIGATURES[ligatureIndex]);
      } else {
        int postNFKDNormalizeIndex = POST_NFKD_UNICODE.indexOf(c);
        if (postNFKDNormalizeIndex >= 0) {
          sb.append(POST_NFKD_ASCII.charAt(postNFKDNormalizeIndex));
        } else {
          sb.append(c);
        }
      }
    }
    return sb;

  }

  /**
   * Normalize a string to ASCII. The string length may change.
   * Inspired by [http://stackoverflow.com/a/2097224/1021892].
   * The getBytes("ascii") ensures only ASCII characters are retained.
   * For ligatures, see [http://stackoverflow.com/questions/7171377/separating-unicode-ligature-characters].
   * It seems that NFKD is most useful for us, as it splits off most diacritical marks. [http://www.unicode.org/reports/tr15/]
   * @param s the inputSource string
   * @return a normalized string derived from {@code s}.
   */
  public static CharSequence normalizeAsciiExpanding(CharSequence s) {
    try {
      String sn = Normalizer.normalize(s, Normalizer.Form.NFKD);
      // Remove diacritical marks after NFKD. The getBytes("ascii") will lose Cyrillic, which may be bad.
      String sr = new String(sn.replaceAll("[\\p{IsMn}\\p{IsLm}\\p{IsSk}]+", "").getBytes("ascii"), "ascii");
      CharSequence sre = expandASCIILigatures(sr);
      return sre;
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e); // This should never happen, unless "ascii" ceases to exist.
    }
  }

  /**
   * Normalize a character to ASCII, possibly expanding into multiple characters.
   * @param c the inputSource character.
   * @return a string resulting from the normalization of c
   */
  public static CharSequence normalizeAsciiExpanding(char c) {
    return normalizeAsciiExpanding(Character.toString(c));
  }

  /**
   * An efficient way to get a char[] from a CharSequence.
   * @param cs a CharSequence.
   * @return the corresponding char array.
   */
  public static char[] charSequenceToCharArray(CharSequence cs) {
    CharBuffer cb = CharBuffer.wrap(cs);
    char[] chars = new char[cb.remaining()];
    cb.get(chars);
    return chars;
  }


}
