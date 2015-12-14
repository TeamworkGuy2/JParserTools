package codeParser.csharp;

import java.util.ArrayList;
import java.util.List;

import lombok.val;
import parser.condition.AstParserCondition;
import twg2.treeLike.simpleTree.SimpleTree;
import baseAst.util.AstFragType;
import baseAst.util.NameUtil;
import codeParser.CodeFragmentType;
import documentParser.DocumentFragmentText;

/**
 * @author TeamworkGuy2
 * @since 2015-12-8
 */
public class CsUsingStatementExtractor implements AstParserCondition<List<List<String>>> {

	static enum State {
		INIT,
		FOUND_USING,
		COMPLETE,
		FAILED;
	}


	List<List<String>> usingStatements = new ArrayList<>();
	State state = State.INIT;


	@Override
	public boolean acceptNext(SimpleTree<DocumentFragmentText<CodeFragmentType>> tokenNode) {
		if(state != State.FOUND_USING) {
			if(AstFragType.isKeyword(tokenNode.getData(), CsKeyword.USING)) {
				state = State.FOUND_USING;
			}
		}
		else if(state == State.FOUND_USING) {
			val data = tokenNode.getData();
			if(AstFragType.isIdentifier(data)) {
				usingStatements.add(NameUtil.splitFqName(data.getText()));
				state = State.COMPLETE;
				return true;
			}
			else {
				state = State.FAILED;
			}
		}
		return false;
	}


	@Override
	public List<List<String>> getParserResult() {
		return usingStatements;
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
	public CsUsingStatementExtractor recycle() {
		reset();
		return this;
	}


	@Override
	public CsUsingStatementExtractor copy() {
		val copy = new CsUsingStatementExtractor();
		return copy;
	}


	// package-private
	void reset() {
		usingStatements.clear();
		state = State.INIT;
	}

}
