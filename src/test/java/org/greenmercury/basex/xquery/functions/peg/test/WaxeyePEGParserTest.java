package org.greenmercury.basex.xquery.functions.peg.test;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.basex.query.QueryException;
import org.greenmercury.basex.xquery.functions.peg.Logger;
import org.greenmercury.basex.xquery.functions.peg.WaxeyePEGParser;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.convert.XmlString;
import org.junit.jupiter.api.Test;

public class WaxeyePEGParserTest
{

  private static final org.junit.platform.commons.logging.Logger junitLogger = org.junit.platform.commons.logging.LoggerFactory.getLogger(WaxeyePEGParser.class);
  private static final Logger logger = new Logger() {
    @Override
    public void info(String message)
    {
      junitLogger.info(() -> message);
    }
    @Override
    public void warning(String message)
    {
      junitLogger.warn(() -> message);
    }
    @Override
    public void error(String message)
    {
      junitLogger.error(() -> message);
    }
  };

  private String simplify(SmaxDocument document) throws Exception {
    return XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
  }

  
  private final String calculatorGrammar =
    "sum   <- prod *(ws [+-] ws prod)\n" +
    "prod  <- unary *(ws [*/] ws unary)\n" +
    "unary <= '-' ws unary\n" +
    "       | :'(' ws sum ws :')'\n" +
    "       | num\n" +
    "num   <- +[0-9] ?('.' +[0-9])\n" +
    "ws    <: *[ \\t\\n\\r]";

  private final String abcPalindromeGrammar =
    "palindrome <- 'a' :?palindrome 'a' | 'b' ?:palindrome 'b' | 'c' ?:palindrome 'c' | 'a' | 'b' | 'c' \n";

  
  @Test
  void test_Grammar_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c>1 + 1</c>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<c><Sum><Prod><Num>1</Num></Prod> + <Prod><Num>1</Num></Prod></Sum></c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_Grammar_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c>1 + 2*3  +  (1 + 2) * 3</c>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<c><Sum><Prod><Num>1</Num></Prod> + <Prod><Num>2</Num>*<Num>3</Num></Prod>  +  "+
      "<Prod>(<Sum><Prod><Num>1</Num></Prod> + <Prod><Num>2</Num></Prod></Sum>) * <Num>3</Num></Prod></Sum></c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_Grammar_3() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c><int>1</int><plus>+</plus><int>1</int></c>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<c><Sum><int><Prod><Num>1</Num></Prod></int><plus>+</plus><int><Prod><Num>1</Num></Prod></int></Sum></c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_CompleteMatch_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c>what is 1 + 1?</c>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<c>what is <Sum><Prod><Num>1</Num></Prod> + <Prod><Num>1</Num></Prod></Sum>?</c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_CompleteMatch_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("complete-match", "true");
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c>what is 1 + 1?</c>");
    Exception exception = assertThrows(QueryException.class, () -> parser.scan(document));
    assertTrue(exception.getMessage().contains("failed to match"));
  }

  @Test
  void test_CompleteMatch_3() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("complete-match", "true");
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c>1 + 1</c>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<c><Sum><Prod><Num>1</Num></Prod> + <Prod><Num>1</Num></Prod></Sum></c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_Namespaces_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c:c xmlns:c=\"calculator\">1 + 1</c:c>");
    parser.scan(document);
    String output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "");
    String expectedOutput = "<c:c xmlns:c=\"calculator\"><Sum><Prod><Num>1</Num></Prod> + <Prod><Num>1</Num></Prod></Sum></c:c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_Namespaces_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c:c xmlns:c=\"calculator\" xmlns:op=\"operator\">1 <op:plus>+</op:plus> 1</c:c>");
    parser.scan(document);
    String output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "");
    String expectedOutput = "<c:c xmlns:c=\"calculator\"><Sum><Prod><Num>1</Num></Prod> <op:plus xmlns:op=\"operator\">+</op:plus> <Prod><Num>1</Num></Prod></Sum></c:c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_Namespaces_3() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<c:c xmlns:c=\"calculator\" xmlns:op=\"operator\">1 <operator op:id=\"plus\">+</operator> 1</c:c>");
    parser.scan(document);
    String output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "");
    String expectedOutput = "<c:c xmlns:c=\"calculator\"><Sum><Prod><Num>1</Num></Prod> <operator xmlns:op=\"operator\" op:id=\"plus\">+</operator> <Prod><Num>1</Num></Prod></Sum></c:c>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_AdjacentMatches_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("match-whole-words", "true");
    WaxeyePEGParser parser = new WaxeyePEGParser(abcPalindromeGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<p>palindrome abcba, abba?</p>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<p>palindrome <Palindrome>abcba</Palindrome>, <Palindrome>abba</Palindrome>?</p>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_AdjacentMatches_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("match-whole-words", "true");
    options.put("adjacent-matches", "true");
    WaxeyePEGParser parser = new WaxeyePEGParser(abcPalindromeGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<p>palindrome abcba, abaaba?</p>");
    Exception exception = assertThrows(QueryException.class, () -> parser.scan(document));
    assertTrue(exception.getMessage().contains("failed to match"));
  }

  @Test
  void test_AdjacentMatches_3() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("adjacent-matches", "true");
    WaxeyePEGParser parser = new WaxeyePEGParser(abcPalindromeGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<p>abcbaabaaba</p>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<p><Palindrome>abcba</Palindrome><Palindrome>abaaba</Palindrome></p>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_MatchWholeWords_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(abcPalindromeGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<p>[abcbaabba]</p>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<p>[<Palindrome>abcba</Palindrome><Palindrome>abba</Palindrome>]</p>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_MatchWholeWords_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("match-whole-words", "true");
    WaxeyePEGParser parser = new WaxeyePEGParser(abcPalindromeGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<p>[abcbaabba]</p>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<p>[abcbaabba]</p>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_Grammar_File_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    ClassLoader classLoader = getClass().getClassLoader();
    URL grammar = classLoader.getResource("palindrome.waxeye");
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<p>[abcbaabba]</p>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<p>[<Palindrome>abcba</Palindrome><Palindrome>abba</Palindrome>]</p>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_Grammar_File_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("modular", "true");
    ClassLoader classLoader = getClass().getClassLoader();
    URL grammar = classLoader.getResource("modular.waxeye");
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<p>[abcba313abba]</p>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<p>[<Palindrome><Abc_palindrome>abcba</Abc_palindrome></Palindrome><Palindrome><Num_palindrome>313</Num_palindrome></Palindrome><Palindrome><Abc_palindrome>abba</Abc_palindrome></Palindrome>]</p>";
    assertEquals(expectedOutput, output);
  }

}
