package helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DbSchema {
	
    public String dbName;
    public String schemaName;
    public int schemaSequence;
    public int deploySequence;
    public int backoutSequence;
    
    public List<DbObjectType> objectTypes;
    public String connectText;
    public List<String> preRequisiteMessages;
    public List<String> postDeploymentMessages;
    public Database parent;
    
	public boolean hasDML  = false;
	public boolean hasCode = false;
	public boolean hasJobDependency = false;
	public boolean hasPoolRefresh = false;
	public boolean isNew = false;
	
	 private Set<DbSchema> dependencies = new HashSet<DbSchema>();
	
	public static final int schemaSeqInterval = 10000000; // 10 mil
    
    {
    	objectTypes = new ArrayList<DbObjectType>();
    	preRequisiteMessages = new ArrayList<String>();
    	postDeploymentMessages =  new ArrayList<String>();
    }
    
    public DbSchema(Database parent, String dbName, String schemaName, int schemaSequence, String connectText) {
    	this.parent = parent;
    	this.dbName = dbName;
    	this.schemaName = schemaName;
    	this.schemaSequence = schemaSequence;
    	this.connectText = connectText;
   	 	this.deploySequence = schemaSequence;
   	 	this.backoutSequence = -(schemaSequence);
    	
    }
    
    public void addObjectType(DbObjectType objectType){
    	objectTypes.add(objectType);
    }
    
    public void addPreReq(String preReq){
    	preRequisiteMessages.add(preReq);
    }
    
    public void addPostDeployment(String postDeployment){
    	postDeploymentMessages.add(postDeployment);
    }
    
    public void empty() {
    	objectTypes.clear();
    	preRequisiteMessages.clear();
    	postDeploymentMessages.clear();
    }
    
    public void print(Instruction.PrintType pt) {
    	preRequisiteMessages.stream().forEach(item->System.out.println(item));
    	if (pt == Instruction.PrintType.BACKOUT)
    		objectTypes.stream().sorted(Comparator.comparing(item-> (item.backoutSequence))).forEach(item->item.print(pt));
    	else
    		objectTypes.stream().sorted(Comparator.comparing(item-> item.deploySequence)).forEach(item->item.print(pt));
    	postDeploymentMessages.stream().forEach(item->System.out.println(item));
    }
    
    public List<String> getInstructions(Instruction.PrintType pt) {
    	List<String> instructionText = new ArrayList<String>();
    	if (pt == Instruction.PrintType.BACKOUT)
    		    	
    	     instructionText = objectTypes.stream().sorted(Comparator.comparing(item-> item.backoutSequence))
    	     .flatMap(item->item.getInstructions(pt).stream()).collect(Collectors.toList());
    	else
    	    instructionText = objectTypes.stream().sorted(Comparator.comparing(item-> item.deploySequence))
    	    .flatMap(item->item.getInstructions(pt).stream()).collect(Collectors.toList());
    	
    	return instructionText;
    }
    
    public void populateOtherinstructions(){
    	for (DbObjectType dbObjectType:objectTypes) {
    		for(Instruction instruction:dbObjectType.instructions) {
    			if(dbObjectType.objectTypeName.equals("dml")){
    				hasDML = true;
    				if (instruction.exists)
    					preRequisiteMessages.add("Backup data on table " + instruction.objectName);
    			}
    			
    			if(dbObjectType.objectTypeName.equals("table")){
    				if (instruction.exists) {
    					preRequisiteMessages.add("Backup structure of table " + instruction.objectName);
    					hasCode = true;
    					hasPoolRefresh = true;
    				}
    			}

    			if( (dbObjectType.objectTypeName.equals("trigger")) ||  
    					dbObjectType.objectTypeName.equals("procedure") ||
    					dbObjectType.objectTypeName.equals("function") ||
    					dbObjectType.objectTypeName.equals("package") ||
    					(dbObjectType.objectTypeName.equals("view"))
    					){
    				hasCode = true;
    				if (instruction.exists)
    					preRequisiteMessages.add("Backup copy of  " + dbObjectType.objectTypeName + " " + instruction.objectName);
    			}
    			if(     dbObjectType.objectTypeName.equals("procedure") ||
    					dbObjectType.objectTypeName.equals("function") ||
    					dbObjectType.objectTypeName.equals("package")
    					){
    				hasPoolRefresh = true;
    			}
    		}
    	}
        if (hasDML)
        	postDeploymentMessages.add("Commit Explicitly");
        if(hasCode)
        	postDeploymentMessages.add("Recompile Invalid Objects");
        if(hasPoolRefresh)
        	postDeploymentMessages.add("Refresh DB Connection Pools");
    }
    
    public long getNextSequence() {
    	long maxval = parent.schemas.stream().mapToLong(item->item.schemaSequence).max().orElse(0);
    	return maxval + schemaSeqInterval;
    }
    
    private void addDependency(Optional<DbSchema> schema){
    	if (schema.isPresent())
    		dependencies.add(schema.get());
    }
    
    public void getPredecessors( List<DbSchema> schemas, String otherSchemaName){
		addDependency(schemas.stream().filter(item->item.schemaName.equalsIgnoreCase(otherSchemaName)).findFirst()); 
    }
    
    
    
}

