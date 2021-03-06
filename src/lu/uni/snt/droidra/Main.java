package lu.uni.snt.droidra;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import lu.uni.snt.droidra.booster.ApkBooster;
import lu.uni.snt.droidra.model.ReflectionExchangable;
import lu.uni.snt.droidra.model.ReflectionProfile;
import lu.uni.snt.droidra.model.StmtValue;
import lu.uni.snt.droidra.model.UniqStmt;
import lu.uni.snt.droidra.retarget.RetargetWithDummyMainGenerator;
import lu.uni.snt.droidra.typeref.ArrayVarItemTypeRef;

import com.google.gson.Gson;

import edu.psu.cse.siis.coal.DefaultCommandLineArguments;
import edu.psu.cse.siis.coal.DefaultCommandLineParser;
import edu.psu.cse.siis.coal.DefaultResult;
import edu.psu.cse.siis.coal.Result;

/**
 * at soot.toDex.PrimitiveType.getByName(PrimitiveType.java:24)
	at soot.toDex.ExprVisitor.castPrimitive(ExprVisitor.java:587)
	at soot.toDex.ExprVisitor.caseCastExpr(ExprVisitor.java:548)
	at soot.jimple.internal.AbstractCastExpr.apply(AbstractCastExpr.java:134)
	at soot.toDex.StmtVisitor.caseAssignStmt(StmtVisitor.java:371)
	at soot.jimple.internal.JAssignStmt.apply(JAssignStmt.java:238)
	at soot.toDex.DexPrinter.toInstructions(DexPrinter.java:1152)
	at soot.toDex.DexPrinter.toMethodImplementation(DexPrinter.java:1035)
	at soot.toDex.DexPrinter.toMethods(DexPrinter.java:932)
	at soot.toDex.DexPrinter.addAsClassDefItem(DexPrinter.java:496)
	at soot.toDex.DexPrinter.add(DexPrinter.java:1281)
	at soot.PackManager.writeClass(PackManager.java:1004)
	at soot.PackManager.writeOutput(PackManager.java:620)
	at soot.PackManager.writeOutput(PackManager.java:526)
	at soot.Main.run(Main.java:250)
	at soot.Main.main(Main.java:152)
	at lu.uni.snt.droidra.booster.ApkBooster.apkBooster(ApkBooster.java:69)
	at lu.uni.snt.droidra.Main.main(Main.java:145)
 */



/**
 * COAL contribution
 * 
 * No parameter exception
 * Providing a parameter for manually specifying a main method
 * Model.toString() exception, some Field is null, finding the reasons
 * 
 * Check sub-class model and override-method
 * 
 * @author li.li
 *
 */
public class Main 
{
	/**
	 * 0. Some inits
	 * 
	 * 1. Retarget Android app to class (with a single main entrance).
	 * 
	 * 2. Launch COAL for reflection string extractions.
	 * 		In this step, we can also put the results into a database for better usage. (heuristic results)
	 *     
	 * 
	 * 3 Revist the Android app to make sure all the involved Class and methods, 
	 *     fields exist in the current classpath, if not, 
	 *     1) try to dynamically load them, or 
	 *     2) create fake one for all of them.
	 *     
	 *     ==> it can also provide heuristic results for human analysis (e.g., how the app code is dynamically loaded)
	 * 
	 * 4. Revisit the Android app for instrumentation.
	 * 	   Even in this step, if some methods, fields or constructors do not exist, 
	 *     a robust implementation should be able to create them on-the-fly.
	 *      
	 * 5. Based on the instrumented results to perform furture static analysis.
	 * 
	 * @param args
	 */

	public static void main(String[] args) 
	{
		long startTime = System.currentTimeMillis();
		System.out.println("==>TIME:" + startTime);
		
		String apkPath = args[0];
		String forceAndroidJar = args[1];
		
		String dexes = null;
		if (args.length > 2)
		{
			dexes = args[2];
		}
		
		String apkName = apkPath;
		if (apkName.contains("/"))
		{
			apkName = apkName.substring(apkName.lastIndexOf('/')+1);
		}
		
		if (! new File(GlobalRef.WORKSPACE).exists())
		{
			File workspace = new File(GlobalRef.WORKSPACE);
			workspace.mkdirs();
		}
		
		init(apkPath, forceAndroidJar, dexes);
		
		long afterDummyMain = System.currentTimeMillis();
		System.out.println("==>TIME:" + afterDummyMain);
		
		reflectionAnalysis();
		toReadableText(apkName);
		toJson();
		
		long afterRA = System.currentTimeMillis();
		System.out.println("==>TIME:" + afterRA);
		
		booster();
		
		long afterBooster = System.currentTimeMillis();
		System.out.println("==>TIME:" + afterBooster);
		
		System.out.println("====>TIME_TOTAL:" + startTime + "," + afterDummyMain + "," + afterRA + "," + afterBooster);
	}
	
	public static int test()
	{
		return (int) new Object();
	}
	
	public static void init(String apkPath, String forceAndroidJar, String additionalDexes)
	{
		DroidRAUtils.extractApkInfo(apkPath);	
		GlobalRef.clsPath = forceAndroidJar;

		if (null != additionalDexes)
		{
			RetargetWithDummyMainGenerator.retargetWithDummyMainGeneration(apkPath, forceAndroidJar, GlobalRef.WORKSPACE, additionalDexes.split(File.pathSeparator));
		}
		else
		{
			RetargetWithDummyMainGenerator.retargetWithDummyMainGeneration(apkPath, forceAndroidJar, GlobalRef.WORKSPACE);
		}
	}
	
	public static void reflectionAnalysis()
	{
		
		String[] args = {
			"-cp", GlobalRef.clsPath,
			"-model", GlobalRef.coalModelPath,
			"-input", GlobalRef.WORKSPACE
		};
		
		ArrayVarItemTypeRef.setup(GlobalRef.apkPath, GlobalRef.clsPath);
		GlobalRef.arrayTypeRef = ArrayVarItemTypeRef.arrayTypeRef;
		
		DroidRAAnalysis<DefaultCommandLineArguments> analysis = new DroidRAAnalysis<>();
		DefaultCommandLineParser parser = new DefaultCommandLineParser();
		DefaultCommandLineArguments commandLineArguments =
		    parser.parseCommandLine(args, DefaultCommandLineArguments.class);
		if (commandLineArguments != null) 
		{
			AndroidMethodReturnValueAnalyses.registerAndroidMethodReturnValueAnalyses("");
			analysis.performAnalysis(commandLineArguments);
		}
		
		GlobalRef.uniqStmtKeyValues = DroidRAResult.toUniqStmtKeyValues(HeuristicUnknownValueInfer.getInstance().infer(DroidRAResult.stmtKeyValues));
		
		ReflectionProfile.fillReflectionProfile(DroidRAResult.stmtKeyValues);
		GlobalRef.rClasses = ReflectionProfile.rClasses;
		ReflectionProfile.dump();
		ReflectionProfile.dump("==>0:");
	}
	
	public static void booster()
	{
		ApkBooster.apkBooster(GlobalRef.apkPath, GlobalRef.clsPath, GlobalRef.WORKSPACE);
	}
	
	public static void toReadableText(String apkName)
	{
		try 
		{
			PrintStream systemPrintStream = System.out;
					
			PrintStream fileStream = new PrintStream(new File("droidra_" + apkName + "_" + GlobalRef.pkgName + "_v" + GlobalRef.apkVersionCode + ".txt"));
			System.setOut(fileStream);
			
			System.out.println("The following values were found:");
		    for (Result result : DroidRAResultProcessor.results) 
		    {
		    	((DefaultResult) result).dump();
		    }
			
			System.setOut(systemPrintStream);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}
	
	public static void toJson()
	{
		String jsonFilePath = GlobalRef.jsonFile;
		
		Gson gson = new Gson();
		
		ReflectionExchangable re = new ReflectionExchangable();
		re.set(GlobalRef.uniqStmtKeyValues);
		
		try 
		{
			FileWriter fileWriter = new FileWriter(jsonFilePath);
			fileWriter.write(gson.toJson(re));
			
			fileWriter.flush();
			fileWriter.close();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	public static void loadJsonBack()
	{
		String jsonFilePath = GlobalRef.jsonFile;
		
		Gson gson = new Gson();
		
		try 
		{
			BufferedReader reader = new BufferedReader(new FileReader(jsonFilePath));
			ReflectionExchangable re = gson.fromJson(reader, ReflectionExchangable.class);
			
			Map<UniqStmt, StmtValue> map = re.get();
           	
           	for (Map.Entry<UniqStmt, StmtValue> entry : map.entrySet())
           	{
           		System.out.println(entry.getKey().className);
           		System.out.println("    " + entry.getValue());
           	}
           	
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
}
