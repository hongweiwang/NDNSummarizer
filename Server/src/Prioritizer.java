import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;

public class Prioritizer{
    public Prioritizer(String rootName, String rootDirectory){
        this.treeRoot = new TreeNode(rootName, null);
        this.rootDirectory = rootDirectory;
        this.metaList = new ArrayList<String>();
    }
    
    public Prioritizer(String rootDirectory){
        this.treeRoot = new TreeNode("root", null);
        this.rootDirectory = rootDirectory;
        this.metaList = new ArrayList<String>();
    }

    public synchronized void prioritization() {
        this.buildTree();
        this.printTree();
        ArrayList<String> order = this.orderSingleSide();
        this.printStringList(order);
        try {
            File file = new File(rootDirectory + "/meta.txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            for (String filename : order) {
            	if (!filename.equals("/meta.txt")){
            		System.out.println(filename);
            		bw.write(filename + '\n');
            	}	
            }
            bw.close();
            //System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printStringList (ArrayList<String> list){
//        for(String l : list) {
//            System.out.println(l);
//        }
    }

    public ArrayList<String> orderSingleSide(){
        return order(null);
    }

    public ArrayList<String> order(TreeNode occupancyTreeRoot){
        if (occupancyTreeRoot != null){
            if (this.treeRoot.getNodeName().equals(occupancyTreeRoot.getNodeName())){
                return order(this.treeRoot, occupancyTreeRoot);
            }
        }
        return order(this.treeRoot, null);
    }

    public ArrayList<String> order(TreeNode node, TreeNode occupancyTreeRoot) {
        ArrayList<String> ret = new ArrayList<String>();
        if (node.hasValue()){
            ret.add(node.getNodeValue());
        }

        if(node.isLeaf()){
            return ret;
        }

        ArrayList<ArrayList<String>> lol = new ArrayList<ArrayList<String>>();
        ArrayList<TreeNode> children = node.getChildren();
        if(occupancyTreeRoot == null) {
            occupancyTreeRoot = new TreeNode();
        }
        ArrayList<TreeNode> occupancyChildren = occupancyTreeRoot.getChildren();

        for(TreeNode child : children) {
            TreeNode occupancyChild = null;
            for (TreeNode oc : occupancyChildren) {
                if (child.getNodeName().equals(oc.getNodeName())){
                    occupancyChild = oc;
                }
            }
            lol.add(order(child, occupancyChild));
        }

        ArrayList<Double> occupancies = new ArrayList<Double>();
        for(TreeNode child: children) {
            TreeNode occupancyChild = null;
            for (TreeNode oc : occupancyChildren) {
                if (child.getNodeName().equals(oc.getNodeName())) {
                    occupancyChild = oc;
                }
            }
            if (occupancyChild == null) {
                occupancies.add(0.0);
            }
            else {
                occupancies.add(occupancyChild.getOccupancy());
            }
        }

        while (lol != null) {
            int leastOccupancyIdx = 0;
            double leastOccupancy = Double.MAX_VALUE;
            for (int i = 0; i<lol.size(); i++) {
                if (leastOccupancy > occupancies.get(i)) {
                    leastOccupancy = occupancies.get(i);
                    leastOccupancyIdx = i;
                }
            }
            ret.add(lol.get(leastOccupancyIdx).get(0));
            occupancies.set(leastOccupancyIdx, occupancies.get(leastOccupancyIdx) + 1.0);
            lol.get(leastOccupancyIdx).remove(0);
            if(lol.get(leastOccupancyIdx).isEmpty()) {
                lol.remove(leastOccupancyIdx);
                occupancies.remove(leastOccupancyIdx);
            }
            if(lol.isEmpty()){
                lol = null;
            }
        }

        return ret;
    }

    public void printTree(){
        printTree(this.treeRoot, 0);
    }

    public void printTree(TreeNode root, int depth){
//        for (int i = 0; i< depth; i++) {
//            System.out.print("  ");
//        }
//        System.out.print(root.getNodeName());
//        System.out.print(" ");
//        System.out.print(root.getNodeValue());
//        System.out.print(" ");
//        System.out.print(root.getOccupancy());
//        System.out.println();
        ArrayList<TreeNode> children = root.getChildren();
        for(TreeNode child : children){
            printTree(child, depth+1);
        }
    }

    public void buildTree(){
        TreeNode curRoot = this.treeRoot;
        File dir = new File(this.rootDirectory);
        buildTree(curRoot, dir, "/");
    }

    public void buildTree(TreeNode curRoot, File dir, String prefix){
        File[] list = dir.listFiles();
        ArrayList<File> listOfFiles = new ArrayList<File>();
        ArrayList<File> listOfFolders = new ArrayList<File>();

        for(int i = 0; i < list.length; i++) {
            if (list[i].isDirectory()) {
                listOfFolders.add(list[i]);
            }
            else {
            	if (!list[i].getName().startsWith("."))
            		listOfFiles.add(list[i]);
            }
        }

        /*for (File file: listOfFiles) {
            String filename = file.getName();
            if (filename.startsWith(".")){
                listOfFiles.remove(file);
            }
        }*/

        for (final File dirEntry: listOfFolders) {
            String nodeName = dirEntry.getName();
            String nodeValue = null;
            for (final File fileEntry: listOfFiles) {
                //System.out.println("haha:" + fileEntry.getName() + ":" + nodeName);
                if (fileEntry.getName().equals(nodeName + ".txt")) {
                    nodeValue = prefix + fileEntry.getName();
                    listOfFiles.remove(fileEntry);
                    break;
                }
            }
            curRoot.addChild(nodeName, nodeValue);
            buildTree(curRoot.children.get(curRoot.children.size()-1), dirEntry, prefix + dirEntry.getName() + "/");
        }

        for (final File fileEntry : listOfFiles) {
            String fileName = null;
            String fileValue = prefix + fileEntry.getName();
            curRoot.addChild(fileName, fileValue);
        }
    }

    private class TreeNode{
        public TreeNode() {
            this(null, null);
        }

        public TreeNode(String nodeName, String nodeValue) {
            this(nodeName, nodeValue, 0.0);
        }

        public TreeNode(String nodeName, String nodeValue, double occupancy){
            this.nodeName = nodeName;
            this.nodeValue = nodeValue;
            this.occupancy = occupancy;
            this.weight = 1.0; // now we consider that all the nodes have same weight
            this.children = new ArrayList<TreeNode>();
            this.parent = null;
        }

        public TreeNode (String nodeName, String nodeValue, double occupancy, double weight) {
            this(nodeName, nodeValue, occupancy);
            this.weight = weight; // in this case the weights are not
                                  // identical 
        }

        public String getNodeName(){
            return this.nodeName;
        }

        public String getNodeValue() {
            return this.nodeValue;
        }

        public boolean hasName(){
            return this.nodeName != null;
        }

        public boolean hasValue(){
            return this.nodeValue != null;
        }
        
        public double getOccupancy(){
            return this.occupancy;
        }

        public void setOccupancy(double occupancy){
            this.occupancy = occupancy;
        }

        public void increaseOccupancy() {
            increaseOccupancy(1.0);
        }

        public void increaseOccupancy(double increase){
            setOccupancy(this.occupancy + increase);
        }

        public boolean isLeaf(){
            return this.children.size() == 0;
        }

        public ArrayList<TreeNode> getChildren(){
            return this.children;
        }

        public void addChild(TreeNode child){
            this.children.add(child);
            child.parent = this;
        }

        public void addChild(String nodeName, String nodeValue){
            addChild(nodeName, nodeValue, 0.0);
        }

        public void addChild(String nodeName, String nodeValue, double occupancy){
            TreeNode child = new TreeNode(nodeName, nodeValue, occupancy); 
            this.addChild(child);
        }

        private String nodeName;
        private String nodeValue;
        private double occupancy;
        private double weight;
        private ArrayList<TreeNode> children;
        private TreeNode parent;
    }

    private TreeNode treeRoot;
    private String rootDirectory;
    private ArrayList<String> metaList;

    public static void main(String[] args){
        Prioritizer pri = new Prioritizer("TheRoot", "public");
        pri.prioritization();
    }
}
