package twg2.parser.codeParser.test;

import static twg2.parser.test.utils.AnnotationAssert.assertAnnotation;
import static twg2.parser.test.utils.FieldAssert.assertField;
import static twg2.parser.test.utils.MethodAssert.assertParameter;
import static twg2.parser.test.utils.TypeAssert.assertType;
import static twg2.parser.test.utils.TypeAssert.ls;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

import twg2.ast.interm.annotation.AnnotationSig;
import twg2.ast.interm.classes.ClassAst;
import twg2.ast.interm.field.FieldSig;
import twg2.ast.interm.method.MethodSig;
import twg2.ast.interm.method.ParameterSig;
import twg2.parser.codeParser.AccessModifierEnum;
import twg2.parser.codeParser.java.JavaBlock;
import twg2.parser.codeParser.java.JavaKeyword;
import twg2.parser.codeParser.tools.NameUtil;
import twg2.parser.language.CodeLanguageOptions;
import twg2.parser.test.utils.CodeFileAndAst;
import twg2.parser.workflow.CodeFileParsed;

/**
 * @author TeamworkGuy2
 * @since 2016-1-15
 */
public class JavaModelParseTest {
	private static List<String> srcLines = Arrays.asList(
		"package ParserExamples.Samples;",
		"",
		"/** A simple class to test parsing.",
		" * @since 2017-6-24",
		" */",
		"protected class Model1Java {",
		"",
		"    /** The modification count. */",
		"    @MultiLineAnnotation(\"alpha-1\", ",
		"        Double.TYPE ,",
		"        3)",
		"    private int mod;",
		"",
		"    /** The name. */",
		"    private String _name = \"initial-name\";",
		"",
		"    /** The names. */",
		"    public Map<Integer, String> Props;",
		"",
		"    /** Set properties",
		"     * @param props the properties",
		"     * @return the properties",
		"     */",
		"    @SetterAnnotation(Prop = \"Props\", UriTemplate = \"/SetProps?props={props}\",",
		"        ResponseFormat = WebMessageFormat.Json)",
		"    public static Result<List<String>> SetProps(final List<String>[] props) {",
		"        content of SetProps;",
		"    }",
		"",
		"    List<String> hiddenField;",
		"}"
	);

	@Parameter
	private CodeFileAndAst<JavaBlock> simpleJava = CodeFileAndAst.<JavaBlock>parse(CodeLanguageOptions.JAVA, "Model1Java.java", "ParserExamples.Samples.Model1Java", true, srcLines);


	public JavaModelParseTest() throws IOException {
	}


	@Test
	public void model1JavaParseTest() {
		List<CodeFileParsed.Simple<String, JavaBlock>> blocks = simpleJava.parsedBlocks;
		String fullClassName = simpleJava.fullClassName;
		Assert.assertEquals(1, blocks.size());
		ClassAst.SimpleImpl<JavaBlock> clas = blocks.get(0).getParsedClass();
		List<FieldSig> fields = clas.getFields();
		Assert.assertEquals(4, fields.size());

		Assert.assertEquals(fullClassName, NameUtil.joinFqName(clas.getSignature().getFullName()));
		Assert.assertEquals(AccessModifierEnum.NAMESPACE_OR_INHERITANCE_LOCAL, clas.getSignature().getAccessModifier());
		Assert.assertEquals("class", clas.getSignature().getDeclarationType());

		assertField(fields, 0, fullClassName + ".mod", "int");
		Assert.assertEquals(Arrays.asList(" The modification count. "), fields.get(0).getComments());
		List<AnnotationSig> as = fields.get(0).getAnnotations();
		// annotations: EmptyAnnotation()
		assertAnnotation(as, 0, "MultiLineAnnotation", new String[] { "arg1", "arg2", "arg3" }, "alpha-1", "Double.TYPE", "3");

		assertField(fields, 1, fullClassName + "._name", "String");
		assertField(fields, 2, fullClassName + ".Props", ls("Map", ls("Integer", "String")));
		assertField(fields, 3, fullClassName + ".hiddenField", ls("List", ls("String")));

		// methods:
		Assert.assertEquals(1, clas.getMethods().size());

		// AddName()
		MethodSig.SimpleImpl m = clas.getMethods().get(0);
		Assert.assertEquals(fullClassName + ".SetProps", NameUtil.joinFqName(m.getFullName()));
		Assert.assertEquals(Arrays.asList(" Set properties\n" +
		        "     * @param props the properties\n" +
		        "     * @return the properties\n" +
		        "     "), m.getComments());
		List<ParameterSig> ps = m.getParamSigs();
		assertParameter(ps, 0, "props", "List<String>[]", Arrays.asList(JavaKeyword.FINAL), null);

		// annotations:
		// @SetterAnnotation(Prop = "Props", UriTemplate = "/SetProps?props={props}", ResponseFormat = WebMessageFormat.Json)
		assertAnnotation(m.getAnnotations(), 0, "SetterAnnotation", new String[] { "Prop", "UriTemplate", "ResponseFormat" }, new String[] { "Props", "/SetProps?props={props}", "WebMessageFormat.Json" });

		//returnType: {"typeName": "Result", "genericParameters": [ {"typeName": "IList", "genericParameters": [ {"typeName": "String"}]}]}
		assertType(ls("Result", ls("List", ls("String"))), m.getReturnType());
	}

}