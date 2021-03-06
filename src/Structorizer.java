/*
    Structorizer
    A little tool which you can use to create Nassi-Shneiderman Diagrams (NSD)

    Copyright (C) 2009  Bob Fisch

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any
    later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/******************************************************************************************************
 *
 *      Author:         Bob Fisch
 *
 *      Description:    Structorizer class (main entry point for interactive and batch mode)
 *
 ******************************************************************************************************
 *
 *      Revision List
 *
 *      Author          Date            Description
 *      ------          ----            -----------
 *      Bob Fisch       2007-12-27      First Issue
 *      Kay Gürtzig     2015-12-16      Bugfix #63 - no open attempt without need
 *      Kay Gürtzig     2016-04-28      First draft for enh. #179 - batch generator mode (KGU#187)
 *      Kay Gürtzig     2016-05-03      Prototype for enh. #179 - incl. batch parser and help (KGU#187)
 *      Kay Gürtzig     2016-05-08      Issue #185: Capability of multi-routine import per file (KGU#194)
 *      Kay Gürtzig     2016-12-02      Enh. #300: Information about updates on start in interactive mode
 *                                      Modification in command-line concatenation
 *      Kay Gürtzig     2016-12-12      Issue #306: multiple arguments in simple command line are now
 *                                      interpreted as several files to be opened in series.
 *      Kay Gürtzig     2017-01-27      Issue #306 + #290: Support for Arranger files in command line
 *      Kay Gürtzig     2017-03-04      Enh. #354: Configurable set of import parsers supported now
 *      Kay Gürtzig     2017-04-27      Enh. #354: Verbose option (-v with log directory) for batch import
 *      Kay Gürtzig     2017-07-02      Enh. #354: Parser-specific options retrieved from Ini, parser cloned.
 *      Kay Gürtzig     2017-11-06      Issue #455: Loading of argument files put in a sequential thread to overcome races
 *      Kay Gürtzig     2018-03-21      Issue #463: Logging configuration via file logging.properties
 *      Kay Gürtzig     2018-06-07      Issue #463: Logging configuration mechanism revised (to support WebStart)
 *      Kay Gürtzig     2018-06-08      Issue #536: Precaution against command line argument trouble
 *      Kay Gürtzig     2018-06-12      Issue #536: Experimental workaround for Direct3D trouble
 *      Kay Gürtzig     2018-06-25      Issue #551: No message informing about version check option on WebStart
 *      Kay Gürtzig     2018-07-01      Bugfix #554: Parser selection and instantiation for batch parsing was defective.
 *      Kay Gürtzig     2018-07-03      Bugfix #554: Now a specified parser will override the automatic search.
 *      Kay Gürtzig     2018-08-17      Help text for parser updated (now list is from parsers.xml).
 *      Kay Gürtzig     2018-08-18      Bugfix #581: Loading of a list of .nsd/.arr/.arrz files as command line argument
 *      Kay Gürtzig     2018-09-14      Issue #537: Apple-specific code revised such that build configuration can handle it
 *      Kay Gürtzig     2018-09-19      Bugfix #484/#603: logging setup extracted to method, ini dir existence ensured
 *      Kay Gürtzig     2018-09-27      Slight modification to verbose option (-v may also be used without argument)
 *      Kay Gürtzig     2018-10-08      Bugfix #620: Logging path setup revised
 *      Kay Gürtzig     2018-10-25      Enh. #416: New option -l maxlen for command line parsing, signatures of
 *                                      export(...) and parse(...) modified.
 *
 ******************************************************************************************************
 *
 *      Comment:
 *
 ******************************************************************************************************///

import java.awt.EventQueue;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import lu.fisch.structorizer.application.ApplicationFactory;
import lu.fisch.structorizer.elements.Root;
import lu.fisch.structorizer.generators.Generator;
import lu.fisch.structorizer.generators.XmlGenerator;
import lu.fisch.structorizer.gui.Mainform;
import lu.fisch.structorizer.helpers.GENPlugin;
import lu.fisch.structorizer.io.Ini;
import lu.fisch.structorizer.parsers.CodeParser;
import lu.fisch.structorizer.parsers.GENParser;
import lu.fisch.structorizer.parsers.NSDParser;
import lu.fisch.utils.StringList;

public class Structorizer
{

	// entry point
	public static void main(String args[])
	{
		// START KGU#484 2018-03-21: Issue #463 - Configurability of the logging system ensured
		setupLogging();
		// END KGU#484 2018-03-21
		// START KGU#187 2016-04-28: Enh. #179
		Vector<String> fileNames = new Vector<String>();
		String generator = null;
		String parser = null;
		String switches = "";
		//String outFileName = null;
		//String charSet = "UTF-8";
		// START KGU#354 2017-04-27: Enh. #354
		//String logDir = null;
		// END KGU#354 2017-04-27
		// START KGU#538 2018-07-01: issue #554
		//String settingsFile = null;
		// END KGU#538 2018-07-01
		HashMap<String, String> options = new HashMap<String, String>();
		//System.out.println("arg 0: " + args[0]);
		if (args.length == 1 && args[0].equals("-h"))
		{
			printHelp();
			return;
		}
		for (int i = 0; i < args.length; i++)
		{
			//System.out.println("arg " + i + ": " + args[i]);
			if (i == 0 && args[i].equals("-x") && args.length > 1)
			{
				generator = args[++i];
			}
			else if (i == 0 && args[i].equals("-p") && args.length > 1)
			{
				parser = "*";
				// START KGU#538 2018-07-01: Bugfix #554 - was nonsense and had to be replaced
				// In order to disambiguate alternative parsers for the same file type, there
				// might be a given parser or language name again (3.28-05).
				if (i+2 < args.length) {
					// Legacy support - parsers will now be derived from the file extensions 
					if (args[i+1].equalsIgnoreCase("pas") || args[i+1].equalsIgnoreCase("pascal")) {
						parser = "D7Parser";
					}
					else if (!args[i+1].startsWith("-")) {
						// doesn't seem to be an option, so it might be a default parser name...
						parser = args[i+1];
					}
				}
				// END KGU#538 2018-07-01
			}
			// START KGU#538 2018-07-01: Bugfix #554 - was nonsense and had to be replaced 
			// Legacy support - parsers will now be derived from the file extensions 
			//else if (i > 0 && (parser != null) && (args[i].equalsIgnoreCase("pas") || args[i].equalsIgnoreCase("pascal"))
			//		&& !parser.endsWith("pas")) {
			//	parser += "pas";
			//}
			// END KGU#538 2018-07-01
			else if (args[i].equals("-o") && i+1 < args.length)
			{
				// Output file name
				//outFileName = args[++i];
				options.put("outFileName", args[++i]);
			}
			else if (args[i].equals("-e") && i+1 < args.length)
			{
				// Encoding
				//charSet = args[++i];
				options.put("charSet", args[++i]);
			}
			// START KGU#354 2017-04-27: Enh. #354 verbose mode?
			else if (args[i].equals("-v") && i+1 < args.length)
			{
				// START KGU#354 2018-09-27: More tolerance spent
				//logDir = args[++i];
				String dirName = args[i+1]; 
				if (dirName.startsWith("-") || !(new File(dirName)).isDirectory()) {
					// No valid path given, so use "."
					//logDir = ".";
					options.put("logDir", ".");
				}
				else {
					//logDir = args[++i];
					options.put("logDir", "args[++i]");
				}
				// END KGU#354 2018-09-27
			}
			// END KGU#354 2017-04-27
			// START KGU#538 2018-07-01: Issue #554 - new option for a settings file
			else if (args[i].equals("-s") && i+1 < args.length)
			{
				//settingsFile = args[++i];
				options.put("settingsFile", args[++i]);
			}
			// END KGU#538 2018-07-01
			// START KGU#602 2018-10-25: Enh. #416
			else if (args[i].equals("-l") && parser != null && i+1 < args.length) {
				try {
					short maxLen = Short.parseShort(args[i+1]);
					if (maxLen == 0 || maxLen >= 20) {
						options.put("maxLineLength", args[++i]);
					}
				}
				catch (NumberFormatException ex) {}
			}
			// END KGU#602 2018-10-25
			// Target standard output?
			else if (args[i].equals("-")) {
				switches += "-";
			}
			// Other options
			else if (args[i].startsWith("-")) {
				switches += args[i].substring(1);
			}
			else
			{
				fileNames.add(args[i]);
			}
		}
		if (generator != null)
		{
			//Structorizer.export(generator, fileNames, outFileName, switches, charSet, null);
			Structorizer.export(generator, fileNames, options, switches);
			return;
		}
		else if (parser != null)
		{
			// START KGU#354 2017-04-27: Enh. #354 verbose mode
			//Structorizer.parse(parser, fileNames, outFileName, options, charSet);
			//Structorizer.parse(parser, fileNames, outFileName, switches, charSet, settingsFile, logDir);
			Structorizer.parse(parser, fileNames, options, switches);
			// END KGU#354 2017-04-27
			return;
		}
		// END KGU#187 2016-04-28
		
		// START KGU#521 2018-06-12: Workaround for #536 (corrupted rendering on certain machines) 
		System.setProperty("sun.java2d.noddraw", "true");
		// END KGU#521 2018-06-12

		// try to load the system Look & Feel
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch(Exception e)
		{
			//System.out.println("Error setting native LAF: " + e);
		}

		// load the mainform
		final Mainform mainform = new Mainform();
		
		// START KGU#532 2018-06-25: Issue #551 Suppress version notification option hint
		String appPath = getApplicationPath();
		mainform.isWebStart = appPath.endsWith("webstart");
		// END KGU#532 2018-06-25
		// START KGU#440 2017-11-06: Issue #455 Decisive measure against races on loading an drawing
        try {
        	EventQueue.invokeAndWait(new Runnable() {
        		@Override
        		public void run() {
        // END KGU#440 2017-11-06
        			//String s = new String();
        			int start = 0;
        			if (args.length > 0 && args[0].equals("-open")) {
        				start = 1;
        			}
        			// If there are several .nsd, .arr, or .arrz files as arguments, then try to load
        			// them all ...
    				String lastExt = "";	// Last file extension
        			for (int i=start; i<args.length; i++)
        			{
        				// START KGU#306 2016-12-12/2017-01-27: This seemed to address file names with blanks...
        				//s += args[i];
        				String s = args[i].trim();
        				if (!s.isEmpty())
        				{
        					if (lastExt.equals("nsd") && !mainform.diagram.getRoot().isEmpty()) {
        						// Push the previously loaded diagram to Arranger
        						mainform.diagram.arrangeNSD();
        					}
        					lastExt = mainform.diagram.openNsdOrArr(s);
        					// START KGU#521 2018-06-08: Bugfix #536 (try)
        					if (lastExt == "") {
        						String msg = "Unsuited or misplaced command line argument \"" + s + "\" ignored.";
        						Logger.getLogger(Structorizer.class.getName()).log(Level.WARNING, msg);
        						JOptionPane.showMessageDialog(mainform, msg,
        								"Command line", JOptionPane.WARNING_MESSAGE);
        					}
        					// END KGU#521 2018-06-08
        				}
        				// END KGU#306 2016-12-12/2017-01-27
        			}
       	// START KGU#440 2017-11-06: Issue #455 Decisive measure against races on loading an drawing
        		}
        		// START KGU#306 2016-12-12: Enh. #306 - Replaced with the stuff in the loop above
//			s = s.trim();
//			// START KGU#111 2015-12-16: Bugfix #63 - no open attempt without need
//			//mainform.diagram.openNSD(s);
//			if (!s.isEmpty())
//			{
//				mainform.diagram.openNSD(s);
//			}
//			// END KGU#111 2015-12-16
        	// END KGU#306 2016-12-12
        	});
        } catch (InvocationTargetException e1) {
        	e1.printStackTrace();
        } catch (InterruptedException e1) {
        	e1.printStackTrace();
        }
        // END KGU#440 2017-11-06
        mainform.diagram.redraw();

        if(System.getProperty("os.name").toLowerCase().startsWith("mac os x"))
        {
        	// KGU 2018-09-14: Issue #537
        	ApplicationFactory.getApplication("lu.fisch.structorizer.application.AppleStructorizerApplication").configureFor(mainform);
        }

		// Without this, the toolbar had often wrong status when started from a diagram 
		mainform.doButtons();
		// START KGU#300 2016-12-02
		mainform.popupWelcomePane();
		// END KGU#300 2016-12-02
	}

	// START KGU#579 2018-09-19: Logging setup extracted on occasion of a #463 fix
	/**
	 * Initializes the logging system and tries to ensure a product-specific
	 * default logging configuration in the anticipated Structorizer ini directory
	 */
	private static void setupLogging() {
		// The logging configuration (for java.util.logging) is expected next to the jar file
		// (or in the project directory while debugged from the IDE).
		File iniDir = Ini.getIniDirectory();
		File configFile = new File(iniDir.getAbsolutePath(), "logging.properties");
		// If the file doesn't exist then we'll copy it from the resource
		if (!configFile.exists()) {
			InputStream configStr = Structorizer.class.getResourceAsStream("/lu/fisch/structorizer/logging.properties");
			if (configStr != null) {
				// START KGU#579 2018-09-19: Bugfix #603 On the very first use of Structorizer, iniDir won't exist (Ini() hasn't run yet)
				//copyStream(configStr, configFile);
				try {
					// START KGU#595 2018-10-07: Bugfix #620 - We ought to check the success 
					//if (!iniDir.exists())
					//{
					//	iniDir.mkdir();
					//}
					//copyStream(configStr, configFile);
					if (!iniDir.exists() &&	!iniDir.mkdirs()) {
						System.err.println("*** Creation of folder \"" + iniDir + "\" failed!");
						iniDir = new File(System.getProperty("user.home"));
						configFile = new File(iniDir.getAbsolutePath(), "logging.properties");
					}
					copyLogProperties(configStr, configFile);
					// END KGU#595 2018-10-07
				}
				catch (Exception ex) {
					ex.printStackTrace();
					try {
						configStr.close();
					} catch (IOException e) {}
				}
				// END KGU#579 2018-09-19
			}
		}
		if (configFile.exists()) {
			System.setProperty("java.util.logging.config.file", configFile.getAbsolutePath());
			try {
				LogManager.getLogManager().readConfiguration();
			} catch (SecurityException | IOException e) {
				// Just write the trace to System.err
				e.printStackTrace();
			}
		}
		// START KGU#484 2018-04-05: Issue #463 - If the copy attempt failed too, try to leave a note..,
		else {
			File logLogFile = new File(iniDir.getAbsolutePath(), "Structorizer.log");
			try {
				// Better check twice, we may not know at what point the first try block failed...
				if (!iniDir.exists())
				{
					iniDir.mkdir();
				}
				OutputStreamWriter logLog =	new OutputStreamWriter(new FileOutputStream(logLogFile), "UTF-8");
				logLog.write("No logging config file in dir " + iniDir + " - using Java logging standard.");
				logLog.close();
			} catch (IOException e) {
				// Just write the trace to System.err
				e.printStackTrace();
			}
		}
		// END KGU#484 2018-04-05
		//System.out.println(System.getProperty("java.util.logging.config.file"));
	}
	// END KGU#579 2018-09-19
	
	// START KGU#187 2016-05-02: Enh. #179
	private static final String[] synopsis = {
		"Structorizer [NSDFILE|ARRFILE|ARRZFILE]",
		"Structorizer -x GENERATOR [-a] [-b] [-c] [-f] [-l] [-t] [-e CHARSET] [-] [-o OUTFILE] NSDFILE...",
		"Structorizer -p [PARSER] [-f] [-v [LOGPATH]] [-l MAXLINELEN] [-e CHARSET] [-s SETTINGSFILE] [-o OUTFILE] SOURCEFILE...",
		"Structorizer -h"
	};
	// END KGU#187 2016-05-02
	
	// START KGU#187 2016-04-28: Enh. #179
	/*****************************************
	 * batch code export method
	 * @param _generatorName - name of the target language or generator class
	 * @param _nsdFileNames - vector of the diagram file names
	 * @param _options - map of non-binary command line options
	 * @param _switches - set of switches (on / off)
	 *****************************************/
	public static void export(String _generatorName, Vector<String> _nsdFileNames, HashMap<String, String> _options, String _switches)
	{
		Vector<Root> roots = new Vector<Root>();
		String codeFileName = _options.get("outFileName");
		// the encoding to be used. 
		String charSet = _options.getOrDefault("charSet", "UTF-8");
		// path of a property file to be preferred over structorizer.ini
		//String _settingsFileName = _options.get("settingsFile");
		for (String fName : _nsdFileNames)
		{
			Root root = null;
			try
			{
				// Test the existence of the current NSD file
				File f = new File(fName);
				if (f.exists())
				{
					// open an existing file
					NSDParser parser = new NSDParser();
					// START KGU#363 2017-05-21: Issue #372 API change
					//root = parser.parse(f.toURI().toString());
					root = parser.parse(f);
					// END KGU#363 2017-05-21
					root.filename = fName;
					roots.add(root);
					// If no output file name is given then derive one from the first NSD file
					if (codeFileName == null && _switches.indexOf('-') < 0)
					{
						codeFileName = f.getCanonicalPath();
					}
				}
				else
				{
					System.err.println("*** File " + fName + " not found. Skipped.");
				}
			}
			catch (Exception e)
			{
				System.err.println("*** Error while trying to load " + fName + ": " + e.getMessage());
			}
		}
		
		String genClassName = null;
		if (!roots.isEmpty())
		{
			String usage = "Usage: " + synopsis[1] + "\nwith GENERATOR =";
			// We just (ab)use some class residing in package gui to fetch the plugin configuration 
			BufferedInputStream buff = new BufferedInputStream(lu.fisch.structorizer.gui.EditData.class.getResourceAsStream("generators.xml"));
			GENParser genp = new GENParser();
			Vector<GENPlugin> plugins = genp.parse(buff);
			try { buff.close();	} catch (IOException e) {}
			for (int i=0; genClassName == null && i < plugins.size(); i++)
			{
				GENPlugin plugin = (GENPlugin) plugins.get(i);
				StringList names = StringList.explode(plugin.title, "/");
				String className = plugin.getKey();
				usage += (i>0 ? " |" : "") + "\n\t" + className;
				if (className.equalsIgnoreCase(_generatorName))
				{
					genClassName = plugin.className;
				}
				else
				{
					for (int j = 0; j < names.count(); j++)
					{
						if (names.get(j).trim().equalsIgnoreCase(_generatorName))
							genClassName = plugin.className;
						usage += " | " + names.get(j).trim();
					}
				}
			}

			if (genClassName == null)
			{
				System.err.println("*** Unknown code generator \"" + _generatorName + "\"");
				System.err.println(usage);
				System.exit(1);
			}
			
			try
			{
				Class<?> genClass = Class.forName(genClassName);
				Generator gen = (Generator) genClass.newInstance();
				gen.exportCode(roots, codeFileName, _switches, charSet);
			}
			catch(java.lang.ClassNotFoundException ex)
			{
				System.err.println("*** Generator class " + ex.getMessage() + " not found!");
				System.exit(3);
			}
			catch(Exception e)
			{
				System.err.println("*** Error while using " + _generatorName + "\n" + e.getMessage());
				e.printStackTrace();
				System.exit(4);
			}
		}
		else
		{
			System.err.println("*** No NSD files for code generation.");
			System.exit(2);
		}
	}
	// END KGU#187 2016-04-28
	
	// START KGU#187 2016-04-29: Enh. #179 - for symmetry reasons also allow a parsing in batch mode
	/*****************************************
	 * batch code import method
	 * @param _parserName - name of a preferred default parser (just for the case of ambiguity)
	 * @param _filenames - names of the files to be imported
	 * @param _options - map of non-binary command line parameters
	 * @param _switches - set of switches (either on or off)
	 *****************************************/
	private static void parse(String _parserName, Vector<String> _filenames, HashMap<String, String> _options, String _switches)
	{
		
		String usage = "Usage: " + synopsis[2] + "\nAccepted file extensions:";
		// base name of the nsd file(s) to be created
		String outFile = _options.get("outFileName"); 
		// the encoding to be assumed or used 
		String charSet = _options.getOrDefault("charSet", "UTF-8");
		// path of a property file to be preferred against structorizer.ini
		String settingsFileName = _options.get("settingsFile");
		// Path of the target folder for the parser log
		String _logDir = _options.get("logDir");

		Vector<GENPlugin> plugins = null;
		String fileExt = null;
		// START KGU#354 2017-03-10: Enh. #354 configurable parser plugins
		// Initialize the mapping file extensions -> CodeParser
		// We just (ab)use some class residing in package gui to fetch the plugin configuration 
		BufferedInputStream buff = new BufferedInputStream(lu.fisch.structorizer.gui.EditData.class.getResourceAsStream("parsers.xml"));
		try {
			GENParser genp = new GENParser();
			plugins = genp.parse(buff);
		}
		finally {
			try { buff.close();	} catch (IOException e) {}
		}
		HashMap<CodeParser, GENPlugin> parsers = new HashMap<CodeParser, GENPlugin>();
		//String parsClassName = null;
		CodeParser specifiedParser = null;
		for (int i=0; i < plugins.size(); i++)
		{
			GENPlugin plugin = (GENPlugin) plugins.get(i);
			String className = plugin.className;
			try
			{
				Class<?> parsClass = Class.forName(className);
				CodeParser parser = (CodeParser) parsClass.newInstance();
				parsers.put(parser, plugin);
				usage += "\n\t";
				for (String ext: parser.getFileExtensions()) {
					usage += ext + ", ";
				}
				// Get rid of last ", " 
				if (usage.endsWith(", ")) {
					usage = usage.substring(0, usage.length()-2) + " for " + parser.getDialogTitle();
				}
				// START KGU#538 2018-07-01: Bugfix #554
				if (_parserName.equalsIgnoreCase(plugin.getKey()) 
						|| _parserName.equalsIgnoreCase(parser.getDialogTitle())
						|| _parserName.equalsIgnoreCase(plugin.title)) {
					specifiedParser = parser;
				}
				// END KGU#538 2018-07-01
			}
			catch(java.lang.ClassNotFoundException ex)
			{
				System.err.println("*** Parser class " + ex.getMessage() + " not found!");
			}
			catch(Exception e)
			{
				// START KGU#538 2018-07-01: Bugfix #554 
				//System.err.println("*** Error on creating " + _parserName);
				System.err.println("*** Error on creating " + className);
				// END KGU#538 2018-07-01
				e.printStackTrace();
			}
		}
		// END KGU#354 2017-03-10
		// START KGU#538 2018-07-03: Bugfix #554
		if (!_parserName.equals("*") && specifiedParser == null) {
			System.err.println("*** No parser \"" + _parserName+ "\" found! Trying standard parsers.");
		}
		// END KGU#538 2018-07-03
		
		// START KGU#193 2016-05-09: Output file name specification was ignored, option f had to be tuned.
		boolean overwrite = _switches.indexOf("f") >= 0 && 
				!(outFile != null && !outFile.isEmpty() && _filenames.size() > 1);
		// END KGU#193 2016-05-09
		
		// START KGU#354 2017-03-03: Enh. #354 - support several parser plugins
		// While there was only one input language candidate, a single Parser instance had been enough
		//D7Parser d7 = new D7Parser("D7Grammar.cgt");
		// END KGU#354 2017-03-04

		// START KGU#538 2018-07-01: Bugfix #554 - for the case there are alternatives
		Vector<CodeParser> suitedParsers = new Vector<CodeParser>();
		// END KGU#538 2018-07-01
		for (String filename : _filenames)
		{
			// START KGU#538 2018-07-04: Bugfix #554 - the 1st "filename" might be the parser name
			if (specifiedParser != null && filename.equals(_parserName)) {
				continue;
			}
			// END KGU#538 2018-07-04
			// START KGU#194 2016-05-08: Bugfix #185 - face more contained roots
			//Root rootNew = null;
			List<Root> newRoots = new LinkedList<Root>();
			// END KGU#194 2016-05-08
			// START KGU#354 2017-03-04: Enh. #354
			//if (fileExt.equals("pas"))
			File importFile = new File(filename);
			// START KGU#538 2018-07-01: Bugfix #554
			suitedParsers.clear();
			// END KGU#538 2018-07-01
			CodeParser parser = specifiedParser;
			// If the parser wasn't specified explicitly then search for suited parsers
			if (parser == null) {
				// START KGU#416 2017-07-02: Enh. #354, #409 Parser retrieval combined with option retrieval
				for (Entry<CodeParser, GENPlugin> entry: parsers.entrySet()) {
					if (entry.getKey().accept(importFile)) {
						// START KGU#538 2018-07-01: Bugfix #554
						//parser = cloneWithPluginOptions(entry.getValue());
						//break;
						// If the preferred parser is among the suited ones, select it
						if (entry.getKey() == specifiedParser) {
							parser = specifiedParser;
							break;
						}
						suitedParsers.add(entry.getKey());
						// END KGU#538 2018-07-01
					}
				}
				// START KGU#538 2018-07-01: Bugfix #554
				if (parser == null) {
					parser = disambiguateParser(suitedParsers, filename);
				}
				// END KGU#538 2018-07-01
				// END KGU#416 2017-07-02
				if (parser == null) {
					System.out.println("--- File \"" + filename + "\" skipped (not accepted by any parser)!");
					continue;
				}
				// END KGU#354 2017-03-09
			}
			// START KGU#538 2018-07-01: Bugfix #554
			System.out.println("--- Processing file \"" + filename + "\" with " + parser.getClass().getSimpleName() + " ...");
			// Unfortunately, CodeParsers aren't reusable, so we better create a new instance in any case.
			parser = cloneWithPluginOptions(parsers.get(parser), settingsFileName);
			// END KGU#538 2018-07-01
			// START KGU#602 2018-10-25: Issue #416
			if (_options.containsKey("maxLineLength")) {
				try {
					short maxLineLength = Short.parseShort(_options.get("maxLineLength"));
					if (maxLineLength >= 0) {
						parser.optionMaxLineLength = maxLineLength;
					}
				}
				catch (NumberFormatException ex) {}		
			}
			// END KGU#602 2018-10-25
			// START KGU#194 2016-05-04: Bugfix for 3.24-11 - encoding wasn't passed
			// START KGU#354 2017-04-27: Enh. #354 pass in the log directory path
			//newRoots = parser.parse(filename, _charSet);
			newRoots = parser.parse(filename, charSet, _logDir);
			// END KGU#354 2017-04-27
			// END KGU#194 2016-05-04
			if (!parser.error.isEmpty())
			{
				System.err.println("*** Parser error in file \"" + filename + "\":\n" + parser.error);
				continue;
			}

			// Now save the roots as NSD files. Derive the target file names from the source file name
			// if _outFile isn't given.
			// START KGU#193 2016-05-09: Output file name specification was ignred, optio f had to be tuned.
			if (outFile != null && !outFile.isEmpty())
			{
				filename = outFile;
			}
			// END KGU#193 2016-05-09
			overwrite = writeRootsToFiles(newRoots, filename, fileExt, overwrite);
		}
	}
	// END KGU#187 2016-04-29

	// START KGU#538 2018-07-01: Bugfix #554
	/**
	 * Generates the nsd files from the given list of {@link Root}s
	 * @param newRoots - list of generated {@link Root}s
	 * @param filename - the base file name for the resulting nsd files
	 * @param fileExt - the file name extension to be used.
	 * @param overwrite
	 * @return
	 */
	private static boolean writeRootsToFiles(List<Root> newRoots, String filename, String fileExt, boolean overwrite)
	{
		// START KGU#194 2016-05-08: Bugfix #185 - face more contained roots
		//if (rootNew != null)
		boolean multipleRoots = newRoots.size() > 1;
		for (Root rootNew : newRoots)
		// END KGU#194 2016-05-08
		{
			StringList nameParts = StringList.explode(filename, "[.]");
			String ext = nameParts.get(nameParts.count()-1).toLowerCase();
			if (ext.equals(fileExt))
			{
				nameParts.set(nameParts.count()-1, "nsd");
			}
			else if (!ext.equals("nsd"))
			{
				nameParts.add("nsd");
			}
			// In case of multiple roots (subroutines) insert the routine's proposed file name
			if (multipleRoots && !rootNew.isProgram())
			{
				nameParts.insert(rootNew.proposeFileName(), nameParts.count()-1);
			}
			//System.out.println("File name raw: " + nameParts);
			if (!overwrite)
			{
				int count = 0;
				do {
					File file = new File(nameParts.concatenate("."));
					if (file.exists())
					{
						if (count == 0) {
							nameParts.insert(Integer.toString(count), nameParts.count()-1);
						}
						else {
							nameParts.set(nameParts.count()-2, Integer.toString(count));
						}
						count++;
					}
					else
					{
						overwrite = true;
					}
				} while (!overwrite);
			}
			String filenameToUse = nameParts.concatenate(".");
			//System.out.println("Writing to " + filename);
			try {
				FileOutputStream fos = new FileOutputStream(filenameToUse);
				Writer out = null;
				out = new OutputStreamWriter(fos, "UTF8");
				try {
					XmlGenerator xmlgen = new XmlGenerator();
					out.write(xmlgen.generateCode(rootNew,"\t"));
				}
				finally {
					out.close();
				}
			}
			catch (UnsupportedEncodingException e) {
				System.err.println("*** " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
			catch (IOException e) {
				System.err.println("*** " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
		return overwrite;
	}
	
	/**
	 * Chooses the parser to be used among the {@code suitedParsers}. If there are many
	 * then the user will be asked interactively.<br/>
	 * (Later there might be a change to get it from a configuration file.)
	 * @param suitedParsers - a vector of parsers accepting the file extension
	 * @param filename - name of the file to be parsed (for dialog purposes)
	 * @return a {@link CodeParser} instance if there was a valid choice or null 
	 */
	private static CodeParser disambiguateParser(Vector<CodeParser> suitedParsers, String filename)
	{
		CodeParser parser = null;
		if (suitedParsers.size() == 1) {
			parser = suitedParsers.get(0);
		}
		else if (suitedParsers.size() > 1) {
			System.out.println("Several suited parsers found for file \"" + filename + "\":");
			for (int i = 0; i < suitedParsers.size(); i++) {
				System.out.println((i+1) + ": " + suitedParsers.get(i).getDialogTitle());
			}
			int chosen = -1;
			Scanner scnr = new Scanner(System.in);
			try {
				while (chosen < 0) {
					System.out.print("Please select the number of your favourite (0 = skip file): ");
					String input = scnr.nextLine();
					try {
						chosen = Integer.parseInt(input);
						if (chosen < 0 || chosen > suitedParsers.size()) {
							System.err.println("*** Value out of range!");
							chosen = -1;
						}
					}
					catch (Exception ex) {
						System.err.println("*** Wrong number format!");
					}
				}
			}
			finally {
				scnr.close();
			}
			if (chosen != 0) {
				parser = suitedParsers.get(chosen-1);
			}
		}
		return parser;
	}
	// END KGUä538 2018-07-01
	
	// START KGU#416 2017-07-03: Enh. #354, #409
	private static CodeParser cloneWithPluginOptions(GENPlugin plugin, String _settingsFile) {
		CodeParser parser;
		try {
			// START KGU#538 2018-07-01: Bugfix #554 Instantioation failed (missing path)
			//parser = (CodeParser)Class.forName(plugin.getKey()).newInstance();
			parser = (CodeParser)Class.forName(plugin.className).newInstance();
			// END KGU#538 2018-07-01
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException ex) {
			System.err.println("Structorizer.CloneWithPluginSpecificOptions("
					+ plugin.getKey()
					+ "): " + ex.toString() + " on creating \"" + plugin.getKey()
					+ "\"");
			return null;
		}
		Ini ini = Ini.getInstance();
		if (!plugin.options.isEmpty()) {
			System.out.println("Retrieved parser-specific options:");
			try {
				// START KGU#538 2018-07-01: Issue #554 - a different properties file might be requested
				//ini.load();
				if (_settingsFile != null && (new File(_settingsFile)).canRead()) {
					ini.load(_settingsFile);
				}
				else {
					ini.load();
				}
				// END KGU#538 2018-07-01
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (HashMap<String, String> optionSpec: plugin.options) {
			String optionKey = optionSpec.get("name");
			String valueStr = ini.getProperty(plugin.getKey() + "." + optionKey, "");
			Object value = null;
			String type = optionSpec.get("type");
			String items = optionSpec.get("items");
			// Now convert the option into the specified type
			if (!valueStr.isEmpty() && type != null || items != null) {
				// Better we fail with just a single option than with the entire method
				try {
					if (items != null) {
						value = valueStr;
					}
					else if (type.equalsIgnoreCase("character")) {
						value = valueStr.charAt(0);
					}
					else if (type.equalsIgnoreCase("boolean")) {
						value = Boolean.parseBoolean(valueStr);
					}
					else if (type.equalsIgnoreCase("int") || type.equalsIgnoreCase("integer")) {
						value = Integer.parseInt(valueStr);
					}
					else if (type.equalsIgnoreCase("unsiged")) {
						value = Integer.parseUnsignedInt(valueStr);
					}
					else if (type.equalsIgnoreCase("double") || type.equalsIgnoreCase("float")) {
						value = Double.parseDouble(valueStr);
					}
					else if (type.equalsIgnoreCase("string")) {
						value = valueStr;
					}
				}
				catch (NumberFormatException ex) {
					System.err.println("*** Structorizer.CloneWithPluginSpecificOptions("
							+ plugin.getKey()
							+ "): " + ex.getMessage() + " on converting \""
							+ valueStr + "\" to " + type + " for " + optionKey);
				}
			}
			System.out.println(" + " + optionKey + " = " + value);
			if (value != null) {
				parser.setPluginOption(optionKey, value);
			}
		}
		if (!plugin.options.isEmpty()) {
			System.out.println("");
		}
		return parser;
	}
	// END KGU#416 2017-07-02

	// START KGU#187 2016-05-02: Enh. #179 - help might be sensible
	private static void printHelp()
	{
		System.out.print("Usage:\n");
		for (int i = 0; i < synopsis.length; i++)
		{
			System.out.println(synopsis[i]);
		}
		System.out.println("with");
		System.out.print("\tGENERATOR = ");
		// We just (ab)use some class residing in package gui to fetch the plugin configuration 
		BufferedInputStream buff = new BufferedInputStream(lu.fisch.structorizer.gui.EditData.class.getResourceAsStream("generators.xml"));
		GENParser genp = new GENParser();
		Vector<GENPlugin> plugins = genp.parse(buff);
		try { buff.close();	} catch (IOException e) {}
		for (int i=0; i < plugins.size(); i++)
		{
			GENPlugin plugin = (GENPlugin) plugins.get(i);
			StringList names = StringList.explode(plugin.title, "/");
			String className = plugin.getKey();
			System.out.print( (i>0 ? " |" : "") + "\n\t\t" + className );
			for (int j = 0; j < names.count(); j++)
			{
				System.out.print(" | " + names.get(j).trim());
			}
		}
		System.out.print("\n\tPARSER = ");
		// Again we (ab)use some class residing in package gui to fetch the plugin configuration 
		buff = new BufferedInputStream(lu.fisch.structorizer.gui.EditData.class.getResourceAsStream("parsers.xml"));
		genp = new GENParser();
		plugins = genp.parse(buff);
		try { buff.close();	} catch (IOException e) {}
		for (int i=0; i < plugins.size(); i++)
		{
			GENPlugin plugin = (GENPlugin) plugins.get(i);
			StringList names = StringList.explode(plugin.title, "/");
			String className = plugin.getKey();
			System.out.print( (i>0 ? " |" : "") + "\n\t\t" + className );
			for (int j = 0; j < names.count(); j++)
			{
				System.err.print(" | " + names.get(j).trim());
			}
		}
		System.out.println("");
	}
	// END KGU#187 2016-05-02
	
	/** @return the installation path of Structorizer */
    public static String getApplicationPath()
    {
        CodeSource codeSource = Structorizer.class.getProtectionDomain().getCodeSource();
        File rootPath = null;
        try {
            rootPath = new File(codeSource.getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }           
        return rootPath.getParentFile().getPath();
    }
		
// START KGU#595 2018-10-07: Bugfix #620 - we must adapt the log pattern
//	/**
//	 * Performs a bytewise copy of {@code sourceStrm} to {@code targetFile}. Closes
//	 * {@code sourceStrm} afterwards.
//	 * @param sourceStrm - opened source {@link InputStream}
//	 * @param targetFile - {@link File} object for the copy target
//	 * @return a string describing occurred errors or an empty string.
//	 */
//	private static String copyStream(InputStream sourceStrm, File targetFile) {
//		String problems = "";
//		final int BLOCKSIZE = 512;
//		byte[] buffer = new byte[BLOCKSIZE];
//		FileOutputStream fos = null;
//		try {
//			fos = new FileOutputStream(targetFile.getAbsolutePath());
//			int readBytes = 0;
//			do {
//				readBytes = sourceStrm.read(buffer);
//				if (readBytes > 0) {
//					fos.write(buffer, 0, readBytes);
//				}
//			} while (readBytes > 0);
//		} catch (FileNotFoundException e) {
//			problems += e + "\n";
//		} catch (IOException e) {
//			problems += e + "\n";
//		}
//		finally {
//			if (fos != null) {
//				try {
//					fos.close();
//				} catch (IOException e) {}
//			}
//			try {
//				sourceStrm.close();
//			} catch (IOException e) {}
//		}
//		return problems;
//	}
	
	/**
	 * Performs a linewise filtered copy of {@code sourceStrm} to {@code targetFile}. Closes
	 * {@code sourceStrm} afterwards.
	 * @param sourceStrm - opened source {@link InputStream}
	 * @param targetFile - {@link File} object for the copy target
	 * @return a string describing occurred errors or an empty string.
	 */
	private static String copyLogProperties(InputStream configStr, File configFile) {
		
		final String pattern = "java.util.logging.FileHandler.pattern = ";
		String problems = "";
		File configDir = configFile.getParentFile();
		File homeDir = new File(System.getProperty("user.home"));
		StringList pathParts = new StringList();
		while (configDir != null && homeDir != null) {
			if (homeDir.equals(configDir)) {
				pathParts.add("%h");
				homeDir = null;
			}
			else if (configDir.getName().isEmpty()) {
				pathParts.add(configDir.toString().replace(":\\", ":"));
			}
			else {
				pathParts.add(configDir.getName());
			}
			configDir = configDir.getParentFile();
		}
		String logPath = pathParts.reverse().concatenate("/") + "/structorizer%u.log";
		DataInputStream in = new DataInputStream(configStr);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(configFile);
			DataOutputStream out = new DataOutputStream(fos);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
			String line = null;
			try {
				while ((line = br.readLine()) != null) {
					if (line.startsWith(pattern)) {
						line = pattern + logPath;
					}
					bw.write(line + "\n");
				}
			} catch (IOException e) {
				problems += e + "\n";
				e.printStackTrace();
			}
			finally {
				if (fos != null) {
					bw.close();
				}
			}
		} catch (IOException e1) {
			problems += e1 + "\n";
			e1.printStackTrace();
		}
		finally {
			try {
				br.close();
			} catch (IOException e) {}
		}
		return problems;
	}
// END KGU#595 2018-10-07


}
