package intermAst.field;

import intermAst.type.TypeSig;

import java.io.IOException;
import java.util.List;

import output.JsonWritableSig;
import output.WriteSettings;
import baseAst.annotation.AnnotationSig;
import baseAst.util.NameUtil;
import twg2.annotations.Immutable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author TeamworkGuy2
 * @since 2015-12-4
 */
@Immutable
@AllArgsConstructor
public class IntermFieldSig implements JsonWritableSig {
	private final @Getter String name;
	private final @Getter List<String> fullyQualifyingName;
	private final @Getter TypeSig fieldType;
	private final @Getter List<AnnotationSig> annotations;


	@Override
	public void toJson(Appendable dst, WriteSettings st) throws IOException {
		dst.append("{ ");
		dst.append("\"name\": \"" + (st.fullFieldName ? NameUtil.joinFqName(fullyQualifyingName) : fullyQualifyingName.get(fullyQualifyingName.size() - 1)) + "\", ");
		dst.append("\"type\": \"" + fieldType + "\"");
		dst.append(" }");
	}


	@Override
	public String toString() {
		return fieldType + " " + NameUtil.joinFqName(fullyQualifyingName);
	}

}
