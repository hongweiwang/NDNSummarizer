
/*
 * A NDNx file proxy program.
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 * This work is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SignatureException;
import java.util.Date;
import java.util.logging.Level;

import org.ndnx.ndn.NDNFilterListener;
import org.ndnx.ndn.NDNHandle;
import org.ndnx.ndn.config.ConfigurationException;
import org.ndnx.ndn.impl.support.Log;
import org.ndnx.ndn.io.NDNFileOutputStream;
import org.ndnx.ndn.io.NDNReader;
import org.ndnx.ndn.io.NDNWriter;
import org.ndnx.ndn.profiles.CommandMarker;
import org.ndnx.ndn.profiles.SegmentationProfile;
import org.ndnx.ndn.profiles.VersioningProfile;
import org.ndnx.ndn.profiles.metadata.MetadataProfile;
import org.ndnx.ndn.profiles.nameenum.NameEnumerationResponse;
import org.ndnx.ndn.profiles.nameenum.NameEnumerationResponse.NameEnumerationResponseMessage;
import org.ndnx.ndn.profiles.nameenum.NameEnumerationResponse.NameEnumerationResponseMessage.NameEnumerationResponseMessageObject;
import org.ndnx.ndn.profiles.security.KeyProfile;
import org.ndnx.ndn.protocol.NDNTime;
import org.ndnx.ndn.protocol.ContentName;
import org.ndnx.ndn.protocol.ContentObject;
import org.ndnx.ndn.protocol.Exclude;
import org.ndnx.ndn.protocol.ExcludeComponent;
import org.ndnx.ndn.protocol.Interest;
import org.ndnx.ndn.protocol.MalformedContentNameStringException;

/**
 * NDNFileProxy is a file system proxy that makes files on the local system
 * available over the NDNx network. It takes a directory from which to serve files,
 * which it treats as the root of its content tree, and an optional NDNx URI
 * to serve as the prefix for that file content as represented in NDNx.
 * 
 * For example, if you have a directory /foo in the file system, with the following
 * contents:
 * 	/foo/
 * 		bar.txt
 * 		baz/
 * 			box.txt
 * and you call NDNFileProxy /foo NDNx:/testprefix
 * 
 * then asking for NDNx:/testprefix/bar.txt would return the file bar.txt (segmented
 * appropriately), and asking for NDNx:/testprefix/baz/box.txt would return box.txt.
 * The version for each file is set using the last modified information available from
 * the file system for the real file (but the file is re-signed every time you ask
 * for it from this server, so will result in slightly different pieces of content
 * with different signatures). The default prefix is NDNx:/, which means asking
 * for NDNx:/bar.txt would get you bar.txt.
 * 
 * Future improvements: 
 * - cache the original signing information so even if the
 * data falls out of NDNd's cache, you get the same signature information back,
 * - implement a NE responder to list files. 
 * - signal handling
 * - logging level control from a command line argument
 * - move file writer to a separate thread
 */
public class Server implements NDNFilterListener {
	
	// static String DEFAULT_URI = "NDNx:/fileprefix";
	static int BUF_SIZE = 4096;
	
	protected boolean _finished = false;
	protected ContentName _prefix;
	protected ContentName _checkinPrefix;
	protected ContentName _checkoutPrefix;
	protected String _filePrefix;
	protected File _rootDirectory;
	protected NDNHandle _handle;
	
	private ContentName _responseName = null;
	
	public static void usage() {
		System.err.println("usage: Server <file path to serve> <NDNx prefix>");
	}

	public Server(String filePrefix, String NDNxURI) throws MalformedContentNameStringException, ConfigurationException, IOException {
		_prefix = ContentName.fromURI(NDNxURI);
		_checkinPrefix = ContentName.fromURI(NDNxURI + "checkin/");
		_checkoutPrefix = ContentName.fromURI(NDNxURI + "checkout/");
		System.out.println(_prefix);
		_filePrefix = filePrefix;
		_rootDirectory = new File(filePrefix);
		if (!_rootDirectory.exists()) {
			Log.severe("Cannot serve files from directory {0}: directory does not exist!", filePrefix);
			throw new IOException("Cannot serve files from directory " + filePrefix + ": directory does not exist!");
		}
		_handle = NDNHandle.open();
		
		//set response name for NE requests
		_responseName = KeyProfile.keyName(null, _handle.keyManager().getDefaultKeyID());

	}
	
	public void start() throws IOException{
		Log.info("Starting file proxy for " + _filePrefix + " on NDNx namespace " + _prefix + "...");
		System.out.println("Starting file proxy for " + _filePrefix + " on NDNx namespace " + _prefix + "...");
		// All we have to do is say that we're listening on our main prefix.
		_handle.registerFilter(_prefix, this);
		System.out.println("filter: " + _prefix.toString());
	}
	
	public boolean handleInterest(final Interest interest) {
		// Alright, we've gotten an interest. Either it's an interest for a stream we're
		// already reading, or it's a request for a new stream.
		Log.info("NDNFileProxy main responder: got new interest: {0}", interest);
		
		// Test to see if we need to respond to it.
		if (!_prefix.isPrefixOf(interest.name())) {
			Log.info("Unexpected: got an interest not matching our prefix (which is {0})", _prefix);
			return false;
		}

		// We see interests for all our segments, and the header. We want to only
		// handle interests for the first segment of a file, and not the first segment
		// of the header. Order tests so most common one (segments other than first, non-header)
		// fails first.
		if (SegmentationProfile.isSegment(interest.name()) && !SegmentationProfile.isFirstSegment(interest.name())) {
			Log.info("Got an interest for something other than a first segment, ignoring {0}.", interest.name());
			return false;
		} else if (interest.name().contains(CommandMarker.COMMAND_MARKER_BASIC_ENUMERATION.getBytes())) {
			try {
				Log.info("Got a name enumeration request: {0}", interest);
				return nameEnumeratorResponse(interest);
			} catch (IOException e) {
				Log.warning("IOException generating name enumeration response to {0}: {1}: {2}", interest.name(), e.getClass().getName(), e.getMessage());
				return false;
			}
		} else if (MetadataProfile.isHeader(interest.name())) {
			Log.info("Got an interest for the first segment of the header, ignoring {0}.", interest.name());
			return false;
		} 
		
		
		if (_checkinPrefix.isPrefixOf(interest.name())) {
			new Thread(new Runnable() {
				public void run() {
					System.out.println(interest);
					NDNReader reader;
					try {
						reader = new NDNReader(_handle);
						// String uri = "NDNx:" + interest.toString().substring(0, interest.toString().length()-2);
						String uri = "ndn:" + interest.toString().substring(0, interest.toString().lastIndexOf('_'));
						System.out.println("uri: " + uri);
						NDNWriter writer = new NDNWriter(_handle);
						writer.put(interest.name(), "OK");
						
						long time = new Date().getTime();
						Interest checkinInterest = new Interest(ContentName.fromURI(uri + '_' + time));
						ContentObject co = reader.get(checkinInterest, 20000);
						String content = new String(co.content());
						String rootDir = _filePrefix + "/" + uri.substring(uri.lastIndexOf('/') + 1);
						System.out.println(rootDir);
						Parser parser = new Parser(rootDir, content);
				        parser.parse();
				        parser.printParseTree();
				        parser.buildDirectory();
				        Prioritizer pri = new Prioritizer(rootDir);	
						pri.prioritization();
					} catch (ConfigurationException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (MalformedContentNameStringException e) {
						e.printStackTrace();
					} catch (SignatureException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}).start();
		}
		
		if (_checkoutPrefix.isPrefixOf(interest.name())) {
			System.out.println(interest.toString());
			// Write the file
			try {
				return writeFile(interest);
			} catch (IOException e) {
				Log.warning("IOException writing file {0}: {1}: {2}", interest.name(), e.getClass().getName(), e.getMessage());
				return false;
			}
		}

		
		return true;
	}
	
	protected File NDNNameToFilePath(ContentName name) {
		
		ContentName fileNamePostfix = name.postfix(_checkoutPrefix);
		if (null == fileNamePostfix) {
			// Only happens if interest.name() is not a prefix of _prefix.
			Log.info("Unexpected: got an interest not matching our prefix (which is {0})", _checkoutPrefix);
			return null;
		}

		File fileToWrite = new File(_rootDirectory, fileNamePostfix.toString());
		Log.info("file postfix {0}, resulting path name {1}", fileNamePostfix, fileToWrite.getAbsolutePath());
		return fileToWrite;
	}
	
	/**
	 * Actually write the file; should probably run in a separate thread.
	 * @param fileNamePostfix
	 * @throws IOException 
	 */
	protected boolean writeFile(final Interest outstandingInterest) throws IOException {
		
		File fileToWrite = NDNNameToFilePath(outstandingInterest.name());
		Log.info("NDNFileProxy: extracted request for file: " + fileToWrite.getAbsolutePath() + " exists? ", fileToWrite.exists());
		
		
		// Set the version of the NDN content to be the last modification time of the file.
		NDNTime modificationTime = new NDNTime(fileToWrite.lastModified());
		ContentName versionedName = 
			VersioningProfile.addVersion(new ContentName(_prefix, 
						outstandingInterest.name().postfix(_prefix).components()), modificationTime);

		// NDNFileOutputStream will use the version on a name you hand it (or if the name
		// is unversioned, it will version it).
		final NDNFileOutputStream NDNout = new NDNFileOutputStream(versionedName, _handle);
		
		// We have an interest already, register it so we can write immediately.
		NDNout.addOutstandingInterest(outstandingInterest);
		
		if (!fileToWrite.exists()) {
			Log.warning("File {0} does not exist. Ignoring request.", fileToWrite.getAbsoluteFile());
			new Thread(new Runnable() {
				public void run() {
					System.out.print("ERROR");
					try {
						NDNout.write("ERROR".getBytes());
						NDNout.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
			
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
		
		
		// Run in a separate thread to not blocking incoming interests
		new Thread(new Runnable() {
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
					NDNout.close(); // will flush
				} catch (IOException e) {
					Log.warning("IOException writing file {0}: {1}: {2}", outstandingInterest.name(), e.getClass().getName(), e.getMessage());
				}
			}
		}).start();
		
		return true;
	}
	
	/**
	 * Handle name enumeration requests
	 * 
	 * @param interest
	 * @throws IOException 
	 * @returns true if interest is consumed
	 */
	public boolean nameEnumeratorResponse(Interest interest) throws IOException {
		
		boolean result = false;
		ContentName neRequestPrefix = interest.name().cut(CommandMarker.COMMAND_MARKER_BASIC_ENUMERATION.getBytes());
		
		File directoryToEnumerate = NDNNameToFilePath(neRequestPrefix);
		
		if (!directoryToEnumerate.exists() || !directoryToEnumerate.isDirectory()) {
			// nothing to enumerate
			return result;
		}
		
		NameEnumerationResponse ner = new NameEnumerationResponse();
		ner.setPrefix(new ContentName(neRequestPrefix, CommandMarker.COMMAND_MARKER_BASIC_ENUMERATION.getBytes()));
		
		Log.info("Directory to enumerate: {0}, last modified {1}", directoryToEnumerate.getAbsolutePath(), new NDNTime(directoryToEnumerate.lastModified()));
		// stat() the directory to see when it last changed -- will change whenever
		// a file is added or removed, which is the only thing that will change the
		// list we return.
		ner.setTimestamp(new NDNTime(directoryToEnumerate.lastModified()));
		// See if the resulting response is later than the previous one we released.
		
		//now add the response id
	    ContentName prefixWithId = new ContentName(ner.getPrefix(), _responseName.components());
	    //now finish up with version and segment
	    ContentName potentialCollectionName = VersioningProfile.addVersion(prefixWithId, ner.getTimestamp());
	    
	    //switch to add response id to name enumeration objects
		//ContentName potentialCollectionName = VersioningProfile.addVersion(ner.getPrefix(), ner.getTimestamp());
	    
	    potentialCollectionName = SegmentationProfile.segmentName(potentialCollectionName, SegmentationProfile.baseSegment());
		//check if we should respond...
		if (interest.matches(potentialCollectionName, null)) {
		
			// We want to set the version of the NE response to the time of the 
			// last modified file in the directory. Unfortunately that requires us to
			// stat() all the files whether we are going to respond or not.
			String [] children = directoryToEnumerate.list();
			
			if ((null != children) && (children.length > 0)) {
				for (int i = 0; i < children.length; ++i) {
					ner.add(children[i]);
				}

				NameEnumerationResponseMessage nem = ner.getNamesForResponse();
				NameEnumerationResponseMessageObject neResponse = new NameEnumerationResponseMessageObject(prefixWithId, nem, _handle);
				neResponse.save(ner.getTimestamp(), interest);
				result = true;
				Log.info("sending back name enumeration response {0}, timestamp (version) {1}.", ner.getPrefix(), ner.getTimestamp());
			} else {
				Log.info("no children available: we are not sending back a response to the name enumeration interest (interest = {0}); our response would have been {1}", interest, potentialCollectionName);
			}
		} else {
			Log.info("we are not sending back a response to the name enumeration interest (interest = {0}); our response would have been {1}", interest, potentialCollectionName);
			if (interest.exclude().size() > 1) {
				Exclude.Element el = interest.exclude().value(1);
				if ((null != el) && (el instanceof ExcludeComponent)) {
					Log.info("previous version: {0}", VersioningProfile.getVersionComponentAsTimestamp(((ExcludeComponent)el).getBytes()));
				}
			}
		}
		return result;
	}

    /**
     * Turn off everything.
     * @throws IOException 
     */
	public void shutdown() throws IOException {
		if (null != _handle) {
			_handle.unregisterFilter(_prefix, this);
			Log.info("Shutting down file proxy for " + _filePrefix + " on NDNx namespace " + _prefix + "...");
			System.out.println("Shutting down file proxy for " + _filePrefix + " on NDNx namespace " + _prefix + "...");
		}
		_finished = true;
	}
	
	public boolean finished() { return _finished; }

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		if (args.length < 2) {
			usage();
			return;
		}
		
		String filePrefix = args[1];
		String NDNURI = args[0];
		
		try {
			Server proxy = new Server(filePrefix, NDNURI);
			
			// All we need to do now is wait until interrupted.
			proxy.start();
			
			while (!proxy.finished()) {
				// we really want to wait until someone ^C's us.
				try {
					Thread.sleep(100000);
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		} catch (Exception e) {
			Log.warning("Exception in NDNFileProxy: type: " + e.getClass().getName() + ", message:  "+ e.getMessage());
			Log.warningStackTrace(e);
			System.err.println("Exception in NDNFileProxy: type: " + e.getClass().getName() + ", message:  "+ e.getMessage());
			e.printStackTrace();
		}
	}
}
