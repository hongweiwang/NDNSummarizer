
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import org.ndnx.ndn.NDNHandle;
import org.ndnx.ndn.config.ConfigurationException;
import org.ndnx.ndn.config.UserConfiguration;
import org.ndnx.ndn.impl.support.Log;
import org.ndnx.ndn.io.NDNReader;
import org.ndnx.ndn.protocol.ContentName;
import org.ndnx.ndn.protocol.ContentObject;
import org.ndnx.ndn.protocol.Interest;
import org.ndnx.ndn.protocol.MalformedContentNameStringException;

public class CheckoutClient {
	private String DEFAULT_URI;
	private NDNHandle _handle;
	private NDNReader _reader;
	private String[] meta;
	private String rootDir;
	
	public CheckoutClient(String rootDir, String uri) {
		Log.setLevel(Log.FAC_ALL, Level.WARNING);
		this.DEFAULT_URI = uri;
		try {
			_handle = NDNHandle.open();
			_reader = new NDNReader(_handle);
			this.rootDir = rootDir;
			String metaStr = getFile("meta.txt");
			this.meta = metaStr.split("\n");
		} catch (ConfigurationException e) {
			System.out.println("NDNX configuration error!");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Cannot connect with server!");
			System.exit(1);
		}
	}

	public String getFile(String filename){
		ContentName contentName;
		try {
			//System.out.println(DEFAULT_URI + rootDir + filename);
			contentName = ContentName.fromURI(DEFAULT_URI + rootDir + filename);
			Interest interest = new Interest(contentName);
			
			ContentObject co = _reader.get(interest, 20000);
			String content = new String(co.content());
			
			if (content.equals("ERROR")){
				System.out.println("File does not exist!");
				shutdown();
			}
			// System.out.println("******* get file " + filename + " successfully *********");
			return content;
		} catch (MalformedContentNameStringException e) {
			System.out.println("NDNX URL error!");
			shutdown();
		} catch (IOException e) {
			System.out.println("Cannot connect with server!");
			shutdown();
		}
		return null; 
	}
	
	public void writeFile(String filename, String content) {
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(filename));
			writer.write(content);
			writer.close();
		} catch (IOException e) {
			System.out.println("Cannot write file!");
			shutdown();
		}
		
	}
	
	public void shutdown(){
		_handle.close();
		System.exit(0);
	}
	
	public String getSummary(int maxWord){
		// <filename, content>
		HashMap<String, String> summaryHT = new HashMap<String, String>();
		String summary = "";
		int wc = 0;
		
		ArrayList<String> fileList = new ArrayList<String>();
		if (maxWord > 0){
			for (int i=0; i<meta.length; i++){
				// System.out.println(meta[i]);
				String content = getFile(meta[i].substring(1));
				wc += content.split("\\s+").length;
				
				if (wc <= maxWord){
					summaryHT.put(meta[i], content);
					fileList.add(meta[i]);
					// System.out.println(meta[i]);
				} else {
					Resemble resemble = new Resemble(fileList);
					ArrayList<String> results = resemble.nameOrder();
					
					for (String filename: results){
						// System.out.println(filename);
						summary += summaryHT.get(filename) + "\n";
					}
					
					return summary; 
				}
			}
		}
		
		return summary;
	}
	
	
	public static void main(String[] args){
		if (args.length != 3){
			System.out.println("usage: Checkout <NDNx prefix> <file name> <maxWordNum>");
			System.exit(1);
		}
		String name = args[1];
		CheckoutClient client = new CheckoutClient(name + "/", args[0]);
		
		int maxWord = Integer.parseInt(args[2]);
		String summary = client.getSummary(maxWord);
		client.writeFile(name + "_" + maxWord, summary);
		System.out.println("Checkout successfully!");
		client.shutdown();
	}
}




