package helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.Collectors;

public class Database {
    public String dbName;
    public List<DbSchema> schemas;
    public long dbSequence;
    public List<String> preRequisiteMessages;
    public List<String> postDeploymentMessages;
    private List<Database> parent;
    private Set<Database> dependencies = new HashSet<Database>();
    
	public static final int dbSeqInterval = 1000000000; // 1 billion
    
    {
    	this.schemas = new ArrayList<DbSchema>();
    	this.preRequisiteMessages = new ArrayList<String>();
    	this.postDeploymentMessages = new ArrayList<String>();
    }
    
    public Database(List<Database> parent, String dbName, int dbSequence){
    	this.parent = parent;
    	this.dbName = dbName;
    	this.dbSequence = dbSequence;
    }
    
    public void addSchema(DbSchema dbSchema){
    	schemas.add(dbSchema);
    }
    
    public void addPreReq(String preReq){
    	preRequisiteMessages.add(preReq);
    }
    
    public void addPostDeployment(String postDeployment){
    	postDeploymentMessages.add(postDeployment);
    }
    
    public void empty(){
    	schemas.clear();
    	preRequisiteMessages.clear();
    	postDeploymentMessages.clear();
    }
    
    public void print(Instruction.PrintType pt) {
    	preRequisiteMessages.stream().forEach(item->System.out.println(item));
    	if (pt == Instruction.PrintType.BACKOUT)
    		schemas.stream().sorted(Comparator.comparing(item-> -(item.schemaSequence))).forEach(item->item.print(pt));
    	else
    		schemas.stream().sorted(Comparator.comparing(item-> item.schemaSequence)).forEach(item->item.print(pt));
    	postDeploymentMessages.stream().forEach(item->System.out.println(item));
    }
    
    public long getNextSequence() {
    	long maxval = parent.stream().mapToLong(item->item.dbSequence).max().orElse(0);
    	return maxval + dbSeqInterval;
    }
    
    public void swapSequence(Database otherDb) {
    	long temp;
    	if (otherDb.dbSequence < this.dbSequence) { 
    		temp = this.dbSequence;
    		this.dbSequence = otherDb.dbSequence;
    		otherDb.dbSequence = temp;
    	}
    }
    
    private void addDependency(Optional<Database> db){
    	if (db.isPresent())
    		dependencies.add(db.get());
    }
    
    public void getPredecessors( List<Database> dbNames, String otherDbName){
		addDependency(dbNames.stream().filter(item->item.dbName.equalsIgnoreCase(otherDbName)).findFirst()); 
    }
    
    
    public boolean resequenceDatabase() {
    	Database otherDb;
    	long curSeq = dbSequence;
    	long initSeq = dbSequence;
    	if (!dependencies.isEmpty()) {
    		dependencies.forEach((item)-> item.swapSequence(this));
    	}
		return curSeq == initSeq; 
    }

}
