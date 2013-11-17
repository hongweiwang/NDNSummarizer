import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;


public class Parser{
    public Parser( String rootDir, String text) {
        this.setRootDir(rootDir);
        this.index = 0; // start to parse the beginning of the text
        this.text = text;
        
        this.root = new ParserTreeNode();
        this.root.setName(rootDir);
        this.nodeNamePrefix = "node";
        this.nodeNameIdx = 0;
    }
    
    public ParserTreeNode getRoot() {
        return this.root;
    }
    
    public void printParseTree() {
        printParseTree(this.root, 0);
    }
    
    public void buildDirectory() throws IOException {
        buildDirectory(this.root, 0, ".");
    }
    
    public void printParseTree(ParserTreeNode node, int depth){
       
        /*for(int i = 0; i<depth; i++){
            System.out.print("  ");
        }
        System.out.println(node.getName() + ", " + node.getValue());*/
        
        if(! node.isLeaf()) {
            ArrayList<ParserTreeNode> children = node.getChildren();
            
            for(ParserTreeNode child: children) {
                printParseTree(child, depth+1);
            }
        }
    }
    
    public void buildDirectory(ParserTreeNode node, int depth, String path) throws IOException{
        
       // System.out.println(node.getName() + ", " + node.getValue());
      
        path += "/"+node.getName();
        File dir = new File(path);
        
        //System.out.println("dir:" + path);
        
        if (!dir.exists() && node.getChildren().size() != 0){
            dir.mkdir();
        }
        
        if (node.getValue() != null){
           BufferedWriter writer = new BufferedWriter(new FileWriter(path+".txt"));
            writer.write(node.getValue());
            writer.close();
            //System.out.println("file path:"+path+".txt");
            //System.out.println("file:" + node.getValue());
        }
    
        //System.out.println();
        
        if(! node.isLeaf()) {
            ArrayList<ParserTreeNode> children = node.getChildren();
            for(ParserTreeNode child: children) {
                buildDirectory(child, depth+1, path);
            }
        }
    }

    
    public void parse() {
    	text = processText(text);
    	// System.out.println(text);
        parse(this.root, 1);
    }
    
    /**
     * This function parses the text. The parameters taken are:
     * 1. the current root node to put the parsed result in the parse tree.
     * 2. the current importance level
     */
    public void parse(ParserTreeNode root, int curLevel) {
        ParserTreeNode curnode = null;// = new ParserTreeNode(genNodeName(), "");
        
        while(this.index != this.text.length()){
            String lb = getNextLabel();
            int level = getLevel(lb);
            assert(isStartLabel(lb));
            if(level == curLevel) {
                // the level equals to the current level, we add a child
                String value = getText();
                String clb = getNextLabel();
                assert(isStopLabel(clb));
                assert(getLevel(lb) == getLevel(clb));
                ParserTreeNode node = new ParserTreeNode(genNodeName(), "");
                node.setValue(value);
                root.addChild(node);
                node.setParent(root);
                curnode = node;
            }
            else if(level > curLevel) {
                // the level is smaller than the current level, we recursively
                // add child to the current node.
                this.index -= lb.length();
                if (curnode == null) {
                	curnode = new ParserTreeNode(genNodeName(), null);
                	root.addChild(curnode);
                }
                parse(curnode, curLevel +1);
                curnode = null;
            }
            else {
                // the level is greater than the current level, we stop the
                // recursion and return.
                this.index -= lb.length();
                return;
            }
        }
    }
    
    private String genNodeName() {
        String nodeNameSuffix = this.nodeNameIdx + "";
        this.nodeNameIdx ++;
        return this.nodeNamePrefix + nodeNameSuffix;
    }
    
    private boolean isStartLabel(String label) {
        return label.startsWith("<L");
    }
    
    private boolean isStopLabel(String label) {
        return label.startsWith("</L");
    }
    
    private int getLevel(String label) {
        assert(label.startsWith("<"));
        assert(label.charAt(label.length()-1) == '>');
        if(isStartLabel(label)){
            String lvlStr = label.substring(2, label.length()-1);
            return Integer.parseInt(lvlStr);
        }
        if(isStopLabel(label)){
            String lvlStr = label.substring(3, label.length()-1);
            return Integer.parseInt(lvlStr);
        }
        System.err.println("Wrong label: The label should start with \"<L\" or \"</L\"");
        System.exit(1);
        return -1;
    }
    
    private String processText(String input){
        //System.out.println(input);
        StringBuffer ret = new StringBuffer();
        int max = 0;
        
        while(this.index < input.length()){
        	
            int start = index;
            int stop = input.indexOf('<', index);
            //System.out.println("start " + start + "\t stop " + stop);
            
            if (stop == -1) {
            	ret.append("<L*>"+ input.substring(start) +"</L*>");
            	break;
            }
            
            if (start < stop){
            	String s = input.substring(start, stop);
            	if (!s.matches("\\s+")){
            		ret.append("<L*>"+s+"</L*>");
            	}
                
            }
            index = stop;
            
            String lb = getNextLabel();
            assert(isStartLabel(lb));
            
            if (this.getLevel(lb) > max){
            	max = this.getLevel(lb);
            }
            ret.append(lb);
            ret.append(this.getText());
            String clb = getNextLabel();
            assert(isStopLabel(clb));
            ret.append(clb);
        }
        
        //System.out.println(ret.toString());
        
        this.index = 0;
        
        String text = ret.toString();
        text = text.replaceAll("L\\*>", "L" + (max + 1) + ">");
        //System.out.println(text);
        
        return text;
    }
    
    private String getNextLabel() {
        String ret = "";
        assert(this.text.charAt(this.index) == '<');
        while(this.text.charAt(this.index) != '>') {
            ret += this.text.charAt(this.index)+"";
            this.index ++;
        }
        ret += this.text.charAt(this.index)+"";
        this.index++;
        return ret;
    }
    
    private String getText() {
        String ret = "";
        assert(this.text.charAt(this.index) != '<');
        while(this.text.charAt(this.index) != '<') {
            ret += this.text.charAt(this.index) + "";
            this.index ++;
        }
        return ret;
    }
    
	public String getRootDir() {
		return rootDir;
	}

	public void setRootDir(String rootDir) {
		this.rootDir = rootDir;
	}
    
    private class ParserTreeNode {
        public ParserTreeNode() {
            this.children = new ArrayList<ParserTreeNode>();
            this.parent = null;
        }
        public ParserTreeNode(String name, String value) {
            this();
            this.setName(name);
            this.setValue(value);
        }
        public String getName() {
            return this.name;
        }
        public String getValue() {
            return this.value;
        }
        public ArrayList<ParserTreeNode> getChildren() {
            return this.children;
        }
        public ParserTreeNode getParent() {
            return this.parent;
        }
        public void setName( String name) {
            this.name = name;
        }
        public void setValue(String value) {
            this.value = value;
        }
        public void addChild(ParserTreeNode child){
            this.children.add(child);
        }
        public void setParent(ParserTreeNode parent) {
            this.parent = parent;
        }
        public boolean isLeaf() {
            return children.size() == 0;
        }
        private String name;
        private String value;
        private ArrayList<ParserTreeNode> children;
        private ParserTreeNode parent;
    }

    private String rootDir; // the root directory to put the parsed text
    private ParserTreeNode root; // to hold the parse tree
    private String text; // the text to be parsed
    private int index; // the position to be parsed
    private String nodeNamePrefix;
    private int nodeNameIdx;

    public static void main(String[] args) {
    	if (args.length != 2){
			System.out.println("usage: Parser src dest");
			System.exit(1);
		}
    	
    	BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(args[0]));
			StringBuffer buffer = new StringBuffer();
	    	String line;
	    	while ((line = reader.readLine()) != null){
	    		buffer.append(line);
	    	}
	    	String rootDir = "repository/" + args[1];
	    	// System.out.println(buffer.toString());
	        Parser parser = new Parser(rootDir, buffer.toString());
	        parser.parse();
	        parser.printParseTree();
	        parser.buildDirectory();
	        
	        Prioritizer pri = new Prioritizer(rootDir);	
			pri.prioritization();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}
