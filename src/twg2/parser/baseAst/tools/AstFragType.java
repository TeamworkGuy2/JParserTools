package twg2.parser.baseAst.tools;

import twg2.parser.codeParser.CodeFragmentType;
import twg2.parser.documentParser.DocumentFragment;
import twg2.parser.documentParser.DocumentFragmentText;

/**
 * @author TeamworkGuy2
 * @since 2015-12-10
 */
public class AstFragType {


	/** Check if a {@link DocumentFragment} has a fragment type equal to {@code type1}
	 */
	public static final boolean isType(DocumentFragment<?, CodeFragmentType> node, CodeFragmentType type1) {
		return node != null && (node.getFragmentType() == type1);
	}


	/** Check if a {@link DocumentFragment} has a fragment type equal to {@code type1 OR type2}
	 */
	public static final boolean isType(DocumentFragment<?, CodeFragmentType> node, CodeFragmentType type1, CodeFragmentType type2) {
		return node != null && (node.getFragmentType() == type1 || node.getFragmentType() == type2);
	}


	/** Check if a {@link DocumentFragment} has a fragment type equal to {@code type1 OR type2 type3}
	 */
	public static final boolean isType(DocumentFragment<?, CodeFragmentType> node, CodeFragmentType type1, CodeFragmentType type2, CodeFragmentType type3) {
		return node != null && (node.getFragmentType() == type1 || node.getFragmentType() == type2 || node.getFragmentType() == type3);
	}


	/** Check if a {@link DocumentFragment} has a fragment type equal to any of {@code types}
	 */
	public static final boolean isType(DocumentFragment<?, CodeFragmentType> node, CodeFragmentType... types) {
		if(node == null) {
			return false;
		}
		CodeFragmentType nodeType = node.getFragmentType();
		for(CodeFragmentType type : types) {
			if(nodeType != type) {
				return false;
			}
		}
		return true;
	}


	public static final boolean isOptionalTypeMarker(DocumentFragmentText<CodeFragmentType> node) {
		return node != null && (node.getFragmentType() == CodeFragmentType.OPERATOR && "?".equals(node.getText()));
	}


	public static final boolean isIdentifier(DocumentFragmentText<CodeFragmentType> node) {
		return node != null && (node.getFragmentType() == CodeFragmentType.IDENTIFIER);
	}


	public static final boolean isIdentifierOrKeyword(DocumentFragmentText<CodeFragmentType> node) {
		return node != null && (node.getFragmentType() == CodeFragmentType.KEYWORD || node.getFragmentType() == CodeFragmentType.IDENTIFIER);
	}


	public static final boolean isKeyword(DocumentFragmentText<CodeFragmentType> node) {
		return node != null && (node.getFragmentType() == CodeFragmentType.KEYWORD);
	}


	public static final boolean isBlock(DocumentFragmentText<CodeFragmentType> node, String blockSymbol) {
		return node != null && node.getFragmentType().isCompound() && node.getText().startsWith(blockSymbol);
	}

}