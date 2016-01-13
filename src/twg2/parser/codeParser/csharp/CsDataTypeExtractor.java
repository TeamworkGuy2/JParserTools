package twg2.parser.codeParser.csharp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.val;
import twg2.collections.util.ListBuilder;
import twg2.parser.baseAst.csharp.CsAstUtil;
import twg2.parser.baseAst.tools.AstFragType;
import twg2.parser.codeParser.AstExtractor;
import twg2.parser.codeParser.CodeFragmentType;
import twg2.parser.codeParser.CodeLanguageOptions;
import twg2.parser.condition.AstParserCondition;
import twg2.parser.documentParser.DocumentFragmentText;
import twg2.parser.intermAst.type.TypeSig;
import twg2.text.stringUtils.StringCheck;
import twg2.text.stringUtils.StringSplit;
import twg2.text.stringUtils.StringTrim;
import twg2.treeLike.simpleTree.SimpleTree;

/**
 * @author TeamworkGuy2
 * @since 2015-12-12
 */
public class CsDataTypeExtractor implements AstParserCondition<TypeSig.Simple> {

	static enum State {
		INIT,
		FOUND_TYPE_NAME,
		COMPLETE,
		FAILED;
	}


	private static final CodeLanguageOptions<CodeLanguageOptions.CSharp, CsAstUtil, AstExtractor<CsBlock>> lang = CodeLanguageOptions.C_SHARP;

	private TypeSig.Simple type;
	private String typeName;
	private State state = State.INIT;
	private boolean allowVoid;
	private boolean prevNodeWasBlockId;
	private String name = "C# data type";


	/**
	 * @param allowVoid indicate whether 'void'/'Void' is a valid data type when parsing (true for method return types, but invalid for field/variable types)
	 */
	public CsDataTypeExtractor(boolean allowVoid) {
		this.allowVoid = allowVoid;
	}


	@Override
	public String name() {
		return name;
	}


	@Override
	public boolean acceptNext(SimpleTree<DocumentFragmentText<CodeFragmentType>> tokenNode) {
		if(state == State.COMPLETE || state == State.FAILED) {
			state = State.INIT;
		}

		if(state == State.INIT && !prevNodeWasBlockId) {
			// found type name
			if(isPossiblyType(tokenNode, allowVoid)) {
				state = State.FOUND_TYPE_NAME;
				typeName = tokenNode.getData().getText();
				prevNodeWasBlockId = lang.getAstUtil().getChecker().isBlockKeyword(tokenNode.getData());
				return true;
			}
			state = State.INIT;
			prevNodeWasBlockId = lang.getAstUtil().getChecker().isBlockKeyword(tokenNode.getData());
			return false;
		}
		else if(state == State.FOUND_TYPE_NAME) {
			boolean isNullable = false;
			// found optional type marker
			if(AstFragType.isOptionalTypeMarker(tokenNode.getData())) {
				isNullable = true;
			}
			this.state = State.COMPLETE;
			this.type = CsDataTypeExtractor.extractGenericTypes(typeName + (isNullable ? "?" : ""));
			prevNodeWasBlockId = lang.getAstUtil().getChecker().isBlockKeyword(tokenNode.getData());
			return isNullable;
		}
		state = State.INIT;
		prevNodeWasBlockId = lang.getAstUtil().getChecker().isBlockKeyword(tokenNode.getData());
		return false;
	}


	@Override
	public TypeSig.Simple getParserResult() {
		return type;
	}


	@Override
	public boolean isComplete() {
		return state == State.COMPLETE;
	}


	@Override
	public boolean isFailed() {
		return state == State.FAILED;
	}


	@Override
	public boolean canRecycle() {
		return true;
	}


	@Override
	public CsDataTypeExtractor recycle() {
		reset();
		return this;
	}


	@Override
	public CsDataTypeExtractor copy() {
		val copy = new CsDataTypeExtractor(this.allowVoid);
		return copy;
	}


	// package-private
	void reset() {
		type = null;
		typeName = null;
		state = State.INIT;
	}


	/** Check if a tree node is the start of a data type
	 */
	public static boolean isPossiblyType(SimpleTree<DocumentFragmentText<CodeFragmentType>> node, boolean allowVoid) {
		val nodeData = node.getData();
		return AstFragType.isIdentifierOrKeyword(nodeData) && (!CsKeyword.isKeyword(nodeData.getText()) || CsKeyword.isDataTypeKeyword(nodeData.getText())) || (allowVoid ? "void".equalsIgnoreCase(nodeData.getText()) : false);
	}


	// TODO create a proper, full parser for generic types
	public static TypeSig.Simple extractGenericTypes(String typeSig) {
		String genericMark = "#";

		if(typeSig.contains(genericMark)) {
			throw new IllegalArgumentException("cannot parse a type signature containing '" + genericMark + "' (because this is a simple parser implementation)");
		}

		StringBuilder sb = new StringBuilder(typeSig);
		val genericParamSets = new ArrayList<String>();
		int i = 0;
		while(true) {
			val paramSet = extractFirstClosestPair(sb, "<", ">", genericMark + i);
			if(paramSet == null) {
				break;
			}
			if(!paramSet.startsWith("<") || !paramSet.endsWith(">")) {
				throw new IllegalStateException("invalid generic type parameter list '" + paramSet + "'");
			}
			val paramSetStr = paramSet.substring(1, paramSet.length() - 1);
			genericParamSets.add(paramSetStr);
			i++;
		}

		// convert the generic parameters to TypeSig nested
		Map.Entry<String, String> rootNameAndMarker = StringSplit.firstMatchParts(sb.toString(), "#");
		TypeSig.SimpleBaseImpl root = new TypeSig.SimpleBaseImpl(StringTrim.trimTrailing(rootNameAndMarker.getKey(), '?'), rootNameAndMarker.getKey().endsWith("?"));
		TypeSig.Simple sig;

		int rootMarker = !StringCheck.isNullOrEmpty(rootNameAndMarker.getValue()) ? Integer.parseInt(rootNameAndMarker.getValue()) : -1;
		if(rootMarker > -1) {
			val sigChilds = expandGenericParamSet(root, rootMarker, genericParamSets);
			sig = new TypeSig.SimpleGenericImpl(root.getTypeName(), sigChilds, root.isNullable());
		}
		else {
			sig = root;
		}

		return sig;
	}


	public static List<TypeSig.Simple> expandGenericParamSet(TypeSig.SimpleBaseImpl parent, int parentParamMarker, List<String> remainingParamSets) {
		String paramSetStr = remainingParamSets.get(parentParamMarker);
		remainingParamSets.remove(parentParamMarker);
		List<String> params = ListBuilder.newMutable(paramSetStr.split(", "));
		List<TypeSig.Simple> paramSigs = new ArrayList<>();
		for(String param : params) {
			// Split the generic parameter name and possible marker indicating further nested generic type
			Map.Entry<String, String> paramNameAndMarker = StringSplit.firstMatchParts(param, "#");

			// Create basic generic parameter using the name
			TypeSig.SimpleBaseImpl paramSigInit = new TypeSig.SimpleBaseImpl(StringTrim.trimTrailing(paramNameAndMarker.getKey(), '?'), paramNameAndMarker.getKey().endsWith("?"));
			TypeSig.Simple paramSig;

			// if this generic parameter has a marker, parse it's sub parameters and add them to a new compound generic type signature
			int paramMarker = !StringCheck.isNullOrEmpty(paramNameAndMarker.getValue()) ? Integer.parseInt(paramNameAndMarker.getValue()) : -1;
			if(paramMarker > -1) {
				val childParams = expandGenericParamSet(paramSigInit, paramMarker, remainingParamSets);
				paramSig = new TypeSig.SimpleGenericImpl(paramSigInit.getTypeName(), childParams, paramSigInit.isNullable());
			}
			// else just use the generic parameter's basic signature (no nested generic types)
			else {
				paramSig = paramSigInit;
			}

			paramSigs.add(paramSig);
		}
		return paramSigs;
	}


	public static String extractFirstClosestPair(StringBuilder src, String start, String end, String replace) {
		int endI = src.indexOf(end);
		int startI = endI > -1 ? src.substring(0, endI).lastIndexOf(start) : -1;
		if(startI > -1 && endI > -1) {
			String res = src.substring(startI, endI + end.length());
			src.replace(startI, endI + end.length(), replace);
			return res;
		}
		else if(startI > -1 || endI > -1) {
			throw new IllegalArgumentException("remaining type signature '" + src.toString() + "' invalid, contains " +
					(startI > -1 ? "start '" + start + "', but no end '" + end + "'" : "end '" + start + "', but no start '" + end + "'"));
		}
		return null;
	}

}