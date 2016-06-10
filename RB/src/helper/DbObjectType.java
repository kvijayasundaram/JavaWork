package helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import builder.ReleaseNotesBuilder;

public class DbObjectType {
	 public String dbName;
	 public String schemaName;
	 public String objectTypeName;
	 public int objectTypeSequence;
	 public int deploySequence;
	 public int backoutSequence;
	 public List<String> preRequisiteMessages;
	 public List<String> postDeploymentMessages;
	 public List<Instruction> instructions;
	 public DbSchema parent;

	 public static final int objectTypeSeqInterval = 100000; //100 thousand
	 
	 {
		 instructions = new ArrayList<Instruction>();
		 preRequisiteMessages = new ArrayList<String>();
		 postDeploymentMessages = new ArrayList<String>();
	 }
	    
	 public DbObjectType(DbSchema parent, String dbName, String schemaName, String objectTypeName, int objectTypeSequence) {
		 this.parent = parent;
		 this.dbName = dbName;
		 this.schemaName = schemaName;
		 this.objectTypeName = objectTypeName;
		 this.objectTypeSequence = objectTypeSequence;
		 this.deploySequence = objectTypeSequence;
		 this.backoutSequence = -(objectTypeSequence);
	 }
	 
	 public void addInstruction(Instruction instruction){
		 instructions.add(instruction);
	 }

	 public void addPreReq(String preReq){
		 preRequisiteMessages.add(preReq);
	 }

	 public void addPostDeployment(String postDeployment){
		 postDeploymentMessages.add(postDeployment);
	 }

	 public void empty() {
		 instructions.clear();
		 preRequisiteMessages.clear();
		 postDeploymentMessages.clear();
	 }
	 
	 public void print(Instruction.PrintType pt) {
		 preRequisiteMessages.stream().forEach(item->System.out.println(item));
		 if (pt == Instruction.PrintType.BACKOUT)
			 instructions.stream().sorted(Comparator.comparing(item-> item.backoutSequence)).forEach(item->item.printInstruction(pt));
		 else
			 instructions.stream().sorted(Comparator.comparing(item-> item.deploySequence)).forEach(item->item.printInstruction(pt));
		 postDeploymentMessages.stream().forEach(item->System.out.println(item));
	 }
	 
	  public List<String> getInstructions(Instruction.PrintType pt) {
		  List<String> instructionText;
		  if (pt == Instruction.PrintType.BACKOUT)
			  instructionText = instructions.stream().sorted(Comparator.comparing(item-> item.backoutSequence)).map(item->item.getText(pt)).collect(Collectors.toList());
		  else
			  instructionText = instructions.stream().sorted(Comparator.comparing(item-> item.deploySequence)).map(item->item.getText(pt)).collect(Collectors.toList());
		  instructionText.add("--");
		  return instructionText;
	  }
	  
}
