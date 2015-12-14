package intermAst.method;

import intermAst.type.TypeSig;

import java.io.IOException;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import output.JsonWritableSig;
import output.JsonWrite;
import output.WriteSettings;
import twg2.annotations.Immutable;
import baseAst.annotation.AnnotationSig;
import baseAst.util.NameUtil;

/**
 * @author TeamworkGuy2
 * @since 2015-11-24
 */
@Immutable
@AllArgsConstructor
public class IntermMethodSig implements JsonWritableSig {
	private final @Getter String name;
	private final @Getter List<String> fullyQualifyingName;
	private final @Getter String paramsSig;
	private final @Getter TypeSig returnType;
	private final @Getter List<AnnotationSig> annotations;


	@Override
	public void toJson(Appendable dst, WriteSettings st) throws IOException {
		dst.append("{ ");
		dst.append("\"name\": \"" + (st.fullMethodName ? NameUtil.joinFqName(fullyQualifyingName) : fullyQualifyingName.get(fullyQualifyingName.size() - 1)) + "\", ");
		dst.append("\"parametersSignature\": \"" + paramsSig.replace('\n', ' ').replace("\"", "\\\"") + "\", ");

		dst.append("\"annotations\": [");
		JsonWrite.joinStrConsumer(annotations, ", ", dst, (ann) -> ann.toJson(dst, st));
		dst.append("], ");

		dst.append("\"returnType\": \"" + returnType + "\"");
		dst.append(" }");
	}


	@Override
	public String toString() {
		return returnType + " " + NameUtil.joinFqName(fullyQualifyingName) + paramsSig;
	}

}
