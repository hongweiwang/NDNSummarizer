import java.util.ArrayList;
import java.util.HashMap;
class Resemble {
    public Resemble(ArrayList<String> filenames) {
        this.filenames = filenames;
        this.root = new ResembleTreeNode("", null);
        this.results = new ArrayList<String>();
    }
    
    public ArrayList<String> nameOrder() {
        HashMap<Integer, String> map = new HashMap<Integer, String>();
        for (String filename : filenames) {
            String[] components = filename.split("/");
            String fname = components[components.length-1];
            assert(fname.contains(".txt"));
            int key = Integer.parseInt((fname.split("\\.")[0]).split("node")[1]); 
            map.put(key, filename);
        }
        ArrayList<Integer> keys = new ArrayList<Integer>(map.keySet());
        quickSort(keys, 0, keys.size());

        ArrayList<String> ret = new ArrayList<String>();
        for(int i = 0; i<keys.size(); i++) {
            ret.add(map.get(keys.get(i)));
        }

        return ret;
    }
    
    private void quickSort(ArrayList<Integer> array, int begin, int end) {
        if(end - begin < 2) return;
        int pivot = array.get(end-1);
        int i = begin, j = end-1, pidx = j;
        while(i < j) {
            if(pidx == i) {
                for(; array.get(j) >= pivot && i<j; j--);
                array.set(i, array.get(j));
                array.set(j, pivot);
                pidx = j;
            } else {
                for(; array.get(i) <= pivot && i<j; i++);
                array.set(j, array.get(i));
                array.set(i, pivot);
                pidx = i;
            }
            quickSort(array, begin, i);
            quickSort(array, i+1, end);
        }
    }
    
    public ArrayList<String> order() {
        for(String filename : filenames) {
            String[] components = filename.split("/");
            ResembleTreeNode node = this.root;
            for (int i = 1; i<components.length-1; i++){
                int idx = node.addChild(components[i], null);
                ArrayList<ResembleTreeNode> children = node.getChildren();
                node = children.get(idx);
            }
            String nodevalue = components[components.length-1];
            //System.out.println(nodevalue);
            String nodename = (nodevalue.split("\\."))[0];
            node.addChild(nodename, nodevalue);
        }
        scan(this.root, "");
        return this.results;
    }

    private void scan(ResembleTreeNode node, String prefix) {
        // if (node.isLeaf()) {
        //     this.results.add(prefix + "/" + node.getValue());
        //     return;
        // }
        // if (node.getValue() != null) {
        //     this.results.add(prefix + "/" + node.getValue());
        // }
        ArrayList<ResembleTreeNode>children = node.getChildren();
        for(ResembleTreeNode child: children) {
            if(child.isLeaf()){
                results.add(prefix + "/" + child.getValue());
            }
            else{
                if(child.getValue() != null) {
                    results.add(prefix + "/" + child.getValue());
                }
                scan(child, prefix + "/" + child.getName());
            }
        }
    }

    public void printTree() {
        printTree(this.root, 0);
    }

    public void printTree(ResembleTreeNode root, int depth) {
        for(int i = 0; i<depth; i++) {
            System.out.print("  ");
        }
        System.out.print(root.getName());
        System.out.print(" ");
        System.out.print(root.getValue());
        System.out.println();
        ArrayList<ResembleTreeNode> children = root.getChildren();
        for(ResembleTreeNode child: children) {
            printTree(child, depth+1);
        }
    }

    private class ResembleTreeNode {
        public ResembleTreeNode(String name, String value) {
            this.name = name;
            this.value = value;
            this.children = new ArrayList<ResembleTreeNode>();
        }
        public boolean isLeaf() {
            return this.children.size() == 0;
        }
        public int addChild(ResembleTreeNode node) {
            String name = node.getName();
            for(int i = 0; i< this.children.size(); i++) {
                ResembleTreeNode child = children.get(i);
                if (name.equals(child.getName())) {
                    if (child.getValue() == null) {
                        child.setValue(node.getValue());
                    }
                    return i;
                }
            }
            this.children.add(node);
            return this.children.size()-1;
        }
        public int addChild(String name, String value) {
            ResembleTreeNode node = new ResembleTreeNode(name, value);
            return this.addChild(node);
        }

        public ArrayList<ResembleTreeNode> getChildren() {
            return this.children;
        }

        public String getName() {
            return this.name;
        }
        public String getValue(){
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }
        private String value;
        private String name;
        private ArrayList<ResembleTreeNode> children;
    }

    public ArrayList<String> getResults() {
        return this.results;
    }

    private ArrayList<String> filenames;
    private ResembleTreeNode root;
    private ArrayList<String> results;

    public static void main(String[] args) {
        // String temp = "D.txt";
        // String[] c = temp.split(".");
        // System.out.println(c.length);
        // System.out.println(c[0]);
        // System.out.println(c[1]);
        
        ArrayList<String> filenames = new ArrayList<String>();
        filenames.add("/A.txt");
        filenames.add("/A/B.txt");
        filenames.add("/A/C.txt");
        filenames.add("/A/B/D.txt");
        filenames.add("/A/C/F.txt");
        filenames.add("/A/B/E.txt");
        filenames.add("/A/C/G.txt");
        Resemble resemble = new Resemble(filenames);
        ArrayList<String> results = resemble.order();
        // resemble.printTree();
        // System.out.println("=====");
        // ArrayList<String> results = resemble.getResults();
        for(String name: results) {
            System.out.println(name);
        }
    }
}
