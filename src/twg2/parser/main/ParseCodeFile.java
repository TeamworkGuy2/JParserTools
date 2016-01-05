package twg2.parser.main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.val;
import twg2.io.files.FileReadUtil;
import twg2.io.json.Json;
import twg2.parser.codeParser.CodeFileSrc;
import twg2.parser.codeParser.CodeFragmentType;
import twg2.parser.codeParser.CodeLanguage;
import twg2.parser.codeParser.CodeLanguageOptions;
import twg2.parser.codeParser.ParseInput;
import twg2.parser.codeParser.codeStats.ParseDirectoryCodeFiles;
import twg2.parser.documentParser.DocumentFragmentText;
import twg2.text.stringUtils.StringReplace;
import twg2.text.stringUtils.StringSplit;

/**
 * @author TeamworkGuy2
 * @since 2015-11-22
 */
public class ParseCodeFile {

	public static List<CodeFileSrc<DocumentFragmentText<CodeFragmentType>, CodeLanguage>> parseFiles(List<Path> files) throws IOException {
		List<CodeFileSrc<DocumentFragmentText<CodeFragmentType>, CodeLanguage>> parsedFiles = new ArrayList<>();

		for(Path path : files) {
			File file = path.toFile();
			String srcStr = StringReplace.replace(FileReadUtil.defaultInst.readString(new FileReader(file)), "\r\n", "\n");
			String fileName = file.getName();
			String fileExt = StringSplit.lastMatch(fileName, ".");
			val lang = CodeLanguageOptions.tryFromFileExtension(fileExt);
			if(lang != null) {
				val parsedFileInfo = parseCode(file.toString(), lang, srcStr);
				parsedFiles.add(parsedFileInfo);
			}
			else {
				throw new IllegalArgumentException("unsupported file extension '" + fileExt + "' for parsing '" + fileName + "'");
			}
		}

		return parsedFiles;
	}


	public static CodeFileSrc<DocumentFragmentText<CodeFragmentType>, CodeLanguage> parseCode(String fileName, CodeLanguage lang, String srcStr) {
		val parseParams = new ParseInput(srcStr, null, fileName);
		try {
			@SuppressWarnings("unchecked")
			CodeFileSrc<DocumentFragmentText<CodeFragmentType>, CodeLanguage> parsedFileInfo = (CodeFileSrc<DocumentFragmentText<CodeFragmentType>, CodeLanguage>)lang.getParser().apply(parseParams);
			return parsedFileInfo;
		} catch(Exception e) {
			throw new RuntimeException(parseParams.toString(), e);
		}
	}


	public static void parseAndPrintFileStats() throws IOException {
		Path oldProjDir = Paths.get("C:/Users/TeamworkGuy2/Documents/Visual Studio 2015/Projects/psor/ca");
		Path newProjDir = Paths.get("C:/Users/TeamworkGuy2/Documents/Visual Studio 2015/Projects/ps/l/ca");

		String[] oldProjFileTypes = { ".cs", ".js", ".json", ".html", ".css" };
		val oldFiles = ParseDirectoryCodeFiles.loadFiles(oldProjDir, oldProjFileTypes);
		val results = ParseDirectoryCodeFiles.parseFileStats(oldProjDir, oldFiles);

		String[] newProjFileTypes = { ".cs", ".ts", ".json", ".html", ".css" };
		//val newFiles = loadFiles(newProjDir, newProjFileTypes);
		//val results = parseFiles(newProjDir, newFiles);

		File dstLog = new File("C:/Users/TeamworkGuy2/Documents/parsed-file-stats.txt");

		Json.getDefaultInst().setPrettyPrint(true);
		Json.stringify(results, dstLog);
	}


	public static void parseAndPrintCSharpFileInfo() throws IOException {
		Path file = Paths.get("./rsc/ITrackSearchService.cs");
		//Path file = Paths.get("./rsc/TrackInfo.cs");
		val files = Arrays.asList(file);
		val parsedFiles = parseFiles(files);

		for(int i = 0, sizeI = files.size(); i < sizeI; i++) {
			val parsedFile = parsedFiles.get(i);
			CsMain.printParseFileInfo(files.get(i).toString(), parsedFile, true, true, true, true, true);
		}
	}

}
