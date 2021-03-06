package commandproxy.cli;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;

import net.sf.image4j.codec.ico.ICOEncoder;

import org.xml.sax.SAXException;

import commandproxy.core.Constants;
import commandproxy.core.Log;
import commandproxy.core.PluginLoader;

import static commandproxy.cli.Tools.*; 

public class ExportWindows implements Constants{

	public static void export( File airFile, File setupFile, String plugins[] ) throws IOException, ParserConfigurationException, SAXException, InterruptedException{
		// Create a temp directory to work in
		System.out.println( "Creating temporary directory..." ); 
		File temp = File.createTempFile( "commandproxy", "" );
		temp.delete(); // Delete the file
		temp.mkdir();  // And create a directory with that name
		
		
		// Get access to the air-descriptor
		System.out.println( "Reading air descriptor..." ); 
		Hashtable<String, String> conf = getAirConfig( airFile );
		conf.put( "tempdir", temp.getAbsolutePath() ); 
		conf.put( "winrun4j", getCommandProxyFile( "files/windows/winrun4j" ).getAbsolutePath() );
		
		// Look for the output file
		if( setupFile == null ){
			setupFile = new File( conf.get( "filename" ) + "-" + conf.get( "version" ) + "-setup.exe" );
		}
		conf.put( "setupFile", setupFile.getAbsolutePath() ); 
		
		// If the icon exists export it... 
		try{
			// Get the new image path... 
			File target = new File( temp, conf.get( "icon/image128x128" ) );
			target.getParentFile().mkdirs(); 
			
			// Create an input stream for application.xml file
			ZipFile archive = new ZipFile( airFile );
			ZipEntry iconSrc = archive.getEntry( conf.get( "icon/image128x128" ) );
			InputStream in = archive.getInputStream( iconSrc );
			FileOutputStream out = new FileOutputStream( target ); 
			
			int len = 0; 
			byte buffer[] = new byte[4096]; 
			while( ( len = in.read( buffer ) ) > 0 ){
				out.write( buffer, 0, len ); 
			}
			
			// create multiple versions of the icon
			BufferedImage icon = ImageIO.read( target );
			Vector<BufferedImage> icons = new Vector<BufferedImage>();
			int[] sizes = new int[]{ 16, 24, 32, 48, 64, 128 };
			for( int size : sizes ){
				BufferedImage bim = new BufferedImage( size, size, BufferedImage.TYPE_INT_ARGB );
				Image small = icon.getScaledInstance( size, size, Image.SCALE_AREA_AVERAGING );
				bim.getGraphics().drawImage( small, 0, 0, null ); 
				icons.add( bim );
				small.flush();
			}
			
			// write the .ico file
			ICOEncoder.write( icon, new File( temp, "iconX.ico" ) );
			ICOEncoder.write( icons, new File( temp, "icon.ico" ) );
			in.close();
			out.close();
			
			// release it all! 
			for( BufferedImage bim : icons ){
				bim.flush(); 
			}
			
			icon.flush(); 
		}
		catch( Exception ex ){
			Log.error.println( "Icon " + conf.get( "icon/image128x128" ) + " could not be extracted" );
			ex.printStackTrace( Log.error ); 
		}
		
		// Create the windows launcher
		System.out.println( "Creating launcher..." );
		File launchFile = new File( temp, "launcher.jsmooth" );
		String jSmoothExe = getCommandProxyFile( "files/windows/jsmooth/jsmoothcmd.exe" ).getAbsolutePath();  
		
		copy( getCommandProxyFile( "files/windows/launcher.jsmooth" ), launchFile, conf );
		copy( getCommandProxyFile( "jars/commandproxy-launcher.jar" ), temp );
		
		ProcessHelper jSmooth = new ProcessHelper( temp, jSmoothExe, launchFile.getAbsolutePath() );
		if( jSmooth.getReturnCode() != 0 ){
			jSmooth.getException().printStackTrace( Log.error ); 
			Tools.fail( "Executable failed to build", E_EXPORT_FAILED );
		}
		
		// Unzip the .air file
		System.out.println( "Including contents from " + airFile.getName() ); 
		unzip( airFile, new File( temp, "air" ) ); 
		
		// Copy plugin files...
		File pluginDest = new File( temp, "plugins" );
		pluginDest.mkdir();
		
		if( getCommandProxyFile( "plugins" ).exists() ){
			System.out.println( "Copying plugins: " );
			Vector<File> available = PluginLoader.findJars( getCommandProxyFile( "plugins" ) );  
			
			if( plugins == null ){
				for( File plugin : available ){
					System.out.println( "> include " + plugin.getName() );
					copy( plugin, pluginDest );
				}
			}
			else{
				Arrays.sort( plugins ); 
				for( File plugin : available ){
					if( Arrays.binarySearch( plugins, plugin.getName() ) > 0 ){
						System.out.println( "> include " + plugin.getName() );
						copy( plugin, pluginDest );
					}
					else{
						System.out.println( "> skip " + plugin.getName() ); 
					}
				}
			}
		}
		else{
			System.out.println( "Skipping plugins, plugin-directory doesn't exist" ); 
		}
		
		// Create the window installer
		System.out.println( "Creating installer..." ); 
		File nsisFile = new File( temp, "installer.nsi" );
		String nsisExe = getCommandProxyFile( "files/windows/nsis/makensis.exe" ).getAbsolutePath(); 
		
		copy( getCommandProxyFile( "files/windows/installer.nsi" ), temp, conf );
		
		ProcessHelper nsis = new ProcessHelper( temp, nsisExe, nsisFile.getAbsolutePath() );
		if( nsis.getReturnCode() != 0 ){
			nsis.getException().printStackTrace( Log.error ); 
			Tools.fail( "Installer failed to build", E_EXPORT_FAILED ); 
		}
		
		// Done? Done! 
		//deleteDirectory( temp ); 
		System.out.println( "Success!" ); 
	}
}
