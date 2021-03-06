package twg2.parser.workflow;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import twg2.ast.interm.classes.ClassAst;
import twg2.collections.builder.MapBuilder;
import twg2.collections.dataStructures.PairList;
import twg2.io.fileLoading.DirectorySearchInfo;
import twg2.io.fileLoading.SourceFiles;
import twg2.io.files.FileFormatException;
import twg2.io.files.FileReadUtil;
import twg2.io.json.stringify.JsonStringify;
import twg2.logging.LogPrefixFormat;
import twg2.logging.LogService;
import twg2.logging.LogServiceImpl;
import twg2.parser.codeParser.BlockType;
import twg2.parser.codeParser.analytics.ParseTimes;
import twg2.parser.codeParser.analytics.PerformanceTrackers;
import twg2.parser.codeParser.csharp.CsBlock;
import twg2.parser.codeParser.tools.NameUtil;
import twg2.parser.main.ParserMisc;
import twg2.parser.output.WriteSettings;
import twg2.parser.project.ProjectClassSet;
import twg2.text.stringUtils.StringJoin;
import twg2.text.stringUtils.StringSplit;
import twg2.text.stringUtils.StringTrim;

/**
 * @author TeamworkGuy2
 * @since 2016-1-9
 */
public class ParserWorkflow {
	static String newline = System.lineSeparator();

	final List<DirectorySearchInfo> sources;
	final List<DestinationInfo> destinations;
	final Path logFile;
	final int threadCount;
	final boolean debug;


	public ParserWorkflow(List<DirectorySearchInfo> sources, List<DestinationInfo> destinations, Path log, int threads, boolean debug) {
		this.sources = Collections.unmodifiableList(sources);
		this.destinations = Collections.unmodifiableList(destinations);
		this.logFile = log;
		this.threadCount = threads;
		this.debug = debug;
	}


	public static String getNewline() {
		return newline;
	}


	public List<DirectorySearchInfo> getSources() {
		return sources;
	}


	public List<DestinationInfo> getDestinations() {
		return destinations;
	}


	public Path getLogFile() {
		return logFile;
	}


	public int getThreadCount() {
		return threadCount;
	}


	public boolean isDebug() {
		return debug;
	}


	public void run(Level logLevel, ExecutorService executor, PerformanceTrackers perfTracking) throws IOException, FileFormatException {
		// TODO educated guess at average namespace name parts
		NameUtil.estimatedFqPartsCount = 5;

		var log = this.logFile != null ? new LogServiceImpl(logLevel, new PrintStream(this.logFile.toFile()), LogPrefixFormat.DATETIME_LEVEL_AND_CLASS) : null;
		var fileReaders = new ConcurrentHashMap<FileReadUtil, Object>();
		var fileReader = ThreadLocal.withInitial(() -> {
			FileReadUtil fileUtil = new FileReadUtil();
			fileReaders.put(fileUtil, Object.class);
			return fileUtil;
		});

		long start = System.nanoTime();

		var loadRes = SourceFiles.load(this.sources);
		if(log != null) {
			loadRes.log(log, logLevel, true);
		}

		long postLoad = System.nanoTime();

		ParsedResult parseRes = ParsedResult.parse(loadRes.getSources(), executor, fileReader, perfTracking);

		long end = System.nanoTime();

		String parseTimeBreakdownStr = null;
		if(perfTracking != null) {
			var parserStats = perfTracking.getParseStats().entrySet();

			// print out file reader stats
			System.out.println(StringJoin.join(fileReaders.keySet(), "\n", (k) -> k.getStats().toString()));

			// print out total files stats
			var fileSizes = parserStats.stream().mapToInt((entry) -> entry.getValue().getValue2());
			System.out.println("Loaded " + perfTracking.getParseStats().size() + " files, total " + fileSizes.sum() + " bytes");

			var totalCompoundCharParserMatch = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue1().countCompoundCharParserMatch).sum();
			var totalCompoundCharParserAcceptNext = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue1().countCompoundCharParserAcceptNext).sum();
			var totalCreateParser = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue1().countCreateParser).sum();
			var totalTextFragmentsConsumed = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue1().countTextFragmentsConsumed).sum();
			var maxSizePools = new HashMap<String, Integer>();
			var totalParserReuseCount = parserStats.stream().mapToLong((entry) -> {
				var debugging = entry.getValue().getValue1();
				// track the largest pool sizes
				for(int i = 0, size = debugging.reusableParserFactories.size(); i < size; i++) {
					var name = debugging.reusableParserFactories.get(i).name();
					var maxPool = debugging.reusableParserFactories.get(i).getPoolPeekSize();
					var existingMax = maxSizePools.get(name);
					if(existingMax == null || maxPool > existingMax) {
						maxSizePools.put(name, maxPool);
					}
				}
				return debugging.totalParserReuseCount;
			}).sum();

			var readNs = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue0().getReadNs()).sum();
			var setupNs = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue0().getSetupNs()).sum();
			var tokenizeNs = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue0().getTokenizeNs()).sum();
			var extractAstNs = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue0().getExtractAstNs()).sum();
			var totalNs = parserStats.stream().mapToLong((entry) -> entry.getValue().getValue0().getTotalNs()).sum();
			parseTimeBreakdownStr = " (read=" + ParseTimes.roundNsToMs(readNs) + " ms, setup=" + ParseTimes.roundNsToMs(setupNs) + " ms, tokenize=" + ParseTimes.roundNsToMs(tokenizeNs) + " ms, extractAst=" + ParseTimes.roundNsToMs(extractAstNs) + " ms, total=" + ParseTimes.roundNsToMs(totalNs) + " ms)" +
				"\ncompoundCharParserMatch=" + totalCompoundCharParserMatch +
				"\ncompoundCharParserAcceptNext=" + totalCompoundCharParserAcceptNext +
				"\ncreateParser=" + totalCreateParser +
				"\ntextFragmentsConsumed=" + totalTextFragmentsConsumed +
				"\ntotalParserReuseCount=" + totalParserReuseCount + " (peak pool sizes: " + maxSizePools + ")";
		}

		// TODO debugging
		System.out.println("load() time: " + ParseTimes.roundNsToMs(postLoad - start, 0) + " ms");
		System.out.println("parse() time: " + ParseTimes.roundNsToMs(end - postLoad, 0) + " ms" + (parseTimeBreakdownStr != null ? parseTimeBreakdownStr : ""));

		if(log != null) {
			parseRes.log(log, logLevel, true, 1);
		}

		var missingNamespaces = new HashSet<List<String>>();
		var resolvedRes = ResolvedResult.resolve(parseRes.compilationUnits, missingNamespaces);

		if(log != null) {
			resolvedRes.log(log, logLevel, true);
		}

		var filterRes = FilterResult.filter(resolvedRes.compilationUnits, this.destinations);

		if(log != null) {
			filterRes.log(log, logLevel, true);
		}

		WriteResult.write(filterRes.filterSets, missingNamespaces);
	}




	public static class DestinationInfo {
		String path;
		List<String> namespaces;


		@Override
		public String toString() {
			return path + ": " + namespaces.toString();
		}


		public static DestinationInfo parse(String str, String argName) {
			String[] values = StringSplit.split(str, '=', 2);

			if(values[0] == null) {
				throw new IllegalArgumentException("argument '" + argName + "' should contain an argument value");
			}

			var dstInfo = new DestinationInfo();
			dstInfo.namespaces = Collections.emptyList();
			dstInfo.path = values[0];

			if(values[1] != null) {
				if(!values[1].startsWith("[") || !values[1].endsWith("]")) {
					throw new IllegalArgumentException("'" + argName + "' value should be a '[namespace_string,..]'");
				}
				List<String> namespaces = StringSplit.split(values[1].substring(1, values[1].length() - 1), ',');
				dstInfo.namespaces = namespaces;
			}
			return dstInfo;
		}

	}




	public static class ParsedResult {
		/** The set of all parsed files */
		ProjectClassSet.Intermediate<BlockType> compilationUnits;


		@SuppressWarnings({ "unchecked" })
		public ParsedResult(ProjectClassSet.Intermediate<? extends BlockType> compilationUnits) {
			this.compilationUnits = (ProjectClassSet.Intermediate<BlockType>)compilationUnits;
		}


		public void log(LogService log, Level level, boolean includeHeader, int avgCompilationUnitsPerFile) {
			if(LogService.wouldLog(log, level)) {
				int cnt = 0;
				int setCnt = 0;
				var files = compilationUnits.getCompilationUnitsStartWith(Arrays.asList(""));
				var fileSets = new HashMap<CodeFileSrc, List<ClassAst.SimpleImpl<BlockType>>>();
				for(var file : files) {
					List<ClassAst.SimpleImpl<BlockType>> fileSet = fileSets.get(file.id);
					if(fileSet == null) {
						fileSet = new ArrayList<>(avgCompilationUnitsPerFile);
						fileSets.put(file.id, fileSet);
						setCnt++;
					}
					fileSet.add(file.parsedClass);
					cnt++;
				}

				var sb = new StringBuilder();
				if(includeHeader) {
					sb.append(newline);
					sb.append("Classes/interfaces ").append(cnt).append(" in ").append(setCnt).append(" files:");
					sb.append(newline);
				}
				for(var fileSet : fileSets.entrySet()) {
					JsonStringify.inst
						.propName(fileSet.getKey().srcName, sb)
						.toArray(fileSet.getValue(), sb, (f) -> NameUtil.joinFqName(f.getSignature().getFullName()))
						.append(newline, sb);
				}

				log.log(level, this.getClass(), sb.toString());
			}
		}


		public static ParsedResult parse(List<Entry<DirectorySearchInfo, List<Path>>> fileGroups, ExecutorService executor,
				ThreadLocal<FileReadUtil> fileReader, PerformanceTrackers perfTracking) throws IOException, FileFormatException {
			var fileSet = new ProjectClassSet.Intermediate<BlockType>();

			for(var filesWithSrc : fileGroups) {
				ParserMisc.parseFileSet(filesWithSrc.getValue(), fileSet, executor, fileReader, perfTracking);
			}

			return new ParsedResult(fileSet);
		}

	}




	public static class ResolvedResult {
		/** The set of all resolved files (resolution converts simple type names to fully qualifying names for method parameters, fields, extended/implemented classes, etc.) */
		ProjectClassSet.Resolved<BlockType> compilationUnits;
		HashSet<List<String>> missingNamespaces = new HashSet<>();


		@SuppressWarnings({ "unchecked" })
		public ResolvedResult(ProjectClassSet.Resolved<? extends BlockType> compilationUnits, HashSet<List<String>> missingNamespaces) {
			this.compilationUnits = (ProjectClassSet.Resolved<BlockType>)compilationUnits;
		}


		public void log(LogService log, Level level, boolean includeHeader) {
			if(LogService.wouldLog(log, level)) {
				int cnt = 0;
				int setCnt = 0;
				var files = compilationUnits.getCompilationUnitsStartWith(Arrays.asList(""));
				var fileSets = new HashMap<CodeFileSrc, List<ClassAst.ResolvedImpl<BlockType>>>();
				for(var file : files) {
					List<ClassAst.ResolvedImpl<BlockType>> fileSet = fileSets.get(file.id);
					if(fileSet == null) {
						fileSet = new ArrayList<>();
						fileSets.put(file.id, fileSet);
						setCnt++;
					}
					fileSet.add(file.parsedClass);
					cnt++;
				}

				var sb = new StringBuilder();
				if(includeHeader) {
					sb.append(newline);
					sb.append("Resolved classes/interfaces ").append(cnt).append(" in ").append(setCnt).append(" files:");
					sb.append(newline);
				}

				if(missingNamespaces.size() > 0) {
					sb.append("missingNamespaces: ");
					sb.append(missingNamespaces);
					sb.append(newline);
				}

				for(var fileSet : fileSets.entrySet()) {
					JsonStringify.inst
						.propNameUnquoted(fileSet.getKey().srcName, sb)
						.toArray(fileSet.getValue(), sb, (f) -> NameUtil.joinFqName(f.getSignature().getFullName()))
						.append(newline, sb);
				}

				log.log(level, this.getClass(), sb.toString());
			}
		}


		public static ResolvedResult resolve(ProjectClassSet.Intermediate<BlockType> simpleFileSet, HashSet<List<String>> missingNamespaces) throws IOException {
			// TODO shouldn't be using CsBlock, should use language block type
			var resFileSet = ProjectClassSet.resolveClasses(simpleFileSet, CsBlock.CLASS, missingNamespaces);

			return new ResolvedResult(resFileSet, missingNamespaces);
		}

	}




	public static class FilterResult {
		/** List of names associated with parser results */
		Map<DestinationInfo, List<CodeFileParsed.Resolved<BlockType>>> filterSets;


		@SuppressWarnings({ "unchecked" })
		public FilterResult(Map<? extends DestinationInfo, ? extends List<? extends CodeFileParsed.Resolved<? extends BlockType>>> filterSets) {
			this.filterSets = (Map<DestinationInfo, List<CodeFileParsed.Resolved<BlockType>>>)filterSets;
		}


		public void log(LogService log, Level level, boolean includeHeader) {
			if(LogService.wouldLog(log, level)) {
				var sb = new StringBuilder();
				if(includeHeader) {
					sb.append(newline);
					sb.append("destination sets:");
					sb.append(newline);
				}

				for(var entry : filterSets.entrySet()) {
					sb.append(newline);
					sb.append(entry.getKey()).append(" (results: ").append(entry.getValue().size() + ")");
					sb.append(newline);
					JsonStringify.inst.join(entry.getValue(), newline, false, sb, (f) -> NameUtil.joinFqName(f.parsedClass.getSignature().getFullName()));
					sb.append(newline);
				}
				log.log(level, this.getClass(), sb.toString());
			}
		}


		public static FilterResult filter(ProjectClassSet.Resolved<BlockType> resFileSet, List<DestinationInfo> destinations) throws IOException {
			Map<DestinationInfo, List<CodeFileParsed.Resolved<BlockType>>> resSets = new HashMap<>();
			for(var dstInfo : destinations) {
				var matchingNamespaces = new ArrayList<CodeFileParsed.Resolved<BlockType>>();
				for(var namespace : dstInfo.namespaces) {
					var fileSet = resFileSet.getCompilationUnitsStartWith(StringSplit.split(namespace, '.'));
					matchingNamespaces.addAll(fileSet);
				}
				resSets.put(dstInfo, matchingNamespaces);
			}

			return new FilterResult(resSets);
		}

	}




	public static class WriteResult {

		public static void write(Map<DestinationInfo, List<CodeFileParsed.Resolved<BlockType>>> resSets, Collection<List<String>> missingNamespaces) throws IOException {
			var writeSettings = new WriteSettings(true, false, false, true);
			// associates file paths with how many times each has been written to (so we can append on subsequent writes)
			var definitionsByOutputFile = new HashMap<String, PairList<String, char[]>>();

			var tmpSb = new StringBuilder(2048);

			// write class definitions to JSON strings and group by output file
			for(var dstSet : resSets.entrySet()) {
				var dst = dstSet.getKey();
				var classes = dstSet.getValue();
				
				PairList<String, char[]> definitionStrs = definitionsByOutputFile.get(dst.path);
				if(definitionStrs == null) {
					definitionStrs = new PairList<>();
					definitionsByOutputFile.put(dst.path, definitionStrs);
				}

				for(var classInfo : classes) {
					tmpSb.setLength(0);
					String classNameFq = NameUtil.joinFqName(classInfo.parsedClass.getSignature().getFullName());
					tmpSb.append("\"" + classNameFq + "\": ");
					classInfo.parsedClass.toJson(tmpSb, writeSettings);
					char[] dstChars = new char[tmpSb.length()];
					tmpSb.getChars(0, tmpSb.length(), dstChars, 0);
					definitionStrs.add(classNameFq, dstChars);
				}
			}

			for(var dstData : definitionsByOutputFile.entrySet()) {
				List<Entry<String, char[]>> defs = new ArrayList<>(MapBuilder.mutable(dstData.getValue().keyList(), dstData.getValue().valueList(), true).entrySet());
				Collections.sort(defs, (c1, c2) -> c1.getKey().compareTo(c2.getKey()));

				try(var output = Files.newBufferedWriter(Paths.get(dstData.getKey()), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					boolean first = true;
					output.write("{\n\"files\": {");

					for(var def : defs) {
						if(!first) {
							output.append(",\n");
						}
						output.write(def.getValue(), 0, def.getValue().length);
						first = false;
					}

					output.write("}\n}");
					//String[] nonSystemMissingNamespaces = missingNamespaces.stream().filter((ns) -> !"System".equals(ns.get(0))).map((ns) -> NameUtil.joinFqName(ns)).toArray((n) -> new String[n]);
					//System.out.println("missing non-system namespaces: (" + nonSystemMissingNamespaces.length + "): " + Arrays.toString(nonSystemMissingNamespaces));
				} catch(IOException ioe) {
					throw ioe;
				}
			}
		}

	}




	public static ParserWorkflow parseArgs(String[] args) {
		if(args.length == 0 || Arrays.asList("-help", "--help", "-h").contains(args[0])) {
			System.out.println("An in-progress suite of parsing tools for C#, Java, and TypeScript source code.\n" +
				"Used to create basic ASTs containing class signatures, fields, and methods. (source: https://github.com/TeamworkGuy2/JParserCode)\n" +
				"example command:\n" +
				"-sources 'root/TeamworkGuy2/Projects/PsServer/Services=1,[cs];" +
					"root/TeamworkGuy2/Projects/PsServer/Entities=3,[cs]'" +
				" -destinations 'root/TeamworkGuy2/output/Services.json=[App.Services];" +
					"root/TeamworkGuy2/output/Models.json=[App.Entities]'" +
				" -log 'root/TeamworkGuy2/output/parser.log'");
		}

		Map<String, String> argNames = new HashMap<>();
		argNames.put("sources", "sources - a semicolon separated list of strings in the format 'path=depth,[fileExt,fileExt,...];path=depth,[fileExt,fileExt,...];...'.  Example: '/project/myApp/Models=3,[java,json]'");
		argNames.put("destinations", "destinations - a semicolon separated list of strings in the format 'path=[namespace,namespace,...], ...'.  Example: '/project/tmp_files/models.json=[MyApp.Models]'");
		argNames.put("log", "log - a log file path in the format 'path'.  Example: '/project/tmp_files/parser-log.log'");
		argNames.put("threads", "threads - the number of threads to use, 0 for thread count equal to number of logical processors, default 1");
		argNames.put("debug", "debug - log detailed debug and performance info");

		List<DirectorySearchInfo> srcs = new ArrayList<>();
		List<DestinationInfo> dsts = new ArrayList<>();
		Path log = null;
		int threads = 1;
		boolean debug = false;

		// TODO debugging
		System.out.println("args:");
		for(int i = 0, size = args.length; i < size; i++) {
			System.out.println(args[i]);
		}
		System.out.println();

		for(int i = 0, size = args.length; i < size; i += 2) {
			String name = StringTrim.trimLeading(args[i], '-');
			String desc = argNames.get(name);
			if(desc != null) {
				if("debug".equals(name)) {
					debug = true;
					continue; // skip further argument parsing
				}

				if(i + 1 >= args.length) {
					throw new IllegalArgumentException("'" + name + "' is a valid argument name, but is not followed by an argument");
				}

				if("log".equals(name)) {
					log = Paths.get(args[i + 1]);
				}

				if("threads".equals(name)) {
					threads = Integer.parseInt(args[i + 1]);
					threads = (threads == 0 ? Runtime.getRuntime().availableProcessors() : threads);
				}

				if("sources".equals(name)) {
					var values = StringSplit.split(args[i + 1], ';');
					for(var valueStr : values) {
						var value = DirectorySearchInfo.parseFromArgs(valueStr, "sources");
						srcs.add(value);
					}
				}

				if("destinations".equals(name)) {
					var values = StringSplit.split(args[i + 1], ';');
					for(var valueStr : values) {
						var value = DestinationInfo.parse(valueStr, "destination");
						dsts.add(value);
					}
				}
			}
		}

		return new ParserWorkflow(srcs, dsts, log, threads, debug);
	}

}
