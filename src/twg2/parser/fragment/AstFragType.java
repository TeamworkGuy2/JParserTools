package twg2.parser.fragment;

import java.util.function.BiPredicate;

import twg2.arrays.ArrayUtil;
import twg2.parser.codeParser.Operator;
import twg2.treeLike.simpleTree.SimpleTree;

/**
 * @author TeamworkGuy2
 * @since 2015-12-10
 */
public class AstFragType {

	public static final boolean isOperator(CodeToken node, Operator op) {
		return node != null && node.getTokenType() == CodeTokenType.OPERATOR && op.toSrc().equals(node.getText());
	}


	public static final boolean isOptionalTypeMarker(CodeToken node) {
		return node != null && (node.getTokenType() == CodeTokenType.OPERATOR && "?".equals(node.getText()));
	}


	public static final boolean isSeparator(CodeToken node, String separator) {
		return node != null && (node.getTokenType() == CodeTokenType.SEPARATOR && separator.equals(node.getText()));
	}


	public static final boolean isIdentifier(CodeToken node) {
		return node != null && (node.getTokenType() == CodeTokenType.IDENTIFIER);
	}


	public static final boolean isIdentifierOrKeyword(CodeToken node) {
		return node != null && (node.getTokenType() == CodeTokenType.KEYWORD || node.getTokenType() == CodeTokenType.IDENTIFIER);
	}


	public static final boolean isKeyword(CodeToken node) {
		return node != null && (node.getTokenType() == CodeTokenType.KEYWORD);
	}


	public static final boolean isBlock(CodeToken node, char blockSymbol) {
		String nodeText;
		return node != null && node.getTokenType().isCompound() && (nodeText = node.getText()).length() > 0 && nodeText.charAt(0) == blockSymbol;
	}


	public static final boolean isBlock(CodeToken node, String blockSymbol) {
		return node != null && node.getTokenType().isCompound() && node.getText().startsWith(blockSymbol);
	}


	// TODO unused
	public static final boolean blockContainsOnly(SimpleTree<CodeToken> block, BiPredicate<CodeToken, CodeTokenType> cond, boolean emptyTreeValid, CodeTokenType... optionalAllows) {
		if(block == null) {
			return emptyTreeValid;
		}
		if(optionalAllows == null) {
			optionalAllows = new CodeTokenType[0];
		}
		var childs = block.getChildren();
		if(childs.size() == 0) {
			return false;
		}

		for(SimpleTree<CodeToken> child : childs) {
			var frag = child.getData();
			if(ArrayUtil.indexOf(optionalAllows, frag.getTokenType()) < 0 && !cond.test(frag, frag.getTokenType())) {
				return false;
			}
		}
		return true;
	}

}
