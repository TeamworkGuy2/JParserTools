package twg2.parser.test;

import java.io.IOException;
import java.util.Random;

/**
 * @author TeamworkGuy2
 * @since 2014-9-1
 */
public class ParserTest {

	public static void main(String[] args) throws IOException {
		new IdentifierParserTest().identifierWithGenericTypeParse();
		new IdentifierParserTest().compoundIdentifierParse();
		//parserTest.parseBlocksTest();
		//parserTest.lineBufferTest();

		/*
		stringToCaseTest();
		readCharTypeTest();
		parseDateTimeTest();
		readJsonLiteArrayTest();
		readJsonLiteNumberTest();
		lineBufferTest();
		*/
		System.out.println("float min_normal: " + Float.MIN_NORMAL + ", min_value: " + Float.MIN_VALUE);
		System.out.println("double min_normal: " + Double.MIN_NORMAL + ", min_value: " + Double.MIN_VALUE);
		System.out.println();

		Random rand = new Random();
		int size = 200;
		int similarCount = 0;
		for(int i = 0; i < size; i++) {
			long randLong = rand.nextLong();
			double d = Double.longBitsToDouble(randLong);
			String numStr = Double.toString(d);
			double dParsed = Double.parseDouble(numStr);
			float fParsed = (float)dParsed; //Float.parseFloat(numStr);
			double diff = dParsed - fParsed;
			System.out.println(dParsed + "\t " + fParsed + " :\t " + diff + " | " + (diff > Float.MIN_NORMAL));
			if(diff < Float.MIN_NORMAL) {
				similarCount++;
			}
		}
		System.out.println("similar " + similarCount + "/" + size);
	}

}
