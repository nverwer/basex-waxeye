package org.greenmercury.basex.xquery.functions.peg;

import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.basex.query.CompileContext;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.QueryString;
import org.basex.query.expr.Arr;
import org.basex.query.expr.Expr;
import org.basex.query.util.list.AnnList;
import org.basex.query.value.Value;
import org.basex.query.value.item.FuncItem;
import org.basex.query.value.item.QNm;
import org.basex.query.value.item.Str;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.FElem;
import org.basex.query.value.node.FNode;
import org.basex.query.value.type.FuncType;
import org.basex.query.value.type.SeqType;
import org.basex.query.var.Var;
import org.basex.query.var.VarRef;
import org.basex.query.var.VarScope;
import org.basex.util.hash.IntObjMap;
import org.basex.util.hash.TokenMap;
import org.basex.util.log.Log;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.DomElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class PEGModule extends QueryModule
{

  /**
   * A simple logger that can be used in the named entity recognition function.
   * @param qc the query context
   * @return a very simple logger
   */
  private static Logger logger(final QueryContext qc) {
    Log basexLog = qc.context.log;
    return new Logger() {
      @Override
      public void info(String message)
      {
        basexLog.write("INFO", message, null, qc.context);
      }
      @Override
      public void warning(String message)
      {
        basexLog.write("WARNING", message, null, qc.context);
      }
      @Override
      public void error(String message)
      {
        basexLog.write("ERROR", message, null, qc.context);
      }};
  }

  /**
   * The PEG parser generator function:
   * waxeye-peg-parser($grammar as item(), $options as map(*)?)  as  function(item()) as node()*
   */
  @Requires(Permission.NONE)
  @Deterministic
  @ContextDependent
  public FuncItem waxeyPegParser(Object grammar, Map<String, String> options) throws QueryException {
    // Names and types of the arguments of the generated function.
    final Var[] generatedFunctionParameters = { new VarScope().addNew(new QNm("input"), SeqType.ITEM_O, queryContext, null) };
    final Expr[] generatedFunctionParameterExprs = { new VarRef(null, generatedFunctionParameters[0]) };
    // Result type of the generated function.
    final SeqType generatedFunctionResultType = SeqType.NODE_ZM;
    // Type of the generated function.
    final FuncType generatedFunctionType = FuncType.get(generatedFunctionResultType, generatedFunctionParameters[0].declType);
    // The generated function.
    PEGParserFunction parser = new PEGParserFunction(grammar, options, generatedFunctionResultType, generatedFunctionParameterExprs, queryContext);
    // Return a function item.
    return new FuncItem(null, parser, generatedFunctionParameters, AnnList.EMPTY, generatedFunctionType, generatedFunctionParameters.length, null);
}

  /**
   * The generated PEG parser function.
   */
  private static final class PEGParserFunction extends Arr {

    private final WaxeyePEGParser parser;
    private final Logger logger;

    protected PEGParserFunction(Object grammar, Map<String, String> options,
        SeqType generatedFunctionResultType, Expr[] generatedFunctionParameterExprs, QueryContext queryContext)
    throws QueryException
    {
      super(null, generatedFunctionResultType, generatedFunctionParameterExprs);
      this.logger = logger(queryContext);
      try {
        if (grammar instanceof URL) {
          this.parser = new WaxeyePEGParser((URL)grammar, options, logger);
        } else if (grammar instanceof URI) {
          this.parser = new WaxeyePEGParser(((URI)grammar).toURL(), options, logger);
        } else if (grammar instanceof String) {
          this.parser = new WaxeyePEGParser((String)grammar, options, logger);
        } else {
          throw new IllegalArgumentException("The first parameter ($grammar) of waxeye-peg-parser can not be a "+grammar.getClass().getName());
        }
      } catch (Exception e) {
        throw new QueryException(e);
      }
    }

    private PEGParserFunction(WaxeyePEGParser parser, Logger logger,
        SeqType generatedFunctionResultType, Expr[] generatedFunctionParameterExprs)
    {
      super(null, generatedFunctionResultType, generatedFunctionParameterExprs);
      this.logger = logger;
      this.parser = parser;
    }

    /**
     * Evaluate the generated NER function.
     */
    @Override
    public Value value(final QueryContext qc)
    throws QueryException
    {
      Value inputValue = arg(0).value(qc);
      // Create a SMAX document with a <wrapper> root element around the input.
      SmaxDocument smaxDocument = null;
      if (inputValue.seqType().instanceOf(SeqType.STRING_O)) {
        // Wrap the string in an element.
        final String inputString = ((Str)inputValue).toJava();
        final SmaxElement wrapper = new SmaxElement("wrapper").setStartPos(0).setEndPos(inputString.length());
        smaxDocument = new SmaxDocument(wrapper, inputString);
      } else if (inputValue.seqType().instanceOf(SeqType.NODE_O)) {
        Node inputNode = ((ANode)inputValue).toJava();
        if (inputValue.seqType().instanceOf(SeqType.DOCUMENT_NODE_O)) {
          inputNode = inputNode.getFirstChild();
        }
        Element inputElement = wrap(inputNode);
        try{
          smaxDocument = DomElement.toSmax(inputElement);
        } catch (SmaxException e) {
          throw new QueryException(e);
        }
      } else {
        throw new QueryException("The generated NER function accepts a string or node, but not a "+inputValue.seqType().typeString());
      }
      // Do Named Entity Recognition on the SMAX document.
      this.parser.scan(smaxDocument);
      // Convert the SMAX document to something that BaseX can use.
      try {
        Element outputElement = DomElement.documentFromSmax(smaxDocument).getDocumentElement();
        FNode resultWrapperElement = FElem.build(outputElement, new TokenMap()).finish();
        // Remove the wrapper element and return its contents.
        Value result = resultWrapperElement.childIter().value(qc, null);
        return result;
      } catch (Exception e) {
        throw new QueryException(e);
      }
    }

    /**
     * The org.basex.api.dom.BXNode does not implement appendChild().
     * Therefore, we have to make our own wrapper element, which needs to work for org.greenmercury.smax.convert.DomElement.toSmax(Element).
     * @param node A node that must be wrapped in a "wrapper" element.
     * @return The wrapper element.
     */
    private Element wrap(Node node)
    {
      Element wrapper = new VerySimpleElementImpl("wrapper");
      wrapper.appendChild(node);
      return wrapper;
    }

    @Override
    public Expr copy(CompileContext cc, IntObjMap<Var> vm)
    {
      Expr[] functionParameterExprs = copyAll(cc, vm, this.args());
      return copyType(new PEGParserFunction(this.parser, this.logger, this.seqType(), functionParameterExprs));
    }

    @Override
    public void toString(QueryString qs)
    {
      qs.token("generated-PEG-parser").params(exprs);
    }

  }

}
