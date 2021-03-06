package commandproxy.cli;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import commandproxy.core.Constants;
import commandproxy.core.Log;

public class Tools {
	/**
	 * Prints information on how to use the commandproxy to the command line
	 */
	public static void printUsage(){
		System.out.println( 
			"Usage:\n" +
			"        commandproxy debug [-verbose]\n" + 
			"        commandproxy export windows|mac [-verbose] [-out=<out-file>] <air-file>\n" + 
			"\n" + 
			"\n" + 
			"Options: \n" + 
			"        -out:       Specify output file. For windows exports the .exe extension \n" + 
			"                    will be forced (for macintosh exports it's .dmg). \n" + 
			"                    By default <air-filename>-<version>.exe/dmg will be used. \n" +
			"                    \n" +
			"        -template:  Specify disk-image template. This only makes sense when \n" + 
			"                    export a Mac application. \n" + 
			"                    See the commandproxy/files/mac/howto.txt for more information\n" + 
			"                    \n" + 
			"        -plugins:   A list of plugins to be included. \n" +
			"                    When ommited all plugins will be included. \n" +
			"                    Multiple plugins can be separated using a coma (,). \n" + 
			"                    \n" + 
			"        -verbose:   Get more detailed output. This should be interresting only\n" +
			"                    if the build process fails\n"
		); 
	}
	
	
	
	/**
	 * Returns the home directory of the command proxy 
	 * installation
	 * 
	 * @return the home directory
	 */
	public static File getCommandProxyHome(){
		File home = new File( Main.class.getProtectionDomain().getCodeSource().getLocation().getFile() );
		
		// On windows spaces and other characters will be urlencode
		// (e.g. space=%20) 
		// Hm... we don't really want that to happen! 
		try {
			home = new File( URLDecoder.decode( home.getAbsolutePath(), "UTF-8" ) );
		}
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		if( home.isDirectory() ){
			return home.getParentFile(); 
		}
		else{
			return home.getParentFile().getParentFile(); 
		}
	}
	
	/**
	 * Locates a file inside the command proxy directory
	 * @param path The path relative to the commandproxy home dir
	 * @return A file with the correct absolute path set
	 */
	public static File getCommandProxyFile( String path ){
		return new File( getCommandProxyHome(), path ); 
	}
	
	/**
	 * Fails with an error message, 
	 * and then exits
	 * 
	 * @param message The error message
	 * @param code The error code, for a list of codes see {@link Constants}
	 */
	public static void fail( String message, int code ){
		Log.error.println( "Error: " ); 
		Log.error.println( message ); 
		Log.error.println(); 
		
		System.exit( code );
	}
	
	/**
	 * Reads the Adobe Air Application descriptor and places some 
	 * attributes in a properties object
	 * This will only give you access to these properties: 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 * @throws IOException 
	 * @throws ZipException 
	 */
	public static Hashtable<String, String> getAirConfig( File airFile ) throws ZipException, IOException, ParserConfigurationException, SAXException{
		Hashtable<String, String> conf = new Hashtable<String, String>(); 
		
		AirXML air = new AirXML( airFile );
		
		String[] interresting = new String[]{
				"version", 
				"filename", 
				"vendor", 
				"id", 
				"icon/image128x128"
		}; 
		
		for( String key : interresting ){
			conf.put( key, air.get( key ) ); 
		}
		
		if( "".equals( conf.get( "filename" ) ) )
			fail( "filename specified in the Air application descriptor is empty", Constants.E_AIR_FILE_INVALD ); 
		
		if( "".equals( conf.get( "id" ) ) )
			fail( "name specified in the Air application descriptor is empty", Constants.E_AIR_FILE_INVALD ); 
		
		/*if( "".equals( conf.get( "vendor" ) ) )
			fail( "vendor not specified in the Air application descriptor\n" + 
			      "you might want to add the following lines to your " + airFile.getName() + ": \n\n" + 
			      "<!--\n" + 
			      "vendor=My company\n" + 
			      "-->\n\n" + 
			      "The new lines are important, don't put everything on a single line!",
			      Constants.E_AIR_FILE_INVALD ); 
		*/
		if( conf.get( "filename" ).matches( "[^A-Za-z0-9 \\._-]+" ) )
			Log.warn.println( "Warning: filename specified in the Air application descriptor might contain non-trivial characters (" + conf.get("filename") + ")" );
		
		if( "".equals( conf.get( "icon/image128x128" ) ) )
			Log.warn.println( "Warning: icon/image128x128 not set in Air application descriptor" );  
		
		
		return conf;
	}
	
	/**
	 * Copies a file to a different place
	 * @throws IOException 
	 */
	public static void copy( File from, File to ) throws IOException{
		if( to.isDirectory() ){
			to = new File( to, from.getName() ); 
		}
		
		byte buffer[] = new byte[4096];
		int len = 0; 
		
		FileInputStream in = new FileInputStream( from );
		FileOutputStream out = new FileOutputStream( to ); 
		
		while( ( len = in.read( buffer ) ) > 0 ){
			out.write( buffer, 0, len ); 
		}
		
		in.close(); 
		out.close(); 
		
		return; 
	}
	
	/**
	 * Copies the contents fromDir to toDir. 
	 * toDir will be created if it doesn't exist. 
	 *  
	 * @param fromDir The source directory
	 * @param toDir The target directory
	 * @throws IOException 
	 */
	public static void copyDir( File fromDir, File toDir ) throws IOException{
		if( !toDir.exists() ){
			toDir.mkdirs(); 
		}
		
		for( File file : fromDir.listFiles() ){
			if( file.isDirectory() ){
				copyDir( file, new File( toDir.getAbsolutePath(), file.getName() ) );
			}
			else{
				copy( file, toDir ); 
			}
		}
	}
	
	
	/**
	 * Copies a file with property expansion
	 * 
	 * @throws IOException 
	 */
	public static void copy( File from, File to, Hashtable<String, String> props ) throws IOException{
		if( to.isDirectory() ){
			to = new File( to, from.getName() ); 
		}
		
		BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream( from ) ) ); 
		PrintWriter out = new PrintWriter( to ); 
		
		String line; 
		while( ( line = in.readLine() ) != null ){
			for( String key : props.keySet() ){
				line = line.replace( "${" + key + "}", props.get( key ) ); 
			}
			out.println( line ); 
		}
		
		in.close(); 
		out.close(); 
	}

	
	/**
	 * Unzips a file to a directory
	 * 
	 * @param A zip-compressed file
	 * @param destDir The destination directory
	 * @throws IOException 
	 * @throws ZipException 
	 */
	public static void unzip( File zipSrc, File destDir ) throws ZipException, IOException{
		ZipFile zip = new ZipFile( zipSrc );
		Enumeration<? extends ZipEntry> entries = zip.entries();
		
		// we'll need those later, definitely! 
		int len = 0; 
		byte buffer[] = new byte[4096]; 
		
		while( entries.hasMoreElements() ){
			ZipEntry entry = entries.nextElement(); 
			File destFile = new File( destDir.getAbsolutePath() + File.separator + entry.getName() ); 
			if( !destFile.getParentFile().exists() ){
				destFile.getParentFile().mkdirs(); 
			}
			
			InputStream in = zip.getInputStream( entry );
			FileOutputStream out = new FileOutputStream( destFile ); 
			while( ( len = in.read( buffer ) ) > 0 ){
				out.write( buffer, 0, len ); 
			}
			
			in.close();
			out.close(); 
		}
	}


	/**
	 * Copies the plugins-directory to a different place
	 * @throws IOException 
	 */
	public static Vector<File> find( File dir, FileFilter filter ){
		Vector<File> results = new Vector<File>(); 
		
		if( !dir.exists() )
			return results; 
		
		for( File file : dir.listFiles() ){
			if( file.isDirectory() ){
				results.addAll( find( file, filter ) );
			}
			else if( filter.accept( file ) ){
				results.add( file ); 
			}
		}
		
		return results; 
	}



	/**
	 * Deletes a directory recursively
	 * 
	 * @param path
	 * @return
	 */
	public static boolean deleteDirectory( File path ){
		if( path.exists() ){
			for( File file : path.listFiles() ){
				if( file.isDirectory() )
					deleteDirectory( file );
				else
					file.delete();
			}
		}
		
		return path.delete();
	}
	
	/**
	 * Runs a command, waits for it to finish and returns the error code
	 * @param baseDir The app's working directory
	 * @param args The program arguments. The first argument is the name of the executable
	 * @return 
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static ProcessHelper exec( File baseDir, String ... args ) throws IOException, InterruptedException{
		return new ProcessHelper( baseDir, args ); 
	}
	
	
	/**
	 * Changes the extension of a file name, 
	 * if no extension is present it will be appended.
	 *   
	 * @param file
	 * @param extension (without the ".")
	 * @return A new file with the extension changed
	 */
	public static File changeExtension( File file, String extension ){
		return new File( file.getParentFile(), getBaseName( file ) + "." + extension ); 
	}
	
	/**
	 * Finds the basename of a file (without the extension)
	 *   
	 * @param file The file
	 * @return The base name of the file
	 */
	public static String getBaseName( File file ){
		String name = file.getName(); 
		
		// Remove extension
		int pos = name.lastIndexOf( '.' ); 
		if( pos > 0 ){
			return name.substring( 0, pos );  
		}
		else{
			return name;  
		}
	}
	
	
	/**
	 * Passes an input stream to an outputstream
	 * 
	 * @author hansi
	 */
	public static class StreamConnector extends Thread{
		private InputStream in;
		private OutputStream out; 
		
		public StreamConnector( InputStream in, OutputStream out ){
			this.in = in;
			this.out = out; 
			start(); 
		}
		
		public void run(){
			try{
				byte buffer[] = new byte[4096]; 
				int len; 
				while( ( len = in.read( buffer ) ) > 0 ){
					out.write( buffer, 0, len ); 
				}
				
				in.close(); 
			}
			catch( IOException e ){
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * A little helper class that waits for a process to finsih
	 * and buffers all it's output
	 * 
	 * @author hansi
	 *
	 */
	public static class ProcessHelper{
		String in;
		String err;
		int returnCode; 
		
		String path; 
		File baseDir; 
		String[] args; 
		
		/**
		 * Runs a command, waits for it to finish and returns error and output stream. 
		 * 
		 * @param baseDir The app's working directory
		 * @param args The program arguments. The first argument is the name of the executable
		 * @return 
		 * @throws IOException 
		 * @throws InterruptedException 
		 */
		public ProcessHelper( File baseDir, String ... args ) throws IOException, InterruptedException{
			this.args = args; 
			this.baseDir = baseDir; 
			this.path = "PATH=" + System.getenv( "PATH" ) + File.pathSeparator + '"' + baseDir.getAbsolutePath() + '"'; 
			Process p = Runtime.getRuntime().exec( args, new String[]{ path }, baseDir );

			ByteArrayOutputStream errStream = new ByteArrayOutputStream();
			ByteArrayOutputStream inStream = new ByteArrayOutputStream();
			new StreamConnector( p.getInputStream(),  inStream );
			new StreamConnector( p.getErrorStream(), errStream );
			
			p.waitFor();
			
			this.returnCode = p.exitValue(); 
			this.in = inStream.toString(); 
			this.err = errStream.toString(); 
		}
		
		public String getError(){
			return err; 
		}
		
		public String getInput(){
			return in; 
		}
		
		public int getReturnCode(){
			return returnCode; 
		}
		
		/**
		 * In case you want to throw an exception because the 
		 * output or return code produced by this exception 
		 * didn't fulfill your expectations you can use this to generate 
		 * an exception that contains all information about the 
		 * process. 
		 * 
		 * @return
		 */
		public Exception getException(){
			String command = ""; 
			for( String arg : args )
				command += arg + " "; 
			
			return new Exception(
				"Execution failed: \n" + 
				"- Command: " + command + "\n" +  
				"- Path: " + path + "\n" + 
				"- Execution directory: " + baseDir + "\n" +
				"- Exit code: " + returnCode + "\n" + 
				"- Program output: \n" + in + "\n" + 
				"- Program error: \n" + err + "\n"
			); 
				
		}
	}
	
}
