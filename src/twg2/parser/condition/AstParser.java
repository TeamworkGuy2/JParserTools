package twg2.parser.condition;

import twg2.parser.codeParser.CodeFragmentType;
import twg2.parser.documentParser.DocumentFragmentText;
import twg2.treeLike.simpleTree.SimpleTree;

/**
 * @author TeamworkGuy2
 * @since 2015-12-12
 * @param <T_RESULT> the type of result object that parsed data is store in
 */
public interface AstParser<T_RESULT> extends TokenParser<SimpleTree<DocumentFragmentText<CodeFragmentType>>, T_RESULT> {

	@Override
	public AstParser<T_RESULT> copy();


	@Override
	public default AstParser<T_RESULT> recycle() {
		throw new UnsupportedOperationException("AstParser recycling not supported");
	}


	@Override
	public default AstParser<T_RESULT> copyOrReuse() {
		AstParser<T_RESULT> filter = null;
		if(this.canRecycle()) {
			filter = this.recycle();
		}
		else {
			filter = this.copy();
		}
		return filter;
	}

}