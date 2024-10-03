package org.greenmercury.basex.xquery.functions.peg.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.greenmercury.basex.xquery.functions.peg.Logger;
import org.greenmercury.basex.xquery.functions.peg.WaxeyePEGParser;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.convert.XmlString;
import org.junit.jupiter.api.Test;

public class WaxeyePEGParserTest
{
  private static final String waxeyePath = "/P/waxeye/waxeye";

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
    "calc  <- ws sum\n" +
    "sum   <- prod *([+-] ws prod)\n" +
    "prod  <- unary *([*/] ws unary)\n" +
    "unary <= '-' ws unary\n" +
    "       | :'(' ws sum :')' ws\n" +
    "       | num\n" +
    "num   <- +[0-9] ?('.' +[0-9]) ws\n" +
    "ws    <: *[ \t\n\r]";

  @Test
  void test_Grammar_1() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    WaxeyePEGParser parser = new WaxeyePEGParser(calculatorGrammar, options, waxeyePath, logger);

    SmaxDocument document = XmlString.toSmax("1 + 1");

    parser.scan(document);
    String output = simplify(document);
    String expectedOutput = "";
    assertEquals(expectedOutput, output);
  }

}
