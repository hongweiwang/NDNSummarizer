NDNSummarizer
=============

Introduction
-------------

NDNSummarizer is an auto-summarization service on top of NDN that works in conjunction with any text editor. It has a check-in/check-out interface. It enables authoring documents (using any text editor) while tagging segments thereof with tags representing different "important levels". The authors would share their properly tagged file using our check-in command. A person who wishes to read the file would request it via a check-out, specifying a requested level of "summarization" (namely, a maximum summary length not to be exceeded). This requests gets translated into interests that ask for important segments first, then progressively less important ones (stitching them in the order they appear in the original document) until the maximum length is reached (or the entire document is retrieved, whichever happens first). Hence, say, a news website can publish a news story and different people can request it at different levels of summarization as decided by each client.


#### Tag a Document

The author needs to tag his document before doing the checkin to the server. We provide a sample document called "NDN" in "Checkin/local/". Here is an excerpt of the document:

> `<L1>`What is Good Research?`</L1>`

> `<L2>`There has to be a long-term need for it in the broader society.`</L2><L3>`Cyber-physical computing fulfills a perpetual over-arching need to achieve progress towards the betterment of human kind. Data (about the physical world) is a prerequisite to all major scientific advances.`</L3>`

> `<L2>`It should be outward looking.</L2><L3>Much research in computer science is understood only by computer scientists.`</L3>`

We use the XML-like tag "`<L*></L*>`" (* is a number) to represent the "important levels" of the text segment. It forms a tree structure, where L1 indicates the most imporant segment. If a text segment has a larger tag number, it means it is less imporant. If it does not have a tag, then it will be assigned with the least imporant level. Here is the tagging tree structure of the above document.

> `<L1>`

> `--------<L2>`

> `-----------------<L3>`

> `--------<L2>`

> `-----------------<L3>`


Compile the Code
----------------

The program contains three directories, "Checkin", "Checkout" and "Server". They contain the source code of checkin client, checkout client and server respectively. The server accept requests from checkin client and store the tagged document in its local repository. It also handles requests from checkout client and transmits the summarization. The three directories can be deployed on three different machines.

To compile the code, go to each directory and type:
```
make
```


Run the Code
------------

#### Configure NDNx

Download ndnx-0.3 from the website http://named-data.net/download/.

On each machine using NDNx, we need to start the NDNx server. Go to ndnx-0.3/bin, type:
```
./ndndstart
```

If you would like to run it on separate machines, you also need to configure the IP address with:
```
./ndndc add ndn:/summary/ [other machine IP]
```

To stop the server:
```
./ndndstop
```

#### Start Server
```
cd Server/
./startserver
```

#### Checkin Files

As an example, we will checkin a file named "NDN" in the directory "local" to the server repository.
```
cd checkin/
./checkin local NDN
```

Expected result:<br/>
Checkin successfully!

At this point, a directory named "NDN" is generated in the server's repository.

#### Checkout Files

Specify the file name and the maximum number of words you would like to see in the summary. For example, if we want to checkout the "NDN" file with no more than 100 words, we can do the following:
```
cd checkout/
./checkout NDN 100
```

Expected result:<br/>
Checkout successfully!

At this point, a new file named "NDN_100" is generated in the local directory.











