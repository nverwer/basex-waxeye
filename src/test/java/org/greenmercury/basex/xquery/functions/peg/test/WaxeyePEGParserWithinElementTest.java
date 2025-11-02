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

public class WaxeyePEGParserWithinElementTest
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
    "sum   <= prod *(ws [+-] ws prod)\n" +
    "prod  <= unary *(ws [*/] ws unary)\n" +
    "unary <= '-' ws unary\n" +
    "       | :'(' ws sum ws :')'\n" +
    "       | num\n" +
    "num   <- +[0-9] ?('.' +[0-9])\n" +
    "ws    <: *[ \\t\\n\\r]";

  @Test
  void test_grammar_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("parse-within-element", "c");
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<r>The value of <c>1 + 1</c> in binary is <c>10</c></r>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r>The value of <c><Sum><Num>1</Num> + <Num>1</Num></Sum></c> in binary is <c><Num>10</Num></c></r>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_grammar_2() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("parse-within-element", "c");
    options.put("parse-within-namespace", "ns");
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<r>Did you know that <n:c xmlns:n='ns'>1 + 1</n:c> in binary is <c>10</c></r>");
    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "<r>Did you know that <n:c><Sum><Num>1</Num> + <Num>1</Num></Sum></n:c> in binary is <c>10</c></r>";
    assertEquals(expectedOutput, output);
  }

}
