package twg2.parser.codeParser.extractors;

import java.util.ArrayList;
import java.util.List;

import lombok.val;
import twg2.ast.interm.type.TypeSig;
import twg2.collections.builder.ListBuilder;
import twg2.parser.baseAst.AccessModifier;
import twg2.parser.baseAst.AstParser;
import twg2.parser.baseAst.tools.AstFragType;
import twg2.parser.codeParser.CodeFragmentType;
import twg2.parser.codeParser.KeywordUtil;
import twg2.parser.documentParser.CodeFragment;
import twg2.parser.language.CodeLanguage;
import twg2.parser.primitive.NumericParser;
import twg2.text.stringUtils.StringCheck;
import twg2.text.stringUtils.StringSplit;
import twg2.text.stringUtils.StringTrim;
import twg2.treeLike.simpleTree.SimpleTree;

/**
 * @author TeamworkGuy2
 * @since 2015-12-12
 */
public class DataTypeExtractor implements AstParser<TypeSig.Simple> {

	static enum State {
		INIT,
		FOUND_TYPE_NAME,
		COMPLETE,
		FAILED;
	}


	private final CodeLanguage lang;
	private TypeSig.Simple type;
	private String typeName;
	private State state = State.INIT;
	private boolean allowVoid;
	private boolean prevNodeWasBlockId;
	private String name;


	/**
	 * @param allowVoid indicate whether 'void'/'Void' is a valid data type when parsing (true for method return types, but invalid for field/variable types)
	 */
	public DataTypeExtractor(CodeLanguage lang, boolean allowVoid) {
		this.lang = lang;
		this.allowVoid = allowVoid;
		this.name = lang + " data type";
	}


	@Override
	public String name() {
		return name;
	}


	@Override
	public boolean acceptNext(SimpleTree<CodeFragment> tokenNode) {
		if(state == State.COMPLETE || state == State.FAILED) {
			state = State.INIT;
		}

		if(state == State.INIT && !prevNodeWasBlockId) {
			// found type name
			if(isPossiblyType(lang.getKeywordUtil(), tokenNode, allowVoid)) {
				state = State.FOUND_TYPE_NAME;
				typeName = tokenNode.getData().getText();
				prevNodeWasBlockId = lang.getKeywordUtil().blockModifiers().is(tokenNode.getData());
				return true;
			}
			state = State.INIT;
			prevNodeWasBlockId = lang.getKeywordUtil().blockModifiers().is(tokenNode.getData());
			return false;
		}
		else if(state == State.FOUND_TYPE_NAME) {
			boolean isNullable = false;
			// found optional type marker
			if(AstFragType.isOptionalTypeMarker(tokenNode.getData())) {
				isNullable = true;
			}
			this.state = State.COMPLETE;
			this.type = DataTypeExtractor.extractGenericTypes(typeName + (isNullable ? "?" : ""), lang.getKeywordUtil());
			prevNodeWasBlockId = lang.getKeywordUtil().blockModifiers().is(tokenNode.getData());
			return isNullable;
		}
		state = State.INIT;
		prevNodeWasBlockId = lang.getKeywordUtil().blockModifiers().is(tokenNode.getData());
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
	public DataTypeExtractor recycle() {
		reset();
		return this;
	}


	@Override
	public DataTypeExtractor copy() {
		val copy = new DataTypeExtractor(this.lang, this.allowVoid);
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
	public static <T> boolean isPossiblyType(KeywordUtil<? extends AccessModifier> keywordType, SimpleTree<CodeFragment> node, boolean allowVoid) {
		val nodeData = node.getData();
		return AstFragType.isIdentifierOrKeyword(nodeData) && (!keywordType.isKeyword(nodeData.getText()) || keywordType.isDataTypeKeyword(nodeData.getText())) || (allowVoid ? "void".equalsIgnoreCase(nodeData.getText()) : false);
	}


	/** Check if one or two nodes are a number with an optiona -/+ sign
	 * @param node1
	 * @param node2Optional
	 * @return the number of nodes used, 0 if neither node was a number, 1 if the first node was a number, 2 if the first node was a sign and the second node was a number
	 */
	public static int isNumber(CodeFragment node1, CodeFragment node2Optional) {
		String n1Text;
		int matches = node1.getFragmentType() == CodeFragmentType.NUMBER ? 1 : 0;
		if(matches > 0) {
			return matches;
		}
		matches = (node2Optional != null && (n1Text = node1.getText()).length() == 1 && NumericParser.isSign.test(n1Text.charAt(0)) && node2Optional.getFragmentType() == CodeFragmentType.NUMBER) ? 2 : 0;
		return matches;
	}


	// TODO create a proper, full parser for generic types
	public static TypeSig.Simple extractGenericTypes(String typeSig, KeywordUtil<? extends AccessModifier> keywordUtil) {
		String genericMark = "#";

		if(typeSig.contains(genericMark)) {
			throw new IllegalArgumentException("cannot parse a type signature containing '" + genericMark + "' (because this is a simple parser implementation)");
		}

		val sb = new StringBuilder(typeSig);
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
		val rootNameAndMarker = StringSplit.firstMatchParts(sb.toString(), "#");
		val paramName = StringTrim.trimTrailing(rootNameAndMarker.getKey(), '?');
		val nameAndArrayDimensions = StringTrim.countAndTrimTrailing(paramName, "[]", true);
		val isOptional = rootNameAndMarker.getKey().endsWith("?");
		TypeSig.SimpleBaseImpl root = new TypeSig.SimpleBaseImpl(nameAndArrayDimensions.getValue(), nameAndArrayDimensions.getKey(), isOptional, keywordUtil.isPrimitive(nameAndArrayDimensions.getValue()));
		TypeSig.Simple sig;

		int rootMarker = !StringCheck.isNullOrEmpty(rootNameAndMarker.getValue()) ? Integer.parseInt(rootNameAndMarker.getValue()) : -1;
		if(rootMarker > -1) {
			val sigChilds = expandGenericParamSet(keywordUtil, root, rootMarker, genericParamSets);
			sig = new TypeSig.SimpleGenericImpl(root.getTypeName(), sigChilds, root.getArrayDimensions(), root.isNullable(), keywordUtil.isPrimitive(root.getTypeName()));
		}
		else {
			sig = root;
		}

		return sig;
	}


	public static List<TypeSig.Simple> expandGenericParamSet(KeywordUtil<? extends AccessModifier> keywordUtil, TypeSig.SimpleBaseImpl parent, int parentParamMarker, List<String> remainingParamSets) {
		String paramSetStr = remainingParamSets.get(parentParamMarker);
		remainingParamSets.remove(parentParamMarker);
		List<TypeSig.Simple> paramSigs = new ArrayList<>();
		List<String> params = ListBuilder.mutable(paramSetStr.split(", "));

		for(String param : params) {
			// Split the generic parameter name and possible marker indicating further nested generic type
			val paramNameAndMarker = StringSplit.firstMatchParts(param, "#");

			// Create basic generic parameter using the name
			val paramName = StringTrim.trimTrailing(paramNameAndMarker.getKey(), '?');
			val nameAndArrayDimensions = StringTrim.countAndTrimTrailing(paramName, "[]", true);
			val isOptional = paramNameAndMarker.getKey().endsWith("?");
			TypeSig.SimpleBaseImpl paramSigInit = new TypeSig.SimpleBaseImpl(nameAndArrayDimensions.getValue(), nameAndArrayDimensions.getKey(), isOptional, keywordUtil.isPrimitive(nameAndArrayDimensions.getValue()));
			TypeSig.Simple paramSig;

			// if this generic parameter has a marker, parse it's sub parameters and add them to a new compound generic type signature
			int paramMarker = !StringCheck.isNullOrEmpty(paramNameAndMarker.getValue()) ? Integer.parseInt(paramNameAndMarker.getValue()) : -1;
			if(paramMarker > -1) {
				val childParams = expandGenericParamSet(keywordUtil, paramSigInit, paramMarker, remainingParamSets);
				paramSig = new TypeSig.SimpleGenericImpl(paramSigInit.getTypeName(), childParams, paramSigInit.getArrayDimensions(), paramSigInit.isNullable(), keywordUtil.isPrimitive(paramSigInit.getTypeName()));
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