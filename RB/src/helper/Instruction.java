package helper;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import builder.ReleaseNotesBuilder;;

public class Instruction {
    public long instructionSequence;
    public String text;
    public long priorInstruction;
    public String dbName;
    public String schemaName;
    public String objectType;
    public String objectName;
    public String objectSubType;
    public String fileName;
    public boolean isOK;
    public String errorMessage;
    public boolean exists;
    public boolean parentExists;
    public Instruction parentInstruction;
    public String backOut;
    public long deploySequence;
    public long backoutSequence;
    public String preRequisite;
    public String codeBody;
    public String foreignDbName;
    public String foreignSchemaName;
    public DbObjectType parent;
    public Set<Instruction> dependencies = new HashSet<Instruction>();
    
    public static final int instructionSeqInterval = 1000000;
    
    public enum PrintType {DEPLOYMENT,BACKOUT}
    
    public Instruction(DbObjectType parent, long instructionSequence, String text,  
    		String dbName, String schemaName, String objectType, String objectName,  
    		String objectSubType, String fileName, boolean isOK, String errorMessage, String codeBody
    ) {
    	this.parent = parent;
    	this.instructionSequence = instructionSequence;
    	this.text = text;
    	this.dbName  = dbName;
    	this.schemaName = schemaName;
    	this.objectType = objectType;
    	this.objectName = objectName;
    	this.objectSubType = objectSubType;
    	this.fileName = fileName;
    	this.isOK = isOK;
    	this.errorMessage = errorMessage;
    	this.codeBody = codeBody.toLowerCase();
    }
    
    public void createOtherInfo(boolean exists, boolean parentExists, String parentName, String comments){
        this.exists = exists;
        this.parentExists = parentExists;
    	String tempText  = "";
    	if( objectType.equalsIgnoreCase("procedure") || 
    		objectType.equalsIgnoreCase("function") || 
    		objectType.equalsIgnoreCase("package") ||
    		objectType.equalsIgnoreCase("view")
    	)
    	{
    		if (exists){
    			if (fileName.startsWith("pkb")){
    				this.backOut = "Restore previous copy of package body " + objectName;
    				this.preRequisite = "Backup copy of package body " + objectName + "(" + schemaName + "@" + dbName + ")";
    			}
    			else
    			{
    				this.backOut = "Restore previous copy of package " + objectName;
    				this.preRequisite = "Backup copy of package " + objectName + "(" + schemaName + "@" + dbName + ")";
    			}
    		}
    		else if (parentExists &&  ! fileName.startsWith("pkb")){  // no need for separate drop for package body
    			this.backOut = "Drop " + objectType +  "  " + objectName;
    		}
    	}
    	else if( objectType.equalsIgnoreCase("trigger")) {
    		if (exists) {
    			this.preRequisite = "Backup copy of " + objectType + " " + objectName + "(" + schemaName + "@" + dbName + ")";
    			this.backOut = "Restore previous copy of " + objectType + " " + objectName;
    		}
    		else if(parentExists)
    			this.backOut = "Drop trigger " + objectType + " " + objectName;
    	}
    	else if ( objectType.equalsIgnoreCase("dml")) {
    		if(parentExists) {
    			if (comments != null && comments.length() > 0 )
    				this.backOut = comments;
    			else {
    				this.preRequisite = "Backup data for " + objectType + " " + objectName + "(" + schemaName + "@" + dbName + ")";
    				this.backOut = "Restore data from backup copy of " + objectType + " " + objectName;
    			}
    		}
    	}
    	else if( objectType.equalsIgnoreCase("table") ||
    			objectType.equalsIgnoreCase("sequence") ||
    			objectType.equalsIgnoreCase("synonym")
    			) {
    		if (exists){
    		    if (comments != null && comments.length() > 0 )
    			    this.backOut = comments;
    		}
    		else if (parentExists){
    			this.backOut = "drop " +  objectType + " " + objectName + ";";
    		}
    		if (objectType.equalsIgnoreCase("synonym")) {
    			if (codeBody.contains("for")) {
    			    tempText = codeBody.split("for")[1].trim();
    			    if (tempText.contains("@")){
    			    	foreignDbName = tempText.split("@")[1].split(";")[0].trim();
    			    	foreignSchemaName = tempText.split("@")[0].split("\\.")[0].trim();
    			    }
    			    else {
    			    	foreignDbName = dbName;
    			    	foreignSchemaName = tempText.split("\\.")[0].trim();
    			    }
    			    	
    			}
    		}
    	}
    	else if (objectType.equalsIgnoreCase("index")) {
    		if (parentExists) {
    			if (exists){
        		    if (comments != null && comments.length() > 0 )
        			    this.backOut = comments;
        		}
        		else
        			this.backOut = "drop " +  objectType + " " + objectName + ";";
    		}
    	}
    	else if (objectType.equalsIgnoreCase("constraint")) {
    		if (parentExists) {
    			if (exists){
        		    if (comments != null && comments.length() > 0 )
        			    this.backOut = comments;
        		}
        		else
        			this.backOut = "alter table " + parentName + " drop " +  objectType + " " + objectName + ";";
    		}
    	}
    	else if (objectType.equalsIgnoreCase("user")) {
    		if (exists){
    			if (comments != null && comments.length() > 0 )
    				this.backOut = comments;
    		}
    		else
    			this.backOut = "drop user " + objectName + " cascade;";
    	}
    	else if (objectType.equalsIgnoreCase("role")) {
    		if (exists){
    			if (comments != null && comments.length() > 0 )
    				this.backOut = comments;
    		}
    		else
    			this.backOut = "drop role " + objectName + ";";
    	}
    	else if( objectType.equalsIgnoreCase("object") ||
    			objectType.equalsIgnoreCase("collection") 
    			) {
    		if (exists){
    		    if (comments != null && comments.length() > 0 )
    			    this.backOut = comments;
    		}
    		else if (parentExists){
    			this.backOut = "drop type " + objectName + ";\n/";
    		}
    	}
    	else if( objectType.equalsIgnoreCase("scheduler")) {
    		if (exists){
    		    if (comments != null && comments.length() > 0 )
    			    this.backOut = comments;
    		}
    		else if (parentExists){
    			this.backOut = "drop the scheduler job " + objectName + ".";
    		}
    	}
    	else if( objectType.equalsIgnoreCase("privilege")) {
    		if (exists){
    		    if (comments != null && comments.length() > 0 )
    			    this.backOut = comments;
    		    else
    		    	this.backOut = codeBody.replace("grant", "revoke").replace(" to ", " from ");
    		}
    	}
    	else {
    		this.backOut = comments;
    	}
    	computeInstructionSequence();
    }
    
    public void printInstruction(PrintType pt) {
		 if (pt == PrintType.BACKOUT){
			 if (backOut != null)
			     System.out.println(backOut.trim());
		 }
		 else {
			 System.out.println(deploySequence + ":" + text);
		 }
	 }
    
    public String getText(PrintType pt) {
    	String value = "";
    	if (pt == PrintType.BACKOUT){
    		if (backOut != null)
    			value = backOut.trim();
    	}
    	else {
    		value = text;
    	}
    	return value;
    }
    
    private void addDependency(Optional<Instruction> ins){
    	if (ins.isPresent())
    		dependencies.add(ins.get());
    }
    
    public void computeInstructionSequence() {
    	instructionSequence = parent.instructions.size() + 1;
    	int seq = 0;

    	if (objectType.equals("constraint")){
    		if (codeBody.contains("primary")) {
    			seq = ReleaseNotesBuilder.constraintRanking.get("primary");
    		}
    		else if(codeBody.contains("unique")){
    			seq = ReleaseNotesBuilder.constraintRanking.get("unique");
    		}
    		else if(codeBody.contains("check")) {
    			seq = ReleaseNotesBuilder.constraintRanking.get("check");
    		}
    		else if(codeBody.contains("foreign")) {
    			seq = ReleaseNotesBuilder.constraintRanking.get("foreign");
    		}
    	}
    	
    	deploySequence = instructionSequence + (seq * instructionSeqInterval);
    	backoutSequence = -(deploySequence);
    }
    
    public void swapSequence(Instruction other) {
    	long temp;
    	if (other.deploySequence > this.deploySequence){
    		temp = other.deploySequence;
    		other.deploySequence = this.deploySequence;
    		this.deploySequence = temp;
    	}	
    }
    
    public void resequence() {
    	long temp = this.deploySequence;
    	if (!dependencies.isEmpty()){
    		//dependencies.forEach((item)-> item.swapSequence(this));
    		for(Instruction other:dependencies){
    			swapSequence(other);
    		}
    	}
    	if (temp != this.deploySequence){
    		System.out.println("Moved sequence from: " + temp + " to: " + this.deploySequence);
    	}
    	backoutSequence = -(deploySequence);
    }
    
    public void getPredecessors( List<Instruction> mainList, String inputDbName, String inputSchemaName, String inputObjectType, String inputObjectName){
    	if(inputDbName == null || inputDbName.equalsIgnoreCase(this.dbName)) {	
    		if (inputSchemaName.equalsIgnoreCase(this.schemaName)) {
    			addDependency(mainList.stream().filter(
    					item->item.dbName.equalsIgnoreCase(inputDbName) &&
    					item.schemaName.equalsIgnoreCase(inputSchemaName) && 
    					item.objectType.equalsIgnoreCase(inputObjectType) && 
    					item.objectName.equalsIgnoreCase(inputObjectName) &&
    					!item.objectName.equalsIgnoreCase(this.objectName)).findFirst()); 
    			System.out.println("added dependency for:" + this.objectName + " referencing:" + inputSchemaName + "." + inputObjectName + "@" + inputDbName);
    		}
    		else {
    			System.out.println("schema interdependency found for:" + this.objectName);
    		}
    	}
    	else {
    		System.out.println("DB interdependency found:" + this.objectName);
    	}

    }
    
}
