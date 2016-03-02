package twg2.parser.test;

import static twg2.parser.test.ParserTestUtils.parseTestSameParsed;

import org.junit.Test;

import twg2.parser.codeParser.csharp.CsClassParser;
import twg2.parser.condition.text.CharParser;

/**
 * @author TeamworkGuy2
 * @since 2016-2-7
 */
public class ParseAnnotationTest {

	@Test
	public void annotationParseTest() {
		String name = "AnnotationParse";
		CharParser cond = CsClassParser.createAnnotationParser().createParser();

		parseTestSameParsed(false, false, name, cond, "[");
		parseTestSameParsed(false, true, name, cond, "]");
		parseTestSameParsed(false, false, name, cond, "[]");
		parseTestSameParsed(true, false, name, cond, "[A]");
	}

}