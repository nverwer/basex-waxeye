package org.greenmercury.basex.xquery.functions.peg.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.greenmercury.basex.xquery.functions.peg.Logger;
import org.greenmercury.basex.xquery.functions.peg.WaxeyePEGParser;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.convert.XmlString;
import org.junit.jupiter.api.Test;

public class WaxeyePEGParserPreParsedNonTerminalTest
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
    // Serialize the given document. Remove processing instructions and namespace declarations.
    return XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
  }


  private final String calculatorGrammar =
    "sum   <- prod *(ws [+-] ws prod)\n" +
    "prod  <- unary *(ws [*/] ws unary)\n" +
    "unary <= '-' ws unary\n" +
    "       | :'(' ws sum ws :')'\n" +
    "       | num\n" +
    "num   <= <number>\n" +
    "ws    <: *[ \\t\\n\\r]";



  @Test
  void test_calc_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<r><number>1</number> + <number>23</number></r>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><Sum><Prod><number>1</number></Prod> + <Prod><number>23</number></Prod></Sum></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_nesting_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- Y\nY <- <z>\n";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<r><z>...</z></r>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><X><Y><z>...</z></Y></X></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_empty_elements_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- <a> +<b> <a>";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml =
      "<r>"+
      "<a/><b/><b/><a/>"+
      "</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><X><a/><b/><b/><a/></X></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_empty_elements_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- <a> +<b> <c>";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml =
      "<r>"+
      "<a/><b/><b/><c/>"+
      "</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><X><a/><b/><b/><c/></X></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_empty_elements_3() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- <a> +<b> <c>";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml =
      "<r>"+
      "<a/><b/>"+
      "</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><a/><b/></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_empty_elements_4() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- <a> +<b> <c>";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml =
      "<r>"+
      "<b/><c/>"+
      "</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><b/><c/></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_empty_elements_5() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- <a> +<b> <a>";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml =
      "<r>"+
      "<a><b/></a>"+ // no match, b is nested and skipped by <a>
      "</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><X><a><b/></a></X></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_empty_elements_6() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- <a> +<b> <a>";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml =
      "<r>"+
      "<a/><c/><b/><c/><a/>"+ // match, <c/> elements are transparent
      "</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><X><a/><c/><b/><c/><a/></X></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_non_empty_elements_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar = "X <- '(' <a> +<b> <a> ')'";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml =
      "<r>"+
      "(<a>a1</a><b>b1</b><b/><a>a2</a>) (match)"+
      "(<a>a1</a>intervening<b>b1</b><b/><a>a2</a>) (no match)"+
      "</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    // Count the number of <X> elements in the output.
    long countX = Pattern.compile("<X>").matcher(output).results().count();
    assertEquals(1, countX, "Expected exactly one <X> element in the output, but found "+countX+" in: " + output);
  }


  @Test
  void test_alternatives_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar =
      "X <- 'a' <a> 'b'\n"+
      "   | 'a' <a> 'c'\n";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml = "<r>a<a/>c</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><X>a<a/>c</X></r>";
    assertEquals(expectedOutput, output);
  }


  @Test
  void test_alternatives_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    String grammar =
      "X <- 'a' <a> <b> 'b'\n"+
      "   | 'a' <a> <c> 'c'\n";
    WaxeyePEGParser parser = new WaxeyePEGParser(grammar, options, logger);
    String xml = "<r>a<a/><c/>c</r>";
    SmaxDocument document = XmlString.toSmax(xml);
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r><X>a<a/><c/>c</X></r>";
    assertEquals(expectedOutput, output);
  }


}
