import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.SignatureException;
import java.security.Timestamp;
import java.util.Date;
import java.util.logging.Level;

import org.ndnx.ndn.NDNFilterListener;
import org.ndnx.ndn.NDNHandle;
import org.ndnx.ndn.config.ConfigurationException;
import org.ndnx.ndn.impl.support.Log;
import org.ndnx.ndn.io.NDNFileOutputStream;
import org.ndnx.ndn.io.NDNReader;
import org.ndnx.ndn.io.NDNWriter;
import org.ndnx.ndn.profiles.VersioningProfile;
import org.ndnx.ndn.protocol.NDNTime;
import org.ndnx.ndn.protocol.ContentName;
import org.ndnx.ndn.protocol.ContentObject;
import org.ndnx.ndn.protocol.Interest;
import org.ndnx.ndn.protocol.MalformedContentNameStringException;

public class CheckinClient implements NDNFilterListener{
	static int BUF_SIZE = 4096;
	protected String NDNxURI;
	protected String filePrefix;
	protected String rootDir;
	protected ContentName prefix;
	protected NDNHandle handle;
	protected NDNReader reader;
	protected NDNWriter writer;
	
	public CheckinClient(String NDNxURI, String rootDir, String filePrefix) {
		Log.setLevel(Log.FAC_ALL, Level.WARNING);
		this.NDNxURI = NDNxURI;
		this.filePrefix = filePrefix;
		try {
			this.prefix = ContentName.fromURI(NDNxURI);
		} catch (MalformedContentNameStringException e) {
			System.out.println("NDNX URL error");
			System.exit(1);
		}
		this.rootDir = rootDir;
		
		try {
			if (!checkFile(rootDir + "/" + filePrefix)){
				System.out.println("File syntax error!");
				System.exit(1);
			}
		} catch (IOException e) {
			System.out.println("Read file error!");
			System.exit(1);
		}
		
		try {
			handle = NDNHandle.open();
			reader = new NDNReader(handle);
			writer = new NDNWriter(handle);
		} catch (ConfigurationException e) {
			System.out.println("NDNX configuration error!");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Cannot connect with server!");
			System.exit(1);
		}
	}
	
	public boolean checkFile(String filename) throws IOException {
        FileReader fr = new FileReader(filename);
        BufferedReader br = new BufferedReader(fr);
        int value = br.read();
        boolean isopen = false;
        String pretagname = "";
        while(value != -1) {
            char ch = (char)value;
            if(ch == '/' || ch == '>')
            	return false;
            String tagname = "";
            if(ch == '<') {
                value = br.read();
                if(value == -1)
                    return false;
                ch = (char)value;
                if (ch == '/'){
                    if(!isopen)
                        return false;
                    isopen = false;
                }
                else{
                	if(isopen)
                		return false;
                	isopen = true;
                    tagname += ch;
                }
                value = br.read();
                while(value != -1) {
                    ch = (char)value;
                    if (ch != '>')
                        tagname += ch;
                    else
                        break;
                    value = br.read();
                }
                if(!tagname.startsWith("L")) { 
                	return false;
                }
                try{
                	Integer.parseInt(tagname.substring(1));
                } catch(Exception e){
                	return false;
                }
                if (isopen) {
                    pretagname = tagname;
                }
                else {
                    if(!pretagname.equals(tagname))
                        return false;
                }
            }
            value = br.read();
        }
        return true;
    }
	
	public void checkin() {
		// send interest (filename) to server
		long time = new Date().getTime();
		ContentName contentName;
		try {
			contentName = ContentName.fromURI(NDNxURI + filePrefix + "_" + time);
			// System.out.println("checkin: " + NDNxURI + filePrefix + "_" + time);
			Interest interest = new Interest(contentName);
			ContentObject co = reader.get(interest, 5000);
			handle.registerFilter(prefix, this);
		} catch (MalformedContentNameStringException e) {
			System.out.println("NDNX URL error!");
			shutdown();
		} catch (IOException e) {
			System.out.println("Cannot connect with server!");
			shutdown();
		}
	}
	
	public void shutdown(){
		handle.close();
		System.exit(0);
	}
	
	public static void main(String[] args) {
		String checkinURI = args[0];
		String rootDir = args[1];
		String filePrefix = args[2];
		
		if (args.length != 3){
			System.out.println("usage: Checkin <NDNx prefix> <directory> <filename>");
			System.exit(1);
		}
		
		CheckinClient client = new CheckinClient(checkinURI, rootDir, filePrefix);
		client.checkin();
		
	}

	public boolean handleInterest(Interest interest) {
		
		// System.out.println("receive interest: " + interest.toString());
		// Test to see if we need to respond to it.
		if (!prefix.isPrefixOf(interest.name())) {
			// Log.info("Unexpected: got an interest not matching our prefix (which is {0})", prefix);
			return false;
		}
				
		// write file to server
		try {
			return writeFile(interest);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Actually write the file; should probably run in a separate thread.
	 * @param fileNamePostfix
	 * @throws IOException 
	 */
	protected boolean writeFile(final Interest outstandingInterest) throws IOException {
		
		File fileToWrite = NDNNameToFilePath(outstandingInterest.name());
		Log.info("NDNFileProxy: extracted request for file: " + fileToWrite.getAbsolutePath() + " exists? ", fileToWrite.exists());
		if (!fileToWrite.exists()) {
			Log.warning("File {0} does not exist. Ignoring request.", fileToWrite.getAbsoluteFile());
			return false;
		}
		
		FileInputStream tempFis = null;
		try {
			tempFis = new FileInputStream(fileToWrite);
		} catch (FileNotFoundException fnf) {
			Log.warning("Unexpected: file we expected to exist doesn't exist: {0}!", fileToWrite.getAbsolutePath());
			return false;
		}
		final FileInputStream fis = tempFis;
		
		// Set the version of the NDN content to be the last modification time of the file.
		NDNTime modificationTime = new NDNTime(fileToWrite.lastModified());
		ContentName versionedName = 
			VersioningProfile.addVersion(new ContentName(prefix, 
						outstandingInterest.name().postfix(prefix).components()), modificationTime);

		// NDNFileOutputStream will use the version on a name you hand it (or if the name
		// is unversioned, it will version it).
		final NDNFileOutputStream NDNout = new NDNFileOutputStream(versionedName, handle);
		
		// We have an interest already, register it so we can write immediately.
		NDNout.addOutstandingInterest(outstandingInterest);
		
		// Run in a separate thread to not blocking incoming interests
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					byte [] buffer = new byte[BUF_SIZE];
					
					int read = fis.read(buffer);
					while (read >= 0) {
						NDNout.write(buffer, 0, read);
						NDNout.flush();
						read = fis.read(buffer);
					} 
					fis.close();
					// System.out.println("**********close********");
					NDNout.close(); // will flush
					// System.out.println("**********flush OK********");
				} catch (IOException e) {
					Log.warning("IOException writing file {0}: {1}: {2}", outstandingInterest.name(), e.getClass().getName(), e.getMessage());
				}
			}
		});
		
		t.start();
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		handle.close();
		System.out.println("Checkin successfully!");
		System.exit(0);
		
		return true;
	}
	
	protected File NDNNameToFilePath(ContentName name) {
		ContentName fileNamePostfix = name.postfix(prefix);
		if (null == fileNamePostfix) {
			// Only happens if interest.name() is not a prefix of _prefix.
			Log.info("Unexpected: got an interest not matching our prefix (which is {0})", prefix);
			return null;
		}

		File fileToWrite = new File(rootDir, fileNamePostfix.toString().substring(0, fileNamePostfix.toString().lastIndexOf('_')));
		// System.out.println("file to write: " + fileToWrite.getAbsolutePath());
		Log.info("file postfix {0}, resulting path name {1}", fileNamePostfix, fileToWrite.getAbsolutePath());
		return fileToWrite;
	}
}


