package twg2.parser.codeParser.test;

import static twg2.parser.test.utils.TypeAssert.assertType;
import static twg2.parser.test.utils.AnnotationAssert.assertAnnotation;
import static twg2.parser.test.utils.TypeAssert.ary;
import static twg2.parser.test.utils.TypeAssert.ls;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

import twg2.ast.interm.classes.ClassAst;
import twg2.ast.interm.type.TypeSig.TypeSigResolved;
import twg2.io.files.FileFormatException;
import twg2.io.files.FileReadUtil;
import twg2.parser.codeParser.csharp.CsBlock;
import twg2.parser.codeParser.tools.NameUtil;
import twg2.parser.main.ParserMisc;
import twg2.parser.output.WriteSettings;
import twg2.parser.project.ProjectClassSet;
import twg2.parser.workflow.CodeFileParsed;

/**
 * @author TeamworkGuy2
 * @since 2016-1-8
 */
public class CsParseFilesTest {
	private ClassAst.ResolvedImpl<CsBlock> trackSearchServiceDef;
	private ClassAst.ResolvedImpl<CsBlock> albumInfoDef;
	private ClassAst.ResolvedImpl<CsBlock> trackInfoDef;
	private ClassAst.ResolvedImpl<CsBlock> artistMetaDef;
	private ClassAst.ResolvedImpl<CsBlock> baseClassDef;

	@Parameter
	private ProjectClassSet.Intermediate<CsBlock> projFiles;


	public CsParseFilesTest() throws IOException, FileFormatException {
		Path trackSearchServiceFile = Paths.get("rsc/csharp/ParserExamples/Services/ITrackSearchService.cs");
		Path albumInfoFile = Paths.get("rsc/csharp/ParserExamples/Models/AlbumInfo.cs");
		Path trackInfoFile = Paths.get("rsc/csharp/ParserExamples/Models/TrackInfo.cs");
		Path baseClassFile = Paths.get("rsc/csharp/ParserExamples/BaseClass.cs");
		projFiles = new ProjectClassSet.Intermediate<CsBlock>();
		// TODO until better solution for managing algorithm parallelism
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		ThreadLocal<FileReadUtil> fileReader = ThreadLocal.withInitial(() -> new FileReadUtil());

		HashSet<List<String>> missingNamespaces = new HashSet<>();
		ParserMisc.parseFileSet(ls(trackSearchServiceFile, albumInfoFile, trackInfoFile, baseClassFile), projFiles, executor, fileReader, null);
		ProjectClassSet.Resolved<CsBlock> resFileSet = ProjectClassSet.resolveClasses(projFiles, CsBlock.CLASS, missingNamespaces);

		List<CodeFileParsed.Resolved<CsBlock>> res = resFileSet.getCompilationUnitsStartWith(ls(""));

		WriteSettings ws = new WriteSettings(true, true, true, true);
		StringBuilder sb = new StringBuilder();

		// get a subset of all the parsed files
		for(CodeFileParsed.Resolved<CsBlock> classInfo : res) {
			ClassAst.ResolvedImpl<CsBlock> classParsed = classInfo.parsedClass;
			String simpleName = classParsed.getSignature().getSimpleName();
			if("ITrackSearchService".equals(simpleName)) {
				trackSearchServiceDef = classParsed;
			}
			else if("TrackInfo".equals(simpleName)) {
				trackInfoDef = classParsed;
			}
			else if("AlbumInfo".equals(simpleName)) {
				albumInfoDef = classParsed;
			}
			else if("ArtistMeta".equals(simpleName)) {
				artistMetaDef = classParsed;
			}
			else if("BaseClass".equals(simpleName)) {
				baseClassDef = classParsed;
			}
			else {
				throw new IllegalStateException("unknown class '" + NameUtil.joinFqName(classParsed.getSignature().getFullName()) + "'");
			}

			try {
				sb.setLength(0);
				// ensure that toJson() methods work
				classParsed.toJson(sb, ws);

				System.out.println(sb.toString());
			} catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
	}


	@Test
	public void checkResolvedClasses() {
		// TrackInfo : BaseClass, ISerializable, IComparable<TrackInfo>
		Assert.assertArrayEquals(ary("ParserExamples", "Models", "TrackInfo"), trackInfoDef.getSignature().getFullName().toArray());
		assertType(ary("ParserExamples.BaseClass"), trackInfoDef.getSignature().getExtendClass());
		Assert.assertEquals(2, trackInfoDef.getSignature().getImplementInterfaces().size());
		assertType(ary("ISerializable"), trackInfoDef.getSignature().getImplementInterfaces().get(0));
		assertType(ary("IComparable", ary("TrackInfo")), trackInfoDef.getSignature().getImplementInterfaces().get(1));

		// AlbumInfo
		Assert.assertArrayEquals(ary("ParserExamples", "Models", "AlbumInfo"), albumInfoDef.getSignature().getFullName().toArray());
		assertAnnotation(albumInfoDef.getSignature().getAnnotations(), 0, "DataContract", new String[0]);
		Assert.assertNull(albumInfoDef.getSignature().getExtendClass());
		Assert.assertEquals(0, albumInfoDef.getSignature().getImplementInterfaces().size());

		// BaseClass
		Assert.assertArrayEquals(ary("ParserExamples", "BaseClass"), baseClassDef.getSignature().getFullName().toArray());
		Assert.assertEquals(0, baseClassDef.getFields().size());
		Assert.assertEquals(1, baseClassDef.getMethods().size());

		// TrackInfo.ArtistMeta
		Assert.assertArrayEquals(ary("ParserExamples", "Models", "TrackInfo", "ArtistMeta"), artistMetaDef.getSignature().getFullName().toArray());
		Assert.assertEquals(3, artistMetaDef.getFields().size());
		Assert.assertEquals(0, artistMetaDef.getMethods().size());
	}


	@Test
	public void checkResolvedMethodNames() {
		// SearchResult<TrackInfo> Search(TrackSearchCriteria criteria)
		TypeSigResolved mthd1Ret = trackSearchServiceDef.getMethods().get(0).returnType;
		Assert.assertEquals("ParserExamples.Models.TrackInfo", NameUtil.joinFqName(mthd1Ret.getParams().get(0).getFullName()));

		// SearchResult<IDictionary<AlbumInfo, IList<Track>>> GetAlbumTracks(string albumName)
		TypeSigResolved mthd2Ret = trackSearchServiceDef.getMethods().get(1).returnType;

		Assert.assertEquals("IDictionary", NameUtil.joinFqName(mthd2Ret.getParams().get(0).getFullName()));
		Assert.assertEquals("ParserExamples.Models.AlbumInfo", NameUtil.joinFqName(mthd2Ret.getParams().get(0).getParams().get(0).getFullName()));
		Assert.assertEquals("IList", NameUtil.joinFqName(mthd2Ret.getParams().get(0).getParams().get(1).getFullName()));
		Assert.assertEquals("ParserExamples.Models.TrackInfo", NameUtil.joinFqName(mthd2Ret.getParams().get(0).getParams().get(1).getParams().get(0).getFullName()));
	}

}
