package twg2.parser.codeParser.extractors;

import java.util.ArrayList;
import java.util.List;

import twg2.ast.interm.annotation.AnnotationSig;
import twg2.ast.interm.block.BlockAst;
import twg2.ast.interm.field.FieldSig;
import twg2.ast.interm.type.TypeSig;
import twg2.parser.codeParser.Keyword;
import twg2.parser.codeParser.BlockType;
import twg2.parser.codeParser.KeywordUtil;
import twg2.parser.codeParser.tools.NameUtil;
import twg2.parser.fragment.AstFragType;
import twg2.parser.fragment.AstTypeChecker;
import twg2.parser.fragment.CodeToken;
import twg2.parser.fragment.CodeTokenType;
import twg2.parser.stateMachine.AstMemberInClassParserReusable;
import twg2.parser.stateMachine.AstParser;
import twg2.parser.stateMachine.Consume;
import twg2.treeLike.simpleTree.SimpleTree;

/**
 * @author TeamworkGuy2
 * @since 2015-12-4
 */
public class FieldExtractor extends AstMemberInClassParserReusable<FieldExtractor.State, List<FieldSig>> {

	static enum State {
		INIT,
		FINDING_ACCESS_MODIFIERS,
		FINDING_DATA_TYPE,
		FINDING_NAME,
		FOUND_NAME_CHECK,
		COMPLETE,
		FAILED;
	}


	KeywordUtil<? extends Keyword> keywordUtil;
	AstParser<List<AnnotationSig>> annotationParser;
	AstParser<List<String>> commentParser;
	AstParser<TypeSig.TypeSigSimple> typeParser;
	List<Keyword> accessModifiers = new ArrayList<>();
	List<FieldSig> fields = new ArrayList<>();
	TypeSig.TypeSigSimple fieldTypeSig;
	String fieldName;
	AstTypeChecker<?> typeChecker;


	public FieldExtractor(String langName, KeywordUtil<? extends Keyword> keywordUtil, BlockAst<? extends BlockType> parentBlock,
			AstParser<TypeSig.TypeSigSimple> typeParser, AstParser<List<AnnotationSig>> annotationParser, AstParser<List<String>> commentParser, AstTypeChecker<?> typeChecker) {
		super(langName, "field", parentBlock, State.COMPLETE, State.FAILED);
		this.keywordUtil = keywordUtil;
		this.typeParser = typeParser;
		this.annotationParser = annotationParser;
		this.commentParser = commentParser;
		this.typeChecker = typeChecker;
		this.state = State.INIT;
	}


	@Override
	public boolean acceptNext(SimpleTree<CodeToken> tokenNode) {
		if(state == State.COMPLETE || state == State.FAILED) {
			state = State.INIT;
		}
		Consume res = null;

		if(state == State.INIT) {
			if(keywordUtil.fieldModifiers().is(tokenNode.getData())) {
				state = State.FINDING_ACCESS_MODIFIERS;
				res = findingAccessModifiers(tokenNode);
				if(res.isAccept()) { return true; }
			}
			if(DataTypeExtractor.isPossiblyType(keywordUtil, tokenNode, false)) {
				state = State.FINDING_DATA_TYPE;
				res = updateAndCheckTypeParser(tokenNode);
				if(res.isAccept()) { return true; }
			}
		}
		else if(state == State.FINDING_ACCESS_MODIFIERS) {
			res = findingAccessModifiers(tokenNode);
			if(res.isAccept()) { return true; }
		}
		else if(state == State.FINDING_DATA_TYPE) {
			res = findingDataType(tokenNode);
			if(res.isAccept()) { return true; }
		}
		else if(state == State.FINDING_NAME) {
			res = findingName(tokenNode);
			if(res.isAccept()) { return true; }
		}
		else if(state == State.FOUND_NAME_CHECK) {
			res = foundNameCheck(tokenNode);
			if(res.isAccept()) { return true; }
		}
		return false;
	}


	private Consume updateAndCheckTypeParser(SimpleTree<CodeToken> tokenNode) {
		boolean res = typeParser.acceptNext(tokenNode);
		boolean complete = typeParser.isComplete();
		boolean failed = typeParser.isFailed();
		if(complete) {
			fieldTypeSig = typeParser.getParserResult();
			typeParser = typeParser.recycle();
			state = State.FINDING_NAME;
		}
		else if(failed) {
			typeParser = typeParser.recycle();
			accessModifiers.clear();
			state = State.FAILED;
		}
		return res ? Consume.ACCEPTED : Consume.REJECTED;
	}


	private Consume findingAccessModifiers(SimpleTree<CodeToken> tokenNode) {
		Keyword accessMod = AccessModifierExtractor.parseAccessModifier(keywordUtil, tokenNode);
		if(accessMod != null) {
			this.accessModifiers.add(accessMod);
			return Consume.ACCEPTED;
		}
		else {
			state = State.FINDING_DATA_TYPE;
			var res2 = findingDataType(tokenNode);
			if(res2 == Consume.REJECTED) {
				accessModifiers.clear();
				state = State.FAILED;
			}
			return res2;
		}
	}


	private Consume findingDataType(SimpleTree<CodeToken> tokenNode) {
		var res = updateAndCheckTypeParser(tokenNode);
		// TODO because the type parser has to look ahead for now, but may not consume the look ahead token while also completing based on a look ahead
		if(res == Consume.REJECTED && state == State.FINDING_NAME) {
			var res2 = findingName(tokenNode);
			if(res2.isAccept()) { return res2; }
		}
		return res;
	}


	private Consume findingName(SimpleTree<CodeToken> tokenNode) {
		if(AstFragType.isIdentifier(tokenNode.getData())) {
			fieldName = tokenNode.getData().getText();
			state = State.FOUND_NAME_CHECK;
			return Consume.ACCEPTED;
		}
		accessModifiers.clear();
		state = State.FAILED;
		return Consume.REJECTED;
	}


	private Consume foundNameCheck(SimpleTree<CodeToken> tokenNode) {
		if((tokenNode == null || tokenNode.getData().getTokenType() != CodeTokenType.BLOCK || typeChecker.isFieldBlock(tokenNode))) {
			state = State.COMPLETE;
			var annotations = new ArrayList<>(annotationParser.getParserResult());
			annotationParser.recycle();

			var comments = new ArrayList<>(commentParser.getParserResult());
			commentParser.recycle();

			var accessMods = new ArrayList<>(accessModifiers);

			fields.add(new FieldSig(fieldName, NameUtil.newFqName(parentBlock.declaration.getFullName(), fieldName), fieldTypeSig, accessMods, annotations, comments));
			accessModifiers.clear();
			return Consume.ACCEPTED;
		}
		accessModifiers.clear();
		state = State.FAILED;
		return Consume.REJECTED;
	}


	@Override
	public List<FieldSig> getParserResult() {
		return fields;
	}


	@Override
	public FieldExtractor recycle() {
		reset();
		return this;
	}


	@Override
	public FieldExtractor copy() {
		return new FieldExtractor(this.langName, this.keywordUtil, this.parentBlock, this.typeParser.copy(), this.annotationParser.copy(), this.commentParser.copy(), this.typeChecker);
	}


	// package-private
	void reset() {
		this.fields.clear();
		this.accessModifiers.clear();
		this.typeParser = typeParser.recycle();
		this.annotationParser = annotationParser.recycle();
	}

}
