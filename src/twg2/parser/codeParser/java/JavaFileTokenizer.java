package twg2.parser.codeParser.java;

import lombok.val;
import twg2.parser.codeParser.CommentStyle;
import twg2.parser.fragment.CodeFragmentType;
import twg2.parser.language.CodeLanguageOptions;
import twg2.parser.text.CharParserFactory;
import twg2.parser.text.StringParserBuilder;
import twg2.parser.tokenizers.CodeBlockTokenizer;
import twg2.parser.tokenizers.CodeStringTokenizer;
import twg2.parser.tokenizers.CommentTokenizer;
import twg2.parser.tokenizers.CodeTokenizerBuilder;
import twg2.parser.tokenizers.IdentifierTokenizer;
import twg2.parser.tokenizers.NumberTokenizer;

/**
 * @author TeamworkGuy2
 * @since 2015-2-9
 */
public class JavaFileTokenizer {

	public static CodeTokenizerBuilder<CodeLanguageOptions.Java> createFileParser() {
		val identifierParser = IdentifierTokenizer.createIdentifierWithGenericTypeTokenizer();
		val numericLiteralParser = NumberTokenizer.createNumericLiteralTokenizer();

		val parser = new CodeTokenizerBuilder<>(CodeLanguageOptions.JAVA)
			.addParser(CommentTokenizer.createCommentTokenizer(CommentStyle.multiAndSingleLine()), CodeFragmentType.COMMENT)
			.addParser(CodeStringTokenizer.createStringTokenizerForJava(), CodeFragmentType.STRING)
			.addParser(CodeBlockTokenizer.createBlockTokenizer('{', '}'), CodeFragmentType.BLOCK)
			.addParser(CodeBlockTokenizer.createBlockTokenizer('(', ')'), CodeFragmentType.BLOCK)
			// no annotation parser, instead we parse
			.addParser(identifierParser, (text, off, len) -> {
				return JavaKeyword.check.isKeyword(text.toString()) ? CodeFragmentType.KEYWORD : CodeFragmentType.IDENTIFIER; // possible bad performance
			})
			.addParser(createOperatorTokenizer(), CodeFragmentType.OPERATOR)
			.addParser(createSeparatorTokenizer(), CodeFragmentType.SEPARATOR)
			.addParser(numericLiteralParser, CodeFragmentType.NUMBER);

		return parser;
	}


	// TODO only partially implemented
	static CharParserFactory createOperatorTokenizer() {
		CharParserFactory operatorParser = new StringParserBuilder("Java operator")
			.addCharLiteralMarker("+", '+')
			.addCharLiteralMarker("-", '-')
			//.addCharLiteralMarker("*", '*') // causes issue parsing comments..?
			.addCharLiteralMarker("=", '=')
			.addCharLiteralMarker("?", '?')
			.addCharLiteralMarker(":", ':')
			.build();
		return operatorParser;
	}


	// TODO couldn't get this working with identifier parser which needs to parse ', ' in strings like 'Map<String, String>'
	static CharParserFactory createSeparatorTokenizer() {
		CharParserFactory annotationParser = new StringParserBuilder("Java separator")
			//.addCharLiteralMarker(',')
			.addCharLiteralMarker(";", ';')
			.addCharLiteralMarker("@", '@')
			.addStringLiteralMarker("::", "::") // TODO technically not a separator, integrate with identifier parser
			.build();
		return annotationParser;
	}

}
