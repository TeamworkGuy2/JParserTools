package twg2.parser.tokenizers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.val;
import twg2.collections.dataStructures.PairList;
import twg2.parser.codeParser.analytics.TokenizeStepLogger;
import twg2.parser.codeParser.analytics.ParseTimes.TrackerAction;
import twg2.parser.fragment.CodeToken;
import twg2.parser.fragment.CodeTokenType;
import twg2.parser.fragment.TextToken;
import twg2.parser.language.CodeLanguage;
import twg2.parser.textFragment.TextConsumer;
import twg2.parser.textFragment.TextFragmentRefImpl;
import twg2.parser.textFragment.TextFragmentRefImplMut;
import twg2.parser.textFragment.TextTransformer;
import twg2.parser.textParser.TextCharsParser;
import twg2.parser.textParser.TextParser;
import twg2.parser.workflow.CodeFileSrc;
import twg2.parser.workflow.ParseInput;
import twg2.text.tokenizer.CharMultiConditionParser;
import twg2.text.tokenizer.CharParserFactory;
import twg2.treeLike.simpleTree.SimpleTree;
import twg2.treeLike.simpleTree.SimpleTreeImpl;
import twg2.tuple.Tuples;

/**
 * @author TeamworkGuy2
 * @since 2015-11-24
 */
public class CodeTokenizerBuilder<T_LANG extends CodeLanguage> {
	private PairList<CharParserFactory, TextTransformer<CodeTokenType>> parsers;
	private T_LANG language;


	public CodeTokenizerBuilder(T_LANG lang) {
		this.language = lang;
		this.parsers = new PairList<>();
	}


	/** Add a token parser and an associated function which accepts a token and returns the {@link CodeTokenType} of that token
	 * @param parser the parser to add to this code tokenizer builder
	 * @param transformer a function which accepts a token parsed by the {@code parser} and determines the token's {@link CodeTokenType}
	 * @return this instance
	 */
	public CodeTokenizerBuilder<T_LANG> addParser(CharParserFactory parser, TextTransformer<CodeTokenType> transformer) {
		this.parsers.add(parser, transformer);
		return this;
	}


	/** Add a parser which always returns the specified {@code type}
	 * @param parser the parser to add to this code tokenizer builder
	 * @param type the {@link CodeTokenType} to assign to tokens parsed by the {@code parser}
	 * @return this instance
	 */
	public CodeTokenizerBuilder<T_LANG> addParser(CharParserFactory parser, CodeTokenType type) {
		this.parsers.add(parser, (text, off, len) -> type);
		return this;
	}


	/** Build a document parser from the parsers added via {@link #addParser(CharParserFactory, TextTransformer)}
	 * and {@link #addParser(CharParserFactory, CodeTokenType)}
	 */
	public CodeTokenizer build() {
		// TODO should fix at some point, calling addParser() changes the behavior of CodeTokenizers returned by previous calls to build()
		return (src, srcOff, srcLen, srcName, stepDetails) -> tokenizeCodeFile(parsers, src, srcOff, srcLen, language, srcName, stepDetails);
	}


	/**
	 * @param parserConstructor
	 * @return a parser that takes {@link ParseInput}, tokenizes it (optionally timing and tracking stats about the operation) and returns a {@link CodeFileSrc}
	 */
	public static <_T_LANG> Function<ParseInput, CodeFileSrc> createTokenizerWithTimer(Supplier<CodeTokenizer> parserConstructor) {
		return (params) -> {
			try {
				long start = 0;
				if(params.parseTimes() != null) { start = System.nanoTime(); }

				val parser = parserConstructor.get();

				long setupDone = 0;
				if(params.parseTimes() != null) { setupDone = System.nanoTime(); }

				val fileName = params.fileName();
				val res = parser.tokenizeDocument(params.src(), params.srcOff(), params.srcLen(), fileName, params.parserStepsTracker());

				if(params.parseTimes() != null) {
					params.parseTimes().log(TrackerAction.SETUP, setupDone - start);
					params.parseTimes().log(TrackerAction.TOKENIZE, System.nanoTime() - setupDone);
				}

				return res;
			} catch(Exception e) {
				if(params.errorHandler() != null) {
					params.errorHandler().accept(e);
				}
				throw e;
			}
		};
	}


	/** Parse a source string using the parsers provided by the {@link CodeTokenizer}
	 * @param src the source string
	 * @param srcName optional
	 * @return a parsed {@link CodeFileSrc} containing {@link CodeToken} nodes represented the tokens parsed from {@code src}
	 */
	public static <_T_LANG extends CodeLanguage> CodeFileSrc tokenizeCodeFile(PairList<CharParserFactory, TextTransformer<CodeTokenType>> tokenizers,
			char[] src, int srcOff, int srcLen, _T_LANG lang, String srcName, TokenizeStepLogger stepsDetails) {

		val input = TextCharsParser.of(src, srcOff, srcLen);

		val docTextFragment = new TextFragmentRefImplMut(srcOff, srcOff + srcLen, 0, 0, -1, -1);
		val docRoot = new CodeToken(CodeTokenType.DOCUMENT, docTextFragment, docTextFragment.getText(src, srcOff, srcLen).toString());

		SimpleTree<CodeToken> docTree = tokenizeDocument(srcName, input, stepsDetails, tokenizers, docRoot,
				(type, frag) -> new CodeToken(type, frag, frag.getText(src, srcOff, srcLen).toString()),
				(docFrag) -> docFrag.getTokenType().isCompound(),
				(parent, child) -> parent != child && parent.getToken().contains(child.getToken()));

		docTextFragment.setLineEnd(input.getLineNumber() - 1);
		docTextFragment.setColumnEnd(input.getColumnNumber() - 1);

		return new CodeFileSrc(docTree, srcName, src, srcOff, srcLen, input.getLineNumbers().getRawCompletedLineOffsets(), lang);
	}


	/** Consumes a {@link TextParser}, pass the text through this document parser's list of {@link TextTransformer TextTransformers}
	 * @param srcName an optional name of the source being parsed, can be null
	 * @param input the TextParser to read text from
	 * @param root the root element to use for the returned {@link SimpleTree}
	 * @param isParent determine if a document fragment is possibly a parent fragment
	 * @param isInside determines if a document fragment contains another document fragment
	 * @param stepsDetails optional tracker to keep track of parser stats
	 * @return a {@link SimpleTree} containing tokens parsed from the input
	 */
	public static <D extends TextToken<S, T>, T, S> SimpleTree<D> tokenizeDocument(String srcName, TextParser input, TokenizeStepLogger stepsDetails,
			PairList<CharParserFactory, TextTransformer<T>> tokenizers, D root, BiFunction<T, TextFragmentRefImpl, ? extends D> fragmentConstructor,
			Function<? super D, Boolean> isParent, IsParentChild<? super D> isInside) {
		SimpleTreeImpl<D> tree = new SimpleTreeImpl<>(root);

		List<Entry<CharParserFactory, TextConsumer>> conditions = new ArrayList<>();

		for(int i = 0, size = tokenizers.size(); i < size; i++) {
			TextTransformer<T> transformer = tokenizers.getValue(i);

			conditions.add(Tuples.of(tokenizers.getKey(i), (text, off, len, lineStart, columnStart, lineEnd, columnEnd) -> {
				T elemType = transformer.apply(text, off, len);
				TextFragmentRefImpl textFragment = new TextFragmentRefImpl(off, off + len, lineStart, columnStart, lineEnd, columnEnd);

				D docFrag = fragmentConstructor.apply(elemType, textFragment);

				if(isParent.apply(docFrag)) {
					List<SimpleTreeImpl<D>> subChildren = new ArrayList<>();
					getChildrenInRange(tree.getChildrenRaw(), docFrag, isInside, subChildren);
					removeChildren(tree, subChildren);

					// add after checking for children, so that this fragment does not include itself as one of it's children
					SimpleTreeImpl<D> subTree = tree.addChild(docFrag);
					for(int ii = 0, sizeI = subChildren.size(); ii < sizeI; ii++) {
						subTree.addChildTree(subChildren.get(ii));
					}
				}
				else {
					// add after checking for children, so that this fragment does not include itself as one of it's children
					tree.addChild(docFrag);
				}
			}));
		}

		val parser = new CharMultiConditionParser(stepsDetails, conditions);

		while(input.hasNext()) {
			char ch = input.nextChar();
			//System.out.println(input.getPositionDisplayText());
			parser.parse(ch, input);
		}

		return tree;
	}


	/** Search the src list for elements which exist inside parent and add those that are to dstToAddTo
	 * @param src the list of possible children
	 * @param parent the parent
	 * @param isInside a function which checks if the parent contains a specific child
	 * @param dstToAddTo the destination to add matching children to
	 * @return the dstToAddTo list
	 */
	public static <D extends TextToken<S, T>, S, T> List<SimpleTreeImpl<D>> getChildrenInRange(List<? extends SimpleTreeImpl<D>> src,
			D parent, IsParentChild<? super D> isInside, List<SimpleTreeImpl<D>> dstToAddTo) {
		for(int i = 0, size = src.size(); i < size; i++) {
			SimpleTreeImpl<D> child = src.get(i);
			if(isInside.test(parent, child.getData())) {
				dstToAddTo.add(child);
			}
		}
		return dstToAddTo;
	}


	/** Remove a list of child nodes from a tree.
	 * Throws an error if any of the children do not exist in the tree
	 * @param tree the tree to remove child nodes from
	 * @param children the children to remove
	 */
	public static <D extends TextToken<S, T>, S, T> void removeChildren(SimpleTreeImpl<D> tree, List<SimpleTreeImpl<D>> children) {
		for(int i = 0, size = children.size(); i < size; i++) {
			SimpleTreeImpl<D> child = children.get(i);
			boolean res = tree.removeChildRef(child);
			if(res == false) {
				throw new IllegalStateException("could not remove child '" + child + "' from tree '" + tree + "'");
			}
		}
	}

}
